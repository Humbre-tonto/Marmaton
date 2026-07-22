package com.marmaton.agent

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.marmaton.agent.chat.ChatSession
import com.marmaton.agent.workflow.Workflow
import com.marmaton.agent.workflow.WorkflowRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock

class ChatAndWorkflowTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    // ---------- Workflow serialization ----------

    @Test
    fun workflowRoundTripsThroughJson() {
        val workflow = Workflow(
            id = "wf-1",
            name = "Say hi",
            steps = listOf("Open messages", "Send \"hi\" to Mom", "Go home")
        )
        val encoded = json.encodeToString(listOf(workflow))
        val decoded = json.decodeFromString<List<Workflow>>(encoded)

        assertEquals(1, decoded.size)
        assertEquals(workflow, decoded.first())
        assertEquals(3, decoded.first().stepCount)
    }

    // ---------- WorkflowRepository persistence ----------

    private fun repository(): WorkflowRepository {
        val context = mock<Context>()
        val store = PreferenceDataStoreFactory.create {
            tmpFolder.newFile("workflows_${System.nanoTime()}.preferences_pb")
        }
        return WorkflowRepository(context, store)
    }

    @Test
    fun upsertInsertsAndReplacesById() = runTest {
        val repo = repository()
        repo.upsert(Workflow("a", "First", listOf("step 1")))
        repo.upsert(Workflow("b", "Second", listOf("step 1", "step 2")))

        var all = repo.workflowsFlow.first()
        assertEquals(2, all.size)

        // Same id replaces rather than duplicates.
        repo.upsert(Workflow("a", "First (renamed)", listOf("changed")))
        all = repo.workflowsFlow.first()
        assertEquals(2, all.size)
        assertEquals("First (renamed)", all.first { it.id == "a" }.name)
    }

    @Test
    fun deleteRemovesOnlyTheMatchingWorkflow() = runTest {
        val repo = repository()
        repo.upsert(Workflow("a", "First", listOf("s")))
        repo.upsert(Workflow("b", "Second", listOf("s")))

        repo.delete("a")
        val all = repo.workflowsFlow.first()
        assertEquals(1, all.size)
        assertEquals("b", all.first().id)
    }

    @Test
    fun emptyStoreReturnsEmptyList() = runTest {
        val repo = repository()
        assertTrue(repo.workflowsFlow.first().isEmpty())
    }

    // ---------- Chat prompt building ----------

    @Test
    fun buildPromptIncludesSystemAndRoles() {
        val prompt = ChatSession.buildPrompt(
            listOf(
                ChatSession.ChatMessage(ChatSession.Role.USER, "hello"),
                ChatSession.ChatMessage(ChatSession.Role.ASSISTANT, "hi there")
            )
        )
        assertTrue(prompt.contains("You are Marmaton"))
        assertTrue(prompt.contains("User: hello"))
        assertTrue(prompt.contains("Assistant: hi there"))
        assertTrue(prompt.trimEnd().endsWith("Assistant:"))
    }

    @Test
    fun buildPromptTruncatesToRecentHistory() {
        // 20 user messages; only the last 10 should survive the context cap.
        val history = (1..20).map {
            ChatSession.ChatMessage(ChatSession.Role.USER, "msg$it")
        }
        val prompt = ChatSession.buildPrompt(history)
        assertFalse("oldest message should be dropped", prompt.contains("msg1\n"))
        assertTrue("most recent message should be kept", prompt.contains("msg20"))
        assertTrue(prompt.contains("msg11"))
    }

    // ---------- Backend selection details ----------

    // ---------- Agent action schema ----------

    @Test
    fun systemPromptAdvertisesPhoneControlActions() {
        val prompt = com.marmaton.agent.llm.GemmaAgentEngine.buildSystemPrompt("open whatsapp", "[]")
        // New phone-control actions must be described so the model can emit them.
        assertTrue(prompt.contains("OPEN_APP"))
        assertTrue(prompt.contains("BACK"))
        assertTrue(prompt.contains("HOME"))
        assertTrue(prompt.contains("ENTER"))
    }

    @Test
    fun systemPromptIncludesForegroundAppAndFinishRule() {
        val prompt = com.marmaton.agent.llm.GemmaAgentEngine.buildSystemPrompt(
            "open whatsapp", "[]", currentApp = "com.whatsapp"
        )
        assertTrue(prompt.contains("com.whatsapp"))
        assertTrue(prompt.contains("Foreground app"))
        // The rule that prevents the re-open loop must be present.
        assertTrue(prompt.contains("already the Foreground app"))
    }

    @Test
    fun parsesOpenAppAction() {
        val raw = """
            {"actionType":"OPEN_APP","textToType":"WhatsApp","reasoning":"launch the app"}
        """.trimIndent()
        val action = com.marmaton.agent.llm.GemmaAgentEngine.parseAction(raw)
        assertEquals("OPEN_APP", action?.actionType)
        assertEquals("WhatsApp", action?.textToType)
    }

    @Test
    fun derivesAppNameFromLaunchGoals() {
        assertEquals("whatsapp", deriveAppNameFromGoal("open whatsapp"))
        assertEquals("Chrome", deriveAppNameFromGoal("launch Chrome"))
        assertEquals("new chat", deriveAppNameFromGoal("open new chat"))
        // Not a launch instruction -> null
        assertNull(deriveAppNameFromGoal("send hello to mom"))
        // Too long to be an app name -> null
        assertNull(deriveAppNameFromGoal("open the settings and turn on wifi please"))
    }

    @Test
    fun extractHostParsesUrls() {
        assertEquals("api.openai.com", extractHost("https://api.openai.com"))
        assertEquals("api.openai.com", extractHost("https://api.openai.com/v1/chat"))
        assertEquals("localhost", extractHost("http://localhost:8080/x"))
        assertNull(extractHost(""))
    }
}
