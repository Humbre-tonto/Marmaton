package com.marmaton.agent.analytics

import android.content.Context
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

class PostHogAnalyticsSink(
    context: Context,
    apiKey: String,
    host: String,
    @Volatile private var consentEnabled: Boolean
) : AnalyticsSink {

    init {
        val config = PostHogAndroidConfig(
            apiKey = apiKey,
            host = host.ifBlank { "https://eu.i.posthog.com" }
        ).apply {
            // Strict minimal collection rules
            captureApplicationLifecycleEvents = false
            captureScreenViews = false
            captureDeepLinks = false
            sessionReplay = false
            errorTrackingConfig.autoCapture = false

            // Start opted-out by default
            optOut = true
        }

        try {
            PostHogAndroid.setup(context, config)
            // Immediately synchronize the opt-in/opt-out status with the consent status
            if (consentEnabled) {
                PostHog.optIn()
            } else {
                PostHog.optOut()
                PostHog.reset()
            }
        } catch (e: Exception) {
            // Safely catch any initialization or setup exceptions
        }
    }

    override fun setEnabled(consent: Boolean) {
        consentEnabled = consent
        try {
            if (consent) {
                PostHog.optIn()
            } else {
                PostHog.optOut()
                PostHog.reset()
            }
        } catch (e: Exception) {
            // Prevent crashes from PostHog state manipulation
        }
    }

    override fun trackFirstRun() {
        if (!consentEnabled) return
        val properties = mapOf(
            "app_version" to "1.0",
            "\$ip" to "0.0.0.0"
        )
        try {
            PostHog.capture(event = "app_first_run", properties = properties)
        } catch (e: Exception) {
            // Prevent exceptions
        }
    }

    override fun trackBackendSelected(type: String, modelName: String, providerHost: String?) {
        if (!consentEnabled) return
        val properties = mutableMapOf<String, Any>(
            "backend_type" to type,
            "model_name" to modelName,
            "\$ip" to "0.0.0.0"
        )
        if (providerHost != null) {
            properties["provider_host"] = providerHost
        }
        try {
            PostHog.capture(event = "backend_selected", properties = properties)
        } catch (e: Exception) {
            // Prevent exceptions
        }
    }

    override fun trackRunCompleted(type: String, outcome: String, steps: Int, durationMs: Long) {
        if (!consentEnabled) return
        val properties = mapOf(
            "backend_type" to type,
            "outcome" to outcome,
            "step_count" to steps,
            "duration_ms" to durationMs,
            "\$ip" to "0.0.0.0"
        )
        try {
            PostHog.capture(event = "agent_run_completed", properties = properties)
        } catch (e: Exception) {
            // Prevent exceptions
        }
    }
}
