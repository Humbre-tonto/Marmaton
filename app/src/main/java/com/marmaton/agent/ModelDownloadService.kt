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

        val client = OkHttpClient.Builder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val token = SecurePreferences.getHuggingFaceToken(applicationContext)

        // The catalog's exact filename can drift and 404. If it does, look up a real .task file
        // in the model's Hugging Face repo and download that instead.
        var effectiveUrl = model.url
        var effectiveFileName = model.fileName
        val headCode = try {
            val hb = Request.Builder().url(model.url).head()
            if (token.isNotBlank()) hb.header("Authorization", "Bearer $token")
            client.newCall(hb.build()).execute().use { it.code }
        } catch (e: Exception) {
            null
        }
        if (headCode == 404) {
            val resolved = resolveRealTaskFile(client, model.url, token)
            if (resolved != null) {
                effectiveUrl = resolved.first
                effectiveFileName = resolved.second
            } else {
                fail("No Android-compatible file was found for ${model.name}. It may only be published as a web build — try Gemma 3 1B or another model.")
                return
            }
        }

        // Web `.task` bundles use a different container the on-device engine can't open.
        if (effectiveFileName.contains("web", ignoreCase = true)) {
            fail("The available file for ${model.name} is a web build that can't run on-device. Try Gemma 3 1B or another model.")
            return
        }

        val finalFile = File(dir, effectiveFileName)
        val partFile = File(dir, effectiveFileName + ".part")

        try {
            var existing = if (partFile.exists()) partFile.length() else 0L

            val requestBuilder = Request.Builder().url(effectiveUrl)
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
            persistence.updateLocalModel(finalFile.absolutePath, effectiveUrl, effectiveFileName)
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

    /**
     * When the catalog URL 404s, query the Hugging Face API for the repo's real `.task` files and
     * pick a suitable one (preferring non-web q8 → q4 → first). Returns (downloadUrl, fileName).
     */
    private fun resolveRealTaskFile(
        client: OkHttpClient,
        originalUrl: String,
        token: String
    ): Pair<String, String>? {
        val repoId = Regex("huggingface\\.co/(.+?)/resolve/")
            .find(originalUrl)?.groupValues?.getOrNull(1) ?: return null
        val apiBuilder = Request.Builder().url("https://huggingface.co/api/models/$repoId")
        if (token.isNotBlank()) apiBuilder.header("Authorization", "Bearer $token")
        return try {
            client.newCall(apiBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bodyStr = resp.body?.string() ?: return null
                val siblings = org.json.JSONObject(bodyStr).optJSONArray("siblings") ?: return null
                val tasks = ArrayList<String>()
                for (i in 0 until siblings.length()) {
                    val f = siblings.optJSONObject(i)?.optString("rfilename") ?: continue
                    if (f.endsWith(".task")) tasks.add(f)
                }
                if (tasks.isEmpty()) return null
                // Web variants can't be opened by the on-device engine — never fall back to one.
                val candidates = tasks.filterNot { it.contains("web", ignoreCase = true) }
                if (candidates.isEmpty()) return null
                val chosen = candidates.firstOrNull { it.contains("q8", ignoreCase = true) }
                    ?: candidates.firstOrNull { it.contains("q4", ignoreCase = true) }
                    ?: candidates.first()
                Pair("https://huggingface.co/$repoId/resolve/main/$chosen?download=true", chosen)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve real .task file from HF API", e)
            null
        }
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
