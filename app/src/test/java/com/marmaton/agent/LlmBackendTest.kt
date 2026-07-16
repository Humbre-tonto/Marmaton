package com.marmaton.agent

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.marmaton.agent.llm.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock

class LlmBackendTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun testAgentReasonerWithFakeBackend() = runTest {
        val fakeBackend = object : LlmBackend {
            override val displayName: String = "Fake"
            override suspend fun status(): BackendStatus = BackendStatus.Ready
            override suspend fun generate(prompt: String): String {
                return """
                    {
                        "actionType": "CLICK",
                        "targetId": "test_button",
                        "bounds": [10, 20, 100, 200],
                        "reasoning": "Fake reason"
                    }
                """.trimIndent()
            }
        }

        val reasoner = AgentReasoner(fakeBackend)
        val action = reasoner.reason("goal", "[]")
        assertNotNull(action)
        assertEquals("CLICK", action?.actionType)
        assertEquals("test_button", action?.targetId)
        assertEquals("Fake reason", action?.reasoning)
    }

    @Test
    fun testAgentReasonerWithGarbageTextReturnsNullGracefully() = runTest {
        val fakeBackend = object : LlmBackend {
            override val displayName: String = "Fake Garbage"
            override suspend fun status(): BackendStatus = BackendStatus.Ready
            override suspend fun generate(prompt: String): String {
                return "This is pure conversational garbage with no JSON whatsoever!"
            }
        }

        val reasoner = AgentReasoner(fakeBackend)
        val action = reasoner.reason("goal", "[]")
        assertNull(action)
    }

    @Test
    fun testOllamaBackendRequestResponseMapping() = runTest {
        val server = MockWebServer()
        server.start()

        val host = server.hostName
        val port = server.port

        val responseBody = """
            {
                "message": {
                    "role": "assistant",
                    "content": "{\n  \"actionType\": \"SWIPE_UP\",\n  \"reasoning\": \"Scroll to find more options\"\n}"
                }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val backend = OllamaBackend(scheme = "http", host = host, port = port, modelName = "qwen")
        val result = backend.generate("Hello Ollama")

        assertTrue(result.contains("SWIPE_UP"))

        val recordedRequest = server.takeRequest()
        assertEquals("/api/chat", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
        val reqBody = recordedRequest.body.readUtf8()
        assertTrue(reqBody.contains("\"model\":\"qwen\""))
        assertTrue(reqBody.contains("\"stream\":false"))
        assertTrue(reqBody.contains("Hello Ollama"))

        server.shutdown()
    }

    @Test
    fun testOllamaBackendStatusCheck() = runTest {
        val server = MockWebServer()
        server.start()

        val host = server.hostName
        val port = server.port

        val tagsResponse = """
            {
                "models": [
                    { "name": "qwen:latest" }
                ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(tagsResponse))

        val backend = OllamaBackend(scheme = "http", host = host, port = port, modelName = "qwen")
        val status = backend.status()

        assertTrue(status is BackendStatus.Ready)

        server.shutdown()
    }

    @Test
    fun testSettingsPersistenceRoundTrips() = runTest {
        val mockContext = mock<Context>()
        val datastoreFile = tmpFolder.newFile("test_settings.preferences_pb")

        val testDataStore = PreferenceDataStoreFactory.create {
            datastoreFile
        }

        val settings = SettingsPersistence(mockContext, testDataStore)

        // Read initial default values
        val initialConfig = settings.configFlow.first()
        assertEquals(BackendType.LOCAL_FILE, initialConfig.selectedType)
        assertEquals("", initialConfig.localModelFilePath)

        // Perform some updates
        settings.updateSelectedType(BackendType.OLLAMA)
        settings.updateOllamaConfig("https", "192.168.1.10", 11434, "llama3")
        settings.updateCloudConfig("https://custom-cloud.ai", "deepseek-coder")
        settings.updateLocalModel("/path/to/model", "content://saf/uri")

        // Read updated values
        val updatedConfig = settings.configFlow.first()
        assertEquals(BackendType.OLLAMA, updatedConfig.selectedType)
        assertEquals("https", updatedConfig.ollamaScheme)
        assertEquals("192.168.1.10", updatedConfig.ollamaHost)
        assertEquals(11434, updatedConfig.ollamaPort)
        assertEquals("llama3", updatedConfig.ollamaModel)
        assertEquals("https://custom-cloud.ai", updatedConfig.cloudBaseUrl)
        assertEquals("deepseek-coder", updatedConfig.cloudModel)
        assertEquals("/path/to/model", updatedConfig.localModelFilePath)
        assertEquals("content://saf/uri", updatedConfig.localModelUri)
    }
}
