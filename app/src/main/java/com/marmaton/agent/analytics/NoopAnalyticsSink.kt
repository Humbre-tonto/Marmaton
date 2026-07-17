package com.marmaton.agent.analytics

class NoopAnalyticsSink : AnalyticsSink {
    override fun trackBackendSelected(type: String, modelName: String, providerHost: String?) {}
    override fun trackFirstRun() {}
    override fun trackRunCompleted(type: String, outcome: String, steps: Int, durationMs: Long) {}
    override fun setEnabled(consent: Boolean) {}
}
