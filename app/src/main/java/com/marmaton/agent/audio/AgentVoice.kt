package com.marmaton.agent.audio

import android.content.Context

/**
 * Process-wide holder for the agent's voice. The agent loop and UI call [speak]; nothing is
 * spoken unless the user has enabled voice output. Engine creation is lazy so no TTS resources
 * are allocated for users who keep voice off.
 */
object AgentVoice {

    @Volatile
    private var engine: TtsEngine? = null

    @Volatile
    var enabled: Boolean = false
        private set

    /** Initialize with the persisted preference. Safe to call multiple times. */
    fun init(context: Context, enabled: Boolean) {
        this.enabled = enabled
        if (enabled) ensureEngine(context)
    }

    /** Enable/disable at runtime (e.g. from the settings toggle). */
    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        if (value) {
            ensureEngine(context)
        } else {
            engine?.stop()
        }
    }

    /** Speak a short line if voice is enabled. */
    fun speak(text: String) {
        if (!enabled) return
        engine?.speak(text)
    }

    fun stop() {
        engine?.stop()
    }

    private fun ensureEngine(context: Context) {
        if (engine == null) {
            engine = AndroidTtsEngine(context.applicationContext)
        }
    }
}
