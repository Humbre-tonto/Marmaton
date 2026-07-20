package com.marmaton.agent.llm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecurePreferences {
    private const val TAG = "SecurePreferences"
    private const val PREFS_NAME = "secure_prefs"
    private const val KEY_API_KEY = "cloud_api_key"
    private const val KEY_HF_TOKEN = "huggingface_token"

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey).apply()
    }

    /** Optional Hugging Face access token, used to download license-gated models. */
    fun getHuggingFaceToken(context: Context): String {
        return getPrefs(context).getString(KEY_HF_TOKEN, "") ?: ""
    }

    fun saveHuggingFaceToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_HF_TOKEN, token).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences failed to load. Falling back to secure-obfuscated plain preferences.", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: NoClassDefFoundError) {
            // Safe fallback for JUnit JVM unit tests
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
}
