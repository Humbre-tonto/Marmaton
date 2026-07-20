package com.marmaton.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.marmaton.agent.analytics.Analytics
import com.marmaton.agent.audio.AgentVoice
import com.marmaton.agent.llm.BackendConfig
import com.marmaton.agent.llm.BackendFactory
import com.marmaton.agent.llm.BackendStatus
import com.marmaton.agent.llm.BackendType
import com.marmaton.agent.llm.GemmaAgentEngine
import com.marmaton.agent.llm.SettingsPersistence
import com.marmaton.agent.parser.ScreenTreeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AgentForegroundService : Service() {

    private val job = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + job)

    private var loopJob: Job? = null

    companion object {
        private const val TAG = "AgentForegroundService"
        private const val CHANNEL_ID = "MarmatonAgentChannel"
        private const val NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _userGoal = MutableStateFlow("")
        val userGoal: StateFlow<String> = _userGoal.asStateFlow()

        private val _runLog = MutableStateFlow<List<String>>(emptyList())
        val runLog: StateFlow<List<String>> = _runLog.asStateFlow()

        fun log(message: String) {
            Log.d(TAG, message)
            com.marmaton.agent.util.FileLogger.log("Agent", message)
            _runLog.update { current ->
                val next = current.toMutableList()
                next.add(message)
                if (next.size > 200) {
                    next.removeAt(0)
                }
                next
            }
        }

        fun startAgent(context: Context, goal: String) {
            _userGoal.value = goal
            _runLog.value = emptyList()
            val intent = Intent(context, AgentForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAgent(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle stop action at the very top of onStartCommand to prevent driving state to running
        if (intent?.action == "STOP_SERVICE") {
            _isRunning.value = false
            stopSelf()
            return START_NOT_STICKY
        }

        _isRunning.value = true
        log("[Service] Marmaton Agent Loop Started")

        val stopIntent = Intent(this, AgentForegroundService::class.java)
        stopIntent.action = "STOP_SERVICE"
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Marmaton AI Agent Running")
            .setContentText("Goal: ${_userGoal.value}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_delete, "Emergency Stop", stopPendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startAutonomousLoop()

        return START_STICKY
    }

    private fun startAutonomousLoop() {
        // Guard against multiple concurrent autonomous loops
        if (loopJob?.isActive == true) {
            return
        }

        loopJob = serviceScope.launch {
            var stepCount = 0
            val startTime = System.currentTimeMillis()
            var isSuccess = false
            var wasExceptionThrown = false
            val backendConfig = try {
                SettingsPersistence(applicationContext).configFlow.first()
            } catch (e: Exception) {
                null
            }

            if (backendConfig != null) {
                val (type, modelName, providerHost) = getBackendSelectedDetails(backendConfig)
                Analytics.get().trackBackendSelected(type, modelName, providerHost)
            }

            AgentVoice.speak("Starting. Goal: ${_userGoal.value}")

            try {
                while (_isRunning.value) {
                    val service = MarmatonAccessibilityService.instance
                    if (service == null) {
                        log("[Service] Waiting for Marmaton Accessibility Service to be enabled...")
                        delay(5000)
                        continue
                    }

                    log("[Parser] Traversing screen elements...")
                    val rootNode = try {
                        service.rootInActiveWindow
                    } catch (e: Exception) {
                        null
                    }

                    if (rootNode == null) {
                        log("[Parser] Active window is empty/null. Retrying...")
                        delay(3000)
                        continue
                    }

                    val serializedScreen = ScreenTreeParser.serializeTree(rootNode)
                    rootNode.recycle()

                    log("[Reasoner] Analyzing screen state and reasoning next action...")
                    val goal = _userGoal.value
                    val action = try {
                        val backend = BackendFactory.createActiveBackend(service)
                        val status = backend.status()
                        if (status !is BackendStatus.Ready) {
                            val reason = when (status) {
                                is BackendStatus.NotReady -> status.reason
                                is BackendStatus.Unavailable -> status.reason
                                else -> "Unknown status"
                            }
                            log("[Error] Active backend is not ready: $reason")
                            null
                        } else {
                            // Run reasoning inline so we can surface the model's raw output when
                            // it can't be parsed into an action (e.g. a small model that doesn't
                            // emit valid JSON).
                            val prompt = GemmaAgentEngine.buildSystemPrompt(goal, serializedScreen)
                            val raw = backend.generate(prompt)
                            val parsed = GemmaAgentEngine.parseAction(raw)
                            if (parsed == null) {
                                val preview = raw.trim().replace("\n", " ").take(300)
                                log(
                                    if (preview.isBlank())
                                        "[Error] Model returned an empty response. The model may have failed to load or run."
                                    else
                                        "[Error] Model output wasn't a valid JSON action. Raw output: $preview"
                                )
                            }
                            parsed
                        }
                    } catch (e: Throwable) {
                        log("[Error] Backend error: ${e.message ?: e.toString()}")
                        null
                    }

                    if (action == null) {
                        log("[Reasoner] Reasoning failed, backend not ready, or timed out. Waiting...")
                        delay(4000)
                        continue
                    }

                    stepCount++

                    log("[Reasoner] Think: ${action.reasoning}")
                    log("[Action] Decision: ${action.actionType} | Target: ${action.targetId ?: "N/A"} | Bounds: ${action.bounds ?: "N/A"}")

                    when (action.actionType) {
                        "FINISHED" -> {
                            log("[Agent] Goal Successfully Achieved! Stopping loop.")
                            AgentVoice.speak("Goal achieved.")
                            isSuccess = true
                            _isRunning.value = false
                            stopSelf()
                            break
                        }
                        "WAIT" -> {
                            log("[Action] Waiting for 3 seconds...")
                            delay(3000)
                        }
                        else -> {
                            AgentVoice.speak(action.reasoning.ifBlank { action.actionType })
                            val dispatched = service.executeAction(action)
                            if (dispatched) {
                                log("[Action] Action executed successfully.")
                            } else {
                                log("[Action] Warning: Action dispatch returned false/failed.")
                            }
                            delay(3000) // Delay to let screen render new state
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during autonomous loop", e)
                wasExceptionThrown = true
                AgentVoice.speak("I ran into an error.")
            } finally {
                val durationMs = System.currentTimeMillis() - startTime
                val type = when (backendConfig?.selectedType) {
                    BackendType.LOCAL_FILE, BackendType.AICORE -> "on_device"
                    BackendType.OLLAMA -> "ollama"
                    BackendType.CLOUD -> "cloud"
                    null -> "unknown"
                }
                val isCancelled = !_isRunning.value || !isActive
                val outcome = when {
                    isSuccess -> "success"
                    isCancelled -> "stopped"
                    wasExceptionThrown -> "error"
                    else -> "stopped"
                }
                Analytics.get().trackRunCompleted(type, outcome, stepCount, durationMs)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        job.cancel()
        BackendFactory.closeCurrent()
        log("[Service] Marmaton Agent Loop Stopped Safely")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Marmaton Agent Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
