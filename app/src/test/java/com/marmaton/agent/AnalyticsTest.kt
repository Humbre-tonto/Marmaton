package com.marmaton.agent

import com.marmaton.agent.llm.BackendConfig
import com.marmaton.agent.llm.BackendType
import org.junit.Assert.*
import org.junit.Test

class AnalyticsTest {

    // Helper fake class to test flow & behavior
    class TestAnalyticsSink : com.marmaton.agent.analytics.AnalyticsSink {
        var consentEnabled = false
        val capturedEvents = mutableListOf<CapturedEvent>()
        var optOutCount = 0
        var resetCount = 0

        data class CapturedEvent(
            val name: String,
            val properties: Map<String, Any?>
        )

        override fun setEnabled(consent: Boolean) {
            consentEnabled = consent
            if (!consent) {
                optOutCount++
                resetCount++
                capturedEvents.clear()
            }
        }

        override fun trackFirstRun() {
            if (!consentEnabled) return
            capturedEvents.add(CapturedEvent("app_first_run", mapOf("app_version" to "1.0", "\$ip" to "0.0.0.0")))
        }

        override fun trackBackendSelected(type: String, modelName: String, providerHost: String?) {
            if (!consentEnabled) return
            val props = mutableMapOf<String, Any?>(
                "backend_type" to type,
                "model_name" to modelName,
                "\$ip" to "0.0.0.0"
            )
            if (providerHost != null) {
                props["provider_host"] = providerHost
            }
            capturedEvents.add(CapturedEvent("backend_selected", props))
        }

        override fun trackRunCompleted(type: String, outcome: String, steps: Int, durationMs: Long) {
            if (!consentEnabled) return
            capturedEvents.add(CapturedEvent("agent_run_completed", mapOf(
                "backend_type" to type,
                "outcome" to outcome,
                "step_count" to steps,
                "duration_ms" to durationMs,
                "\$ip" to "0.0.0.0"
            )))
        }
    }

    @Test
    fun testConsentOffEmitsNoEvents() {
        val sink = TestAnalyticsSink()
        sink.setEnabled(false)

        sink.trackFirstRun()
        sink.trackBackendSelected("cloud", "gpt-4", "api.openai.com")
        sink.trackRunCompleted("cloud", "success", 5, 2000L)

        assertTrue(sink.capturedEvents.isEmpty())
    }

    @Test
    fun testConsentOnEmitsEvents() {
        val sink = TestAnalyticsSink()
        sink.setEnabled(true)

        sink.trackFirstRun()
        assertEquals(1, sink.capturedEvents.size)
        assertEquals("app_first_run", sink.capturedEvents[0].name)
    }

    @Test
    fun testTogglingConsentOffResetsAndOptOut() {
        val sink = TestAnalyticsSink()
        sink.setEnabled(true)

        sink.trackFirstRun()
        assertEquals(1, sink.capturedEvents.size)

        sink.setEnabled(false)
        assertEquals(1, sink.optOutCount)
        assertEquals(1, sink.resetCount)
        assertTrue(sink.capturedEvents.isEmpty())

        // Post-toggle check: tracking should do nothing
        sink.trackFirstRun()
        assertTrue(sink.capturedEvents.isEmpty())
    }

    @Test
    fun testExtractHostHelper() {
        assertEquals("api.anthropic.com", extractHost("https://api.anthropic.com/v1"))
        assertEquals("api.openai.com", extractHost("https://api.openai.com"))
        assertEquals("localhost", extractHost("http://localhost:11434"))
        assertEquals("192.168.1.50", extractHost("http://192.168.1.50:11434/api/generate"))
        assertNull(extractHost(""))
    }

    @Test
    fun testGetBackendSelectedDetailsCloud() {
        val config = BackendConfig(
            selectedType = BackendType.CLOUD,
            cloudBaseUrl = "https://api.anthropic.com/v1",
            cloudModel = "claude-4.5-sonnet"
        )
        val (type, modelName, providerHost) = getBackendSelectedDetails(config)

        assertEquals("cloud", type)
        assertEquals("claude-4.5-sonnet", modelName)
        assertEquals("api.anthropic.com", providerHost)
    }

    @Test
    fun testGetBackendSelectedDetailsOllama() {
        val config = BackendConfig(
            selectedType = BackendType.OLLAMA,
            ollamaScheme = "http",
            ollamaHost = "192.168.1.100",
            ollamaPort = 11434,
            ollamaModel = "llama3.1"
        )
        val (type, modelName, providerHost) = getBackendSelectedDetails(config)

        assertEquals("ollama", type)
        assertEquals("llama3.1", modelName)
        assertNull(providerHost) // Omit for ollama
    }

    @Test
    fun testGetBackendSelectedDetailsOnDevice() {
        val config = BackendConfig(
            selectedType = BackendType.LOCAL_FILE,
            localModelFilePath = "/data/user/0/com.marmaton.agent/files/imported_model.task"
        )
        val (type, modelName, providerHost) = getBackendSelectedDetails(config)

        assertEquals("on_device", type)
        assertEquals("imported_model.task", modelName) // Derive name from filename only
        assertNull(providerHost)
    }
}
