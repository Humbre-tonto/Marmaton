package com.marmaton.agent.analytics

interface AnalyticsSink {
    fun trackBackendSelected(type: String, modelName: String, providerHost: String? = null)
    fun trackFirstRun()
    fun trackRunCompleted(type: String, outcome: String, steps: Int, durationMs: Long)
    fun setEnabled(consent: Boolean)
}
