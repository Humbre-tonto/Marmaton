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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    /**
     * Decoupled system prompt builder to enable framework-free JVM unit testing of prompt generation.
     */
    fun buildSystemPrompt(userGoal: String, serializedScreen: String): String {
        return """
            You are Marmaton, an extremely capable local Android device agent. Your goal is to help the user achieve: "$userGoal"
            Below is the current screen state in a minimized JSON format of on-screen elements (bnd represents bounds: [left, top, right, bottom]).

            You must output a structured action. Format your response exactly as a single JSON object matching this schema:
            {
               "actionType": "CLICK" | "TYPE_TEXT" | "SWIPE_UP" | "SWIPE_DOWN" | "OPEN_APP" | "BACK" | "HOME" | "ENTER" | "WAIT" | "FINISHED",
               "targetId": "string or null",
               "bounds": [left, top, right, bottom] or null,
               "textToType": "string or null",
               "reasoning": "your step-by-step reasoning"
            }

            Output ONLY the JSON object — no markdown, no code fences, no extra text.
            "bounds" must be exactly four plain integers, e.g. [852, 134, 1036, 266] — never strings or quoted numbers.

            Action meanings:
            - CLICK: tap an element. Provide its bounds or targetId.
            - TYPE_TEXT: type into a text field. Put the text in textToType and the field's bounds/targetId.
            - SWIPE_UP / SWIPE_DOWN: scroll to reveal more content.
            - OPEN_APP: launch an app by name — put the app's name in textToType (e.g. "WhatsApp", "Settings", "Chrome"). ALWAYS prefer this to open an app instead of hunting for its icon on the home screen.
            - BACK: press the system Back button. HOME: go to the home screen.
            - ENTER: press the keyboard's Send/Search/Go key on the focused field (e.g. to submit a search or send a chat message after typing).
            - WAIT: wait for a screen transition to finish.
            - FINISHED: the goal is complete.

            Strategy:
            1. To use a specific app (messages, settings, a browser), start with OPEN_APP.
            2. If you see a button, switch, or field matching the goal, CLICK it (use its bounds or targetId).
            3. To fill a field, TYPE_TEXT, then CLICK the send/next button or use ENTER to submit.
            4. Use SWIPE_DOWN / SWIPE_UP to find off-screen content, and BACK to leave a wrong screen.
            5. When the goal is clearly done (setting changed, message sent, target visible), output FINISHED.
            6. If nothing useful is possible yet, output WAIT.

            Current Screen State:
            $serializedScreen
        """.trimIndent()
    }

    /**
     * Decoupled action parser to enable framework-free JVM unit testing of Gemma reasoning outputs.
     */
    fun parseAction(rawText: String): AgentAction? {
        return try {
            val cleanJson = cleanJsonString(rawText)
            // Parse tolerantly instead of strict decoding: small on-device models emit valid JSON
            // whose field shapes vary (e.g. bounds as ["852, 134, 1036, 266"] — a single string —
            // or a comma string, rather than four ints). Extract each field defensively.
            val obj = jsonParser.parseToJsonElement(cleanJson).jsonObject
            val actionType = obj["actionType"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()
                ?: return null
            AgentAction(
                actionType = actionType,
                targetId = obj["targetId"].asCleanString(),
                bounds = coerceBounds(obj["bounds"]),
                textToType = obj["textToType"].asCleanString(),
                reasoning = obj["reasoning"].asCleanString() ?: ""
            )
        } catch (e: Exception) {
            try {
                Log.e(TAG, "Failed parsing action JSON", e)
            } catch (le: RuntimeException) {
                // Handle JVM unit tests where android.util.Log is not mocked
                println("Failed parsing action JSON: ${e.message}")
            }
            null
        }
    }

    /** A string field, or null for missing/blank/"null" values. */
    private fun JsonElement?.asCleanString(): String? {
        if (this == null || this is JsonNull) return null
        val s = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return null
        return if (s.isBlank() || s.equals("null", ignoreCase = true)) null else s
    }

    /**
     * Coerce a bounds value into [left, top, right, bottom] regardless of how the model formatted
     * it — an int array, a single comma-joined string, an array containing one such string, etc.
     * Pulls the first four integers out of the raw JSON; returns null if fewer than four are found.
     */
    private fun coerceBounds(element: JsonElement?): List<Int>? {
        if (element == null || element is JsonNull) return null
        val nums = Regex("-?\\d+").findAll(element.toString())
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
        return if (nums.size >= 4) nums.take(4) else null
    }

    suspend fun reasonAction(userGoal: String, serializedScreen: String): AgentAction? {
        val model = generativeModel ?: return null
        val systemPrompt = buildSystemPrompt(userGoal, serializedScreen)
        Log.d(TAG, "Sending prompt to Gemma: $systemPrompt")

        return try {
            val rawRequest = GenerateContentRequest.Builder(TextPart(systemPrompt)).build()
            val rawResponse = model.generateContent(rawRequest)
            val rawText = rawResponse.candidates.firstOrNull()?.text ?: ""
            Log.d(TAG, "Gemma Action output (Raw text): $rawText")
            parseAction(rawText)
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
        // Extract the first balanced { ... } object, ignoring braces that appear inside strings.
        // This is more robust than first-'{'-to-last-'}' when a small model appends extra prose
        // (which may itself contain stray braces) after the JSON.
        val start = clean.indexOf('{')
        if (start != -1) {
            var depth = 0
            var inString = false
            var escaped = false
            var end = -1
            for (i in start until clean.length) {
                val c = clean[i]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> inString = false
                    }
                } else {
                    when (c) {
                        '"' -> inString = true
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                end = i
                                break
                            }
                        }
                    }
                }
            }
            clean = if (end != -1) clean.substring(start, end + 1) else clean.substring(start)
        }
        // Drop trailing commas before a closing brace/bracket, which small models often emit.
        clean = clean.replace(Regex(",\\s*([}\\]])"), "$1")
        return clean
    }
}
