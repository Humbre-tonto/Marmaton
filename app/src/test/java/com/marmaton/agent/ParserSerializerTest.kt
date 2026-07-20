package com.marmaton.agent

import com.marmaton.agent.llm.GemmaAgentEngine
import com.marmaton.agent.model.AgentAction
import com.marmaton.agent.parser.ScreenNode
import com.marmaton.agent.parser.ScreenTreeParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserSerializerTest {

    @Test
    fun testScreenNodeSerialization() {
        val node = ScreenNode(
            id = "com.android.settings:id/wifi",
            txt = "Wi-Fi Settings",
            desc = "Open Wifi setting screen",
            clk = true,
            scrl = false,
            bnd = listOf(0, 100, 1080, 200)
        )

        // Serialize ScreenNode utilizing Kotlinx serialization
        val jsonString = Json { encodeDefaults = false; explicitNulls = false }.encodeToString(node)

        assertTrue(jsonString.contains("\"id\":\"com.android.settings:id/wifi\""))
        assertTrue(jsonString.contains("\"txt\":\"Wi-Fi Settings\""))
        assertTrue(jsonString.contains("\"desc\":\"Open Wifi setting screen\""))
        assertTrue(jsonString.contains("\"clk\":true"))
        assertTrue(!jsonString.contains("\"scrl\""))
        assertTrue(jsonString.contains("\"bnd\":[0,100,1080,200]"))
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

        // Assert Kotlinx JSON parser can parse the cleaned JSON successfully via decoupled parseAction
        val action = GemmaAgentEngine.parseAction(cleaned)
        assertNotNull(action)
        assertEquals("CLICK", action?.actionType)
        assertEquals("com.android.settings:id/wifi", action?.targetId)
        assertEquals(listOf(0, 100, 1080, 200), action?.bounds)
        assertEquals("I need to turn on Wi-Fi settings", action?.reasoning)
    }

    @Test
    fun testBuildSystemPromptAndParseActionDecoupled() {
        val goal = "Enable Battery Saver mode"
        val mockScreen = """
            [{"id":"battery_saver_switch","txt":"Battery Saver","clk":true,"bnd":[10,200,500,250]}]
        """.trimIndent()

        val prompt = GemmaAgentEngine.buildSystemPrompt(goal, mockScreen)
        assertTrue(prompt.contains(goal))
        assertTrue(prompt.contains(mockScreen))
        assertTrue(prompt.contains("Marmaton"))

        // Mock Gemma response
        val mockGemmaResponse = """
            {
                "actionType": "CLICK",
                "targetId": "battery_saver_switch",
                "bounds": [10, 200, 500, 250],
                "reasoning": "I see the Battery Saver switch on-screen. I will click it to enable the battery saver mode."
            }
        """.trimIndent()

        val parsedAction = GemmaAgentEngine.parseAction(mockGemmaResponse)
        assertNotNull(parsedAction)
        assertEquals("CLICK", parsedAction?.actionType)
        assertEquals("battery_saver_switch", parsedAction?.targetId)
        assertEquals(listOf(10, 200, 500, 250), parsedAction?.bounds)
        assertEquals("I see the Battery Saver switch on-screen. I will click it to enable the battery saver mode.", parsedAction?.reasoning)
    }

    @Test
    fun testInvalidActionParsingReturnsNullGracefully() {
        val invalidResponse = "This is a random conversational response with no JSON whatsoever!"
        val parsed = GemmaAgentEngine.parseAction(invalidResponse)
        assertNull(parsed)
    }

    @Test
    fun testParsesBoundsAsSingleStringInArray() {
        // Real output from Gemma 3 1B: fenced JSON, bounds as one comma-string inside the array,
        // lowercase-able actionType. The parser must salvage this into a valid action.
        val raw = """
            ```json
            {
               "actionType": "CLICK",
               "targetId": "battery_saver",
               "bounds": [ "852, 134, 1036, 266" ],
               "textToType": "Turn on Battery Saver",
               "reasoning": "Clicking the visible battery saver control."
            }
            ```
        """.trimIndent()

        val action = GemmaAgentEngine.parseAction(raw)
        assertNotNull(action)
        assertEquals("CLICK", action?.actionType)
        assertEquals(listOf(852, 134, 1036, 266), action?.bounds)
        assertEquals("battery_saver", action?.targetId)
    }

    @Test
    fun testParsesBoundsAsCommaStringAndLowercaseType() {
        val raw = """{"actionType":"click","bounds":"10, 20, 30, 40","reasoning":"x"}"""
        val action = GemmaAgentEngine.parseAction(raw)
        assertNotNull(action)
        assertEquals("CLICK", action?.actionType)
        assertEquals(listOf(10, 20, 30, 40), action?.bounds)
        assertNull(action?.targetId)
    }
}
