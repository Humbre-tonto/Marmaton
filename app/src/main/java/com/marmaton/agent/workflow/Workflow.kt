package com.marmaton.agent.workflow

import kotlinx.serialization.Serializable

/**
 * A user-defined workflow: a named, ordered list of natural-language steps the agent carries
 * out one after another (e.g. "Open WhatsApp", "Send 'on my way' to Mom", "Go back home").
 * Each step is run as an agent goal; when the agent reports the step FINISHED, the next step
 * begins. Steps are plain instructions, so a workflow can send messages, place calls, or change
 * settings — anything the agent can do on-screen.
 */
@Serializable
data class Workflow(
    val id: String,
    val name: String,
    val steps: List<String>
) {
    val stepCount: Int get() = steps.size
}
