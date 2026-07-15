package com.marmaton.agent.llm

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.TextPart
import com.marmaton.agent.model.AgentAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

object GemmaAgentEngine {
    private const val TAG = "GemmaAgentEngine"

    private var generativeModel: GenerativeModel? = null

    private val _modelStatus = MutableStateFlow<String>("Checking local Gemma status...")
    val modelStatus: StateFlow<String> = _modelStatus.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun initModel() {
        try {
            generativeModel = Generation.getClient()
            coroutineScope.launch {
                checkModelStatus()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Generation client", e)
            _modelStatus.value = "Error: ${e.message}"
        }
    }

    private suspend fun checkModelStatus() {
        val model = generativeModel ?: return
        try {
            val status = model.checkStatus()
            when (status) {
                FeatureStatus.UNAVAILABLE -> {
                    _modelStatus.value = "Gemma 4 is unavailable on this device configuration."
                    _isDownloading.value = false
                }
                FeatureStatus.DOWNLOADABLE -> {
                    _modelStatus.value = "Gemma 4 is ready to be downloaded."
                    _isDownloading.value = false
                }
                FeatureStatus.DOWNLOADING -> {
                    _modelStatus.value = "Gemma 4 is downloading..."
                    _isDownloading.value = true
                }
                FeatureStatus.AVAILABLE -> {
                    _modelStatus.value = "Gemma 4 is downloaded & ready on-device."
                    _isDownloading.value = false
                    warmupModel()
                }
            }
        } catch (e: Exception) {
            _modelStatus.value = "Status check error: ${e.message}"
            Log.e(TAG, "Status check error", e)
        }
    }

    suspend fun startDownload() {
        val model = generativeModel ?: return
        try {
            _isDownloading.value = true
            _modelStatus.value = "Starting Gemma 4 download..."
            model.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> {
                        _modelStatus.value = "Download started..."
                    }
                    is DownloadStatus.DownloadProgress -> {
                        val mbDownloaded = status.totalBytesDownloaded.toFloat() / (1024 * 1024)
                        _modelStatus.value = "Downloading: %.1f MB".format(mbDownloaded)
                    }
                    DownloadStatus.DownloadCompleted -> {
                        _modelStatus.value = "Gemma 4 download completed successfully!"
                        _isDownloading.value = false
                        warmupModel()
                    }
                    is DownloadStatus.DownloadFailed -> {
                        _modelStatus.value = "Download failed: ${status.e.message}"
                        _isDownloading.value = false
                    }
                }
            }
        } catch (e: Exception) {
            _modelStatus.value = "Download exception: ${e.message}"
            _isDownloading.value = false
            Log.e(TAG, "Download error", e)
        }
    }

    private suspend fun warmupModel() {
        try {
            generativeModel?.warmup()
            Log.d(TAG, "Warmup invoked on Gemma model")
        } catch (e: Exception) {
            Log.e(TAG, "Warmup failed", e)
        }
    }

    suspend fun reasonAction(userGoal: String, serializedScreen: String): AgentAction? {
        val model = generativeModel ?: return null

        val systemPrompt = """
            You are Marmaton, an extremely capable local Android device agent. Your goal is to help the user achieve: "$userGoal"
            Below is the current screen state in a minimized JSON format of on-screen elements (bnd represents bounds: [left, top, right, bottom]).

            You must output a structured action. Format your response exactly as a single JSON object matching this schema:
            {
               "actionType": "CLICK" | "SWIPE_UP" | "SWIPE_DOWN" | "TYPE_TEXT" | "WAIT" | "FINISHED",
               "targetId": "string or null",
               "bounds": [left, top, right, bottom] or null,
               "textToType": "string or null",
               "reasoning": "your step-by-step reasoning"
            }

            Strategy:
            1. If you see a button, switch, or field matching the goal, return CLICK with the correct bounds or target ID.
            2. If you need to scroll down/up to find more settings/options, output SWIPE_DOWN or SWIPE_UP.
            3. If you need to type, output TYPE_TEXT with the textToType and the target ID/bounds of the text field.
            4. If the goal has been achieved successfully (e.g. settings changed, switch toggled, goal visible), output FINISHED.
            5. If you need to wait for a screen transition, output WAIT.

            Current Screen State:
            $serializedScreen
        """.trimIndent()

        Log.d(TAG, "Sending prompt to Gemma: $systemPrompt")

        // Generate content as raw text and parse JSON string manually using Kotlinx Serialization
        return try {
            val rawRequest = GenerateContentRequest.Builder(TextPart(systemPrompt)).build()
            val rawResponse = model.generateContent(rawRequest)
            val rawText = rawResponse.candidates.firstOrNull()?.text ?: ""
            Log.d(TAG, "Gemma Action output (Raw text): $rawText")

            // Clean up Markdown JSON code-block wrapping if any
            val cleanJson = cleanJsonString(rawText)
            val action = jsonParser.decodeFromString<AgentAction>(cleanJson)
            Log.d(TAG, "Gemma Action output (Parsed JSON Fallback): $action")
            action
        } catch (e: Exception) {
            Log.e(TAG, "Failed reasoning action via raw JSON and fallback parsing", e)
            null
        }
    }

    fun cleanJsonString(input: String): String {
        var clean = input.trim()
        if (clean.startsWith("```")) {
            clean = clean.removePrefix("```json").removePrefix("```").trim()
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```").trim()
        }
        // Extract first '{' to last '}' to strip any external conversational text from Gemma
        val firstBrace = clean.indexOf('{')
        val lastBrace = clean.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            clean = clean.substring(firstBrace, lastBrace + 1)
        }
        return clean
    }
}
