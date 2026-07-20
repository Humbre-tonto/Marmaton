package com.marmaton.agent.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * [TtsEngine] backed by the on-device Android system TextToSpeech. No model download or extra
 * dependency required; works offline on devices with a TTS engine installed.
 */
class AndroidTtsEngine(context: Context) : TtsEngine {

    @Volatile
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                try {
                    tts?.language = Locale.getDefault()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set TTS language", e)
                }
            } else {
                Log.w(TAG, "TextToSpeech init failed with status $status")
            }
        }
    }

    override fun isReady(): Boolean = ready

    override fun speak(text: String) {
        if (!ready || text.isBlank()) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
        } catch (e: Exception) {
            Log.w(TAG, "TTS speak failed", e)
        }
    }

    override fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "TTS stop failed", e)
        }
    }

    override fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "TTS shutdown failed", e)
        } finally {
            tts = null
            ready = false
        }
    }

    companion object {
        private const val TAG = "AndroidTtsEngine"
    }
}
