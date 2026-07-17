package com.marmaton.agent.llm

import android.util.Log
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class CloudBackend(
    val baseUrl: String = "https://api.openai.com",
    private val apiKey: String,
    val modelName: String
) : LlmBackend {
    override val displayName: String = "Cloud API ($modelName)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun status(): BackendStatus {
        if (baseUrl.isBlank()) {
            return BackendStatus.NotReady("Cloud base URL is empty. Please configure in Settings.")
        }
        if (apiKey.isBlank()) {
            return BackendStatus.NotReady("Cloud API key is empty. Please configure in Settings.")
        }
        if (modelName.isBlank()) {
            return BackendStatus.NotReady("Cloud model name is empty. Please configure in Settings.")
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    BackendStatus.NotReady("Cloud API Key is invalid or unauthorized (HTTP ${response.code})")
                } else if (response.isSuccessful) {
                    BackendStatus.Ready
                } else {
                    BackendStatus.Ready
                }
            }
        } catch (e: Exception) {
            BackendStatus.Unavailable("Cloud API unreachable: ${e.message}")
        }
    }

    override suspend fun generate(prompt: String): String {
        val url = "${baseUrl.trimEnd('/')}/v1/chat/completions"

        val requestJson = buildJsonObject {
            put("model", modelName)
            put("stream", false)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw IOException("Cloud generation failed with HTTP ${response.code}: $errBody")
            }
            val responseBody = response.body?.string() ?: throw IOException("Empty response from Cloud API")
            return try {
                val element = json.parseToJsonElement(responseBody).jsonObject
                val choices = element["choices"]?.jsonArray
                val firstChoice = choices?.get(0)?.jsonObject
                val message = firstChoice?.get("message")?.jsonObject
                message?.get("content")?.jsonPrimitive?.content
                    ?: throw IOException("No message content in Cloud API response")
            } catch (e: Exception) {
                throw IOException("Failed to parse Cloud API response: $responseBody", e)
            }
        }
    }
}
