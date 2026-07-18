package com.marmaton.agent.llm

import android.util.Log
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OllamaBackend(
    val scheme: String = "http",
    val host: String,
    val port: Int = 11434,
    val modelName: String
) : LlmBackend {
    override val displayName: String = "Ollama ($modelName)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val baseUrl: String
        get() = "$scheme://$host:$port"

    override suspend fun status(): BackendStatus = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext BackendStatus.NotReady("Ollama host is empty. Please configure in Settings.")
        }
        if (modelName.isBlank()) {
            return@withContext BackendStatus.NotReady("Ollama model name is empty. Please configure in Settings.")
        }

        val request = Request.Builder()
            .url("$baseUrl/api/tags")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    var modelFound = false
                    try {
                        val tagsJson = json.parseToJsonElement(bodyString).jsonObject
                        val modelsArray = tagsJson["models"]?.jsonArray
                        if (modelsArray != null) {
                            for (m in modelsArray) {
                                val name = m.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                                if (name.equals(modelName, ignoreCase = true) || name.startsWith("$modelName:", ignoreCase = true)) {
                                    modelFound = true
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        modelFound = true
                    }

                    if (modelFound) {
                        BackendStatus.Ready
                    } else {
                        BackendStatus.NotReady("Ollama is reachable, but model '$modelName' was not found on the server.")
                    }
                } else {
                    BackendStatus.Unavailable("Ollama returned HTTP error ${response.code}")
                }
            }
        } catch (e: Exception) {
            BackendStatus.Unavailable("Ollama server unreachable: ${e.message}")
        }
    }

    override suspend fun generate(prompt: String): String {
        val url = "$baseUrl/api/chat"

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
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Ollama generation failed with HTTP ${response.code}: ${response.body?.string()}")
            }
            val responseBody = response.body?.string() ?: throw IOException("Empty response from Ollama")
            return try {
                val element = json.parseToJsonElement(responseBody).jsonObject
                val message = element["message"]?.jsonObject
                message?.get("content")?.jsonPrimitive?.content
                    ?: throw IOException("No message content in Ollama response")
            } catch (e: Exception) {
                try {
                    val element = json.parseToJsonElement(responseBody).jsonObject
                    val choices = element["choices"]?.jsonArray
                    val firstChoice = choices?.get(0)?.jsonObject
                    val msg = firstChoice?.get("message")?.jsonObject
                    msg?.get("content")?.jsonPrimitive?.content ?: throw IOException("Could not parse Ollama response")
                } catch (ex: Exception) {
                    throw IOException("Failed to parse Ollama response: $responseBody", ex)
                }
            }
        }
    }

    override fun close() {
        // OkHttpClient does not need explicit close under standard usage
    }
}
