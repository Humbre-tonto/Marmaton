package com.marmaton.agent.audio

/**
 * Backend-agnostic text-to-speech so the agent can narrate what it is doing. The current
 * implementation wraps Android's system [android.speech.tts.TextToSpeech]; a neural engine
 * (e.g. KittenTTS) can be added later behind this same interface.
 */
interface TtsEngine {
    /** True once the engine has finished async initialization and can speak. */
    fun isReady(): Boolean

    /** Queue [text] to be spoken. No-op if empty or not ready. */
    fun speak(text: String)

    /** Stop the current utterance and clear the queue. */
    fun stop()

    /** Release engine resources. */
    fun shutdown()
}
