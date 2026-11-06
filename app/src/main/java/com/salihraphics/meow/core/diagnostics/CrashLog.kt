package com.salihraphics.meow.core.diagnostics

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash and freeze records kept on the device, for the student to share when something went
 * wrong (Ayarlar → Hata kayıtları). Nothing is sent anywhere by the app.
 *
 * - A crash in the app is written down with its stack trace, then handed to the system's own
 *   handler, so it still crashes exactly as before: nothing is hidden or swallowed.
 * - On Android 11+, the system's records of how the app last ended (an ANR with its thread
 *   dump, a native crash, being killed for memory) are copied in on the next start.
 *
 * Files live in the app's private storage, separate from PDFs and the database, and only the
 * newest [MAX_FILES] are kept.
 */
class CrashLog(private val dir: File, private val version: String) {
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write("crash", "Çökme (thread: ${thread.name})", stackTrace(error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Copies the system's exit records (ANR traces, native crashes, memory kills) not seen yet. */
    fun collectExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val am = context.getSystemService(ActivityManager::class.java) ?: return
            val seen = File(dir, LAST_SEEN).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L
            val reasons = am.getHistoricalProcessExitReasons(context.packageName, 0, 10)
            var newest = seen
            reasons.filter { it.timestamp > seen && it.reason in INTERESTING }.sortedBy { it.timestamp }.forEach { info ->
                val trace = if (info.reason == ApplicationExitInfo.REASON_ANR || info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                    runCatching { info.traceInputStream?.use { it.readBytes().take(MAX_TRACE_BYTES).toByteArray().decodeToString() } }.getOrNull()
                } else {
                    null
                }
                val text = buildString {
                    appendLine("Sistem kaydı: ${reasonName(info.reason)}")
                    appendLine("Zaman: ${format(info.timestamp)}")
                    appendLine("Açıklama: ${info.description ?: "-"}")
                    appendLine("Bellek (PSS/RSS kB): ${info.pss} / ${info.rss}")
                    if (trace != null) {
                        appendLine()
                        append(trace)
                    }
                }
                write(if (info.reason == ApplicationExitInfo.REASON_ANR) "anr" else "exit", reasonName(info.reason), text, info.timestamp)
                newest = maxOf(newest, info.timestamp)
            }
            if (newest > seen) File(dir, LAST_SEEN).writeText(newest.toString())
        }
    }

    /** Newest first. */
    fun entries(): List<File> = dir.listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedByDescending { it.name }

    fun clear() {
        entries().forEach { it.delete() }
    }

    fun write(kind: String, title: String, body: String, at: Long = System.currentTimeMillis()) {
        dir.mkdirs()
        val name = "${FILE_TIME.format(Date(at))}-$kind.txt"
        File(dir, name).writeText(
            buildString {
                appendLine("MEOW $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine(title)
                appendLine(format(at))
                appendLine()
                append(body)
            },
        )
        entries().drop(MAX_FILES).forEach { it.delete() }
    }

    private fun format(at: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(at))

    companion object {
        const val MAX_FILES = 10
        private const val MAX_TRACE_BYTES = 256 * 1024
        private const val LAST_SEEN = "exit-last-seen"
        private val FILE_TIME = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT)

        // Plain ints inlined at compile time; only read on the Android 11+ path above.
        @SuppressLint("InlinedApi")
        private val INTERESTING = setOf(
            ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_LOW_MEMORY,
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        )

        @SuppressLint("InlinedApi")
        private fun reasonName(reason: Int) = when (reason) {
            ApplicationExitInfo.REASON_ANR -> "Yanıt vermedi (ANR)"
            ApplicationExitInfo.REASON_CRASH -> "Çökme"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Yerel çökme"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "Bellek yetersiz (sistem kapattı)"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Aşırı kaynak kullanımı"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Başlatma hatası"
            else -> "Diğer ($reason)"
        }

        fun stackTrace(error: Throwable): String = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
    }
}
