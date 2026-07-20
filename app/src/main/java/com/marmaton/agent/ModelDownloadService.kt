package com.marmaton.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.marmaton.agent.llm.CatalogModel
import com.marmaton.agent.llm.ModelCatalog
import com.marmaton.agent.llm.SecurePreferences
import com.marmaton.agent.llm.SettingsPersistence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Foreground service that downloads a curated [CatalogModel] over HTTPS into app-specific
 * external storage, with progress, resume-on-restart, and cancellation. On success it registers
 * the file as the active on-device model. Model downloads can be several GB, so this runs as a
 * foreground (dataSync) service to survive backgrounding.
 */
class ModelDownloadService : Service() {

    enum class Phase { IDLE, DOWNLOADING, VERIFYING, COMPLETED, FAILED, CANCELLED }

    data class DownloadUiState(
        val modelId: String? = null,
        val modelName: String = "",
        val phase: Phase = Phase.IDLE,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val message: String? = null
    ) {
        val fraction: Float
            get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    @Volatile
    private var cancelRequested = false

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val CHANNEL_ID = "MarmatonModelDownload"
        private const val NOTIFICATION_ID = 2002
        private const val EXTRA_MODEL_ID = "extra_model_id"
        const val ACTION_CANCEL = "com.marmaton.agent.action.CANCEL_MODEL_DOWNLOAD"

        private val _state = MutableStateFlow(DownloadUiState())
        val state: StateFlow<DownloadUiState> = _state.asStateFlow()

        fun start(context: Context, modelId: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                putExtra(EXTRA_MODEL_ID, modelId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelRequested = true
            return START_NOT_STICKY
        }

        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID)
        val model = modelId?.let { ModelCatalog.byId(it) }
        if (model == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // A download is already running; ignore duplicate starts.
        if (_state.value.phase == Phase.DOWNLOADING || _state.value.phase == Phase.VERIFYING) {
            return START_NOT_STICKY
        }

        cancelRequested = false
        startForegroundCompat(model.name, 0)
        _state.value = DownloadUiState(
            modelId = model.id,
            modelName = model.name,
            phase = Phase.DOWNLOADING,
            totalBytes = model.approxSizeBytes
        )

        scope.launch {
            runDownload(model)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun runDownload(model: CatalogModel) {
        val dir = (getExternalFilesDir("models") ?: File(filesDir, "models")).apply { mkdirs() }
        val finalFile = File(dir, model.fileName)
        val partFile = File(dir, model.fileName + ".part")

        val client = OkHttpClient.Builder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        try {
            var existing = if (partFile.exists()) partFile.length() else 0L

            val token = SecurePreferences.getHuggingFaceToken(applicationContext)
            val requestBuilder = Request.Builder().url(model.url)
            if (existing > 0L) {
                requestBuilder.header("Range", "bytes=$existing-")
            }
            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    fail(
                        if (model.gated)
                            "This model is license-gated. Open its page on Hugging Face, accept the license, then add a Hugging Face token below."
                        else
                            "Access denied (HTTP ${response.code})."
                    )
                    return
                }
                if (!response.isSuccessful) {
                    fail("Download failed (HTTP ${response.code}).")
                    return
                }

                val contentType = response.header("Content-Type") ?: ""
                if (contentType.startsWith("text/html")) {
                    fail("Got a web page instead of the model file — the model is likely gated. Add a Hugging Face token below.")
                    return
                }

                // If the server ignored our Range request, restart from the beginning.
                if (existing > 0L && response.code != 206) {
                    partFile.delete()
                    existing = 0L
                }

                val body = response.body ?: run { fail("Empty response body."); return }
                val remaining = body.contentLength()
                val total = when {
                    remaining >= 0L -> existing + remaining
                    model.approxSizeBytes > 0L -> model.approxSizeBytes
                    else -> 0L
                }

                RandomAccessFile(partFile, "rw").use { raf ->
                    raf.seek(existing)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(256 * 1024)
                        var downloaded = existing
                        var lastPublish = 0L
                        var lastNotify = 0L
                        while (true) {
                            if (cancelRequested) {
                                _state.value = _state.value.copy(
                                    phase = Phase.CANCELLED,
                                    message = "Download cancelled. Partial file kept — restart to resume."
                                )
                                return
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastPublish >= 200L) {
                                _state.value = _state.value.copy(
                                    phase = Phase.DOWNLOADING,
                                    downloadedBytes = downloaded,
                                    totalBytes = total
                                )
                                lastPublish = now
                            }
                            if (now - lastNotify >= 1000L) {
                                val pct = if (total > 0L) ((downloaded * 100) / total).toInt() else 0
                                updateNotification(model.name, pct)
                                lastNotify = now
                            }
                        }
                    }
                }
            }

            if (cancelRequested) {
                _state.value = _state.value.copy(phase = Phase.CANCELLED)
                return
            }

            // Move .part into place and register it as the active model.
            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }

            val persistence = SettingsPersistence(applicationContext)
            persistence.updateLocalModel(finalFile.absolutePath, model.url, model.fileName)
            persistence.updateSelectedType(com.marmaton.agent.llm.BackendType.LOCAL_FILE)

            _state.value = _state.value.copy(
                phase = Phase.COMPLETED,
                downloadedBytes = finalFile.length(),
                totalBytes = finalFile.length(),
                message = "${model.name} is ready."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            fail("Download error: ${e.message ?: "unknown"} — partial file kept, restart to resume.")
        }
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(phase = Phase.FAILED, message = message)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(modelName: String, pct: Int) {
        val notification = buildNotification(modelName, pct)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(modelName: String, pct: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(modelName, pct))
    }

    private fun buildNotification(modelName: String, pct: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading $modelName")
            .setContentText("$pct%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, pct, pct <= 0)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
