package com.marmaton.agent.analytics

import android.content.Context
import com.marmaton.agent.BuildConfig
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

interface PostHogProvider {
    fun capture(event: String, properties: Map<String, Any>?)
    fun optIn()
    fun optOut()
    fun reset()
}

class DefaultPostHogProvider : PostHogProvider {
    override fun capture(event: String, properties: Map<String, Any>?) {
        try {
            PostHog.capture(event, properties = properties)
        } catch (e: Throwable) {
            // Safe fallback
        }
    }
    override fun optIn() {
        try {
            PostHog.optIn()
        } catch (e: Throwable) {
            // Safe fallback
        }
    }
    override fun optOut() {
        try {
            PostHog.optOut()
        } catch (e: Throwable) {
            // Safe fallback
        }
    }
    override fun reset() {
        try {
            PostHog.reset()
        } catch (e: Throwable) {
            // Safe fallback
        }
    }
}

class PostHogAnalyticsSink(
    context: Context,
    apiKey: String,
    host: String,
    @Volatile private var consentEnabled: Boolean,
    private val postHog: PostHogProvider = DefaultPostHogProvider()
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
            PostHogAndroid.setup(context.applicationContext, config)
        } catch (e: Throwable) {
            // Safely catch any initialization, setup exceptions, or link errors (e.g. on JVM tests)
        }

        try {
            // Immediately synchronize the opt-in/opt-out status with the consent status
            if (consentEnabled) {
                postHog.optIn()
            } else {
                postHog.optOut()
                postHog.reset()
            }
        } catch (e: Throwable) {
            // Prevent crashes from PostHog state manipulation
        }
    }

    override fun setEnabled(consent: Boolean) {
        consentEnabled = consent
        try {
            if (consent) {
                postHog.optIn()
            } else {
                postHog.optOut()
                postHog.reset()
            }
        } catch (e: Throwable) {
            // Prevent crashes
        }
    }

    override fun trackFirstRun() {
        if (!consentEnabled) return
        val properties = mapOf(
            "app_version" to BuildConfig.VERSION_NAME,
            "\$ip" to "0.0.0.0"
        )
        try {
            postHog.capture(event = "app_first_run", properties = properties)
        } catch (e: Throwable) {
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
            postHog.capture(event = "backend_selected", properties = properties)
        } catch (e: Throwable) {
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
            postHog.capture(event = "agent_run_completed", properties = properties)
        } catch (e: Throwable) {
            // Prevent exceptions
        }
    }
}
