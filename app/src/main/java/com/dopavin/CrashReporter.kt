package com.dopavin

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Catches any uncaught exception, saves a readable report and opens [CrashActivity] (own process,
 * plain views, so it still works when the Compose UI is what crashed). The file stays until the
 * report screen has shown it, so a crash that could not open the screen is shown on the next start.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"
    private const val HISTORY_DIR = "crash_history"
    private const val HISTORY_MAX = 30
    private const val MAX_CHARS = 60_000
    @Volatile private var handling = false

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (handling) {
                previous?.uncaughtException(thread, error)
                return@setDefaultUncaughtExceptionHandler
            }
            handling = true
            try {
                val text = build(app, thread, error)
                File(app.filesDir, FILE).writeText(text)
                if (isDebuggable(app)) runCatching { saveHistory(app, text) }
                app.startActivity(
                    Intent(app, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Throwable) {
                // Reporting must never hide the original crash.
                previous?.uncaughtException(thread, error)
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    /** True only for debuggable builds: the crash history exists (and is shown in Settings) there only. */
    fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    class Entry(val file: File, val time: Long, val text: String) {
        /** First line of the exception, e.g. "java.lang.IllegalStateException: boom". */
        val headline: String
            get() = text.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("Dopavin crash report") &&
                !it.startsWith("Time:") && !it.startsWith("App:") && !it.startsWith("Device:") &&
                !it.startsWith("Android:") && !it.startsWith("Thread:") }.orEmpty().take(140)
    }

    private fun historyDir(context: Context) = File(context.filesDir, HISTORY_DIR).apply { mkdirs() }

    private fun saveHistory(context: Context, text: String) {
        val dir = historyDir(context)
        File(dir, "crash_${System.currentTimeMillis()}.txt").writeText(text)
        dir.listFiles()?.sortedBy { it.name }?.dropLast(HISTORY_MAX)?.forEach { it.delete() }
    }

    /** Stored crashes, newest first (debuggable builds only). */
    fun history(context: Context): List<Entry> {
        if (!isDebuggable(context)) return emptyList()
        return runCatching {
            historyDir(context).listFiles().orEmpty()
                .sortedByDescending { it.name }
                .mapNotNull { f -> runCatching { Entry(f, f.lastModified(), f.readText()) }.getOrNull() }
        }.getOrDefault(emptyList())
    }

    fun deleteHistory(entry: Entry) {
        runCatching { entry.file.delete() }
    }

    fun clearHistory(context: Context) {
        runCatching { historyDir(context).listFiles()?.forEach { it.delete() } }
    }

    /** The saved report, or null when there is none. */
    fun pending(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    private fun build(context: Context, thread: Thread, error: Throwable): String {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
            appendLine("Dopavin crash report")
            appendLine("Time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()))
            appendLine("App: ${context.packageName} ${info?.versionName.orEmpty()}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }
        return if (text.length > MAX_CHARS) text.take(MAX_CHARS) + "\n… (cut)" else text
    }
}
