package com.marmaton.agent.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends timestamped diagnostic lines to a persistent log file so a run can be captured and
 * shared for debugging. The file lives under the app's external files dir
 * (`Android/data/<pkg>/files/logs/marmaton.log`), which is reachable with a file manager and via
 * the in-app "Share log" action. Rotates at ~2 MB so it never grows unbounded.
 */
object FileLogger {

    private const val TAG = "FileLogger"
    private const val MAX_BYTES = 2_000_000L

    private val lock = Any()
    private val timestamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        try {
            val dir = File(
                context.getExternalFilesDir(null) ?: context.filesDir,
                "logs"
            ).apply { mkdirs() }
            logFile = File(dir, "marmaton.log")
            log("FileLogger", "=== Log started ===")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to init file logger", e)
        }
    }

    fun log(tag: String, message: String) {
        val file = logFile ?: return
        synchronized(lock) {
            try {
                if (file.length() > MAX_BYTES) {
                    val backup = File(file.parentFile, "marmaton.log.1")
                    if (backup.exists()) backup.delete()
                    file.renameTo(backup)
                }
                FileWriter(file, true).use { w ->
                    w.append(timestamp.format(Date()))
                        .append("  ")
                        .append(tag)
                        .append("  ")
                        .append(message)
                        .append('\n')
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write log", e)
            }
        }
    }

    /** The current log file, or null if not initialized. */
    fun file(): File? = logFile

    fun clear() {
        synchronized(lock) {
            try {
                logFile?.writeText("")
                log("FileLogger", "=== Log cleared ===")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear log", e)
            }
        }
    }
}
