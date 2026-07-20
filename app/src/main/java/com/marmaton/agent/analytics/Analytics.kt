package com.marmaton.agent.analytics

import android.content.Context
import com.marmaton.agent.BuildConfig

object Analytics {
    @Volatile
    private var instance: AnalyticsSink? = null

    fun init(context: Context, consent: Boolean) {
        val apiKey = BuildConfig.POSTHOG_API_KEY
        val host = BuildConfig.POSTHOG_HOST

        instance = if (apiKey.isBlank()) {
            NoopAnalyticsSink()
        } else {
            PostHogAnalyticsSink(context.applicationContext, apiKey, host, consent)
        }
    }

    fun get(): AnalyticsSink {
        return instance ?: NoopAnalyticsSink()
    }
}
