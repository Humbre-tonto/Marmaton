package com.marmaton.agent

import com.marmaton.agent.llm.GemmaAgentEngine
import com.marmaton.agent.model.AgentAction
import com.marmaton.agent.parser.ScreenNode
import com.marmaton.agent.parser.ScreenTreeParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserSerializerTest {

    @Test
    fun testScreenNodeToJsonString() {
        val node = ScreenNode(
            id = "com.android.settings:id/wifi",
            txt = "Wi-Fi Settings",
            desc = "Open Wifi setting screen",
            clk = true,
            scrl = false,
            bnd = listOf(0, 100, 1080, 200)
        )

        val json = node.toJsonString()
        assertTrue(json.contains("\"id\":\"com.android.settings:id/wifi\""))
        assertTrue(json.contains("\"txt\":\"Wi-Fi Settings\""))
        assertTrue(json.contains("\"desc\":\"Open Wifi setting screen\""))
        assertTrue(json.contains("\"clk\":true"))
        assertTrue(!json.contains("\"scrl\""))
        assertTrue(json.contains("\"bnd\":[0,100,1080,200]"))
    }

    @Test
    fun testSerializeTreeEmpty() {
        val json = ScreenTreeParser.serializeTree(null)
        assertEquals("[]", json)
    }

    @Test
    fun testCleanJsonStringWithMarkdown() {
        val rawInput = """
            Sure, here is the action to perform:
            ```json
            {
               "actionType": "CLICK",
               "targetId": "com.android.settings:id/wifi",
               "bounds": [0, 100, 1080, 200],
               "reasoning": "I need to turn on Wi-Fi settings"
            }
            ```
            Hope this helps!
        """.trimIndent()

        val cleaned = GemmaAgentEngine.cleanJsonString(rawInput)

        // Assert that the clean method successfully isolated the JSON block
        assertTrue(cleaned.startsWith("{"))
        assertTrue(cleaned.endsWith("}"))

        // Assert Kotlinx JSON parser can parse the cleaned JSON successfully
        val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }
        val action = jsonParser.decodeFromString<AgentAction>(cleaned)

        assertEquals("CLICK", action.actionType)
        assertEquals("com.android.settings:id/wifi", action.targetId)
        assertEquals(listOf(0, 100, 1080, 200), action.bounds)
        assertEquals("I need to turn on Wi-Fi settings", action.reasoning)
    }
}
