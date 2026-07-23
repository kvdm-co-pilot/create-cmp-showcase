package com.kvdm.fuelled.inspector

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Debug-only crash capture: [install] sets a process-wide
 * [Thread.UncaughtExceptionHandler] that persists crash JSON to
 * `filesDir/inspector/crashes/` (bounded to the last [MAX_CRASHES]) so a crash survives the
 * process death that follows it — an in-memory ring buffer would not.
 *
 * MUST NEVER SWALLOW THE CRASH: after persisting, it always hands off to whatever handler was
 * installed before it (chained, not replaced) so system crash dialogs, `System.exit`, and any
 * other crash-reporting tool still behave exactly as if this class did not exist.
 */
object CrashRecorder {

    private const val TAG = "CmpInspector"
    private const val MAX_CRASHES = 20

    fun install(context: Context) {
        val crashDir = File(context.filesDir, "inspector/crashes").apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                persist(crashDir, throwable)
            } catch (t: Throwable) {
                // Persisting the crash record must never itself crash the crash handler.
                Log.w(TAG, "failed to persist crash record", t)
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, throwable)
                } else {
                    // No previous handler installed: fall back to the JVM's own default so the
                    // process still dies the normal way instead of hanging.
                    Runtime.getRuntime().exit(10)
                }
            }
        }
    }

    private fun persist(crashDir: File, throwable: Throwable) {
        val doc = buildJsonObject {
            put("timestamp", JsonPrimitive(isoNow()))
            put("exception", JsonPrimitive(throwable::class.qualifiedName ?: throwable.javaClass.name))
            put("message", throwable.message?.let { JsonPrimitive(it) } ?: JsonNull)
            put("frames", buildJsonArray {
                throwable.stackTrace.forEach { el ->
                    add(buildJsonObject {
                        put("className", JsonPrimitive(el.className))
                        put("methodName", JsonPrimitive(el.methodName))
                        put("fileName", el.fileName?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("lineNumber", JsonPrimitive(el.lineNumber))
                    })
                }
            })
        }
        File(crashDir, "crash-${System.currentTimeMillis()}.json")
            .writeText(Json.encodeToString(JsonElement.serializer(), doc))
        prune(crashDir)
    }

    /** Keep only the most recent [MAX_CRASHES] crash files (current boot + previous ones). */
    private fun prune(crashDir: File) {
        val files = crashDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return
        if (files.size <= MAX_CRASHES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CRASHES)
            .forEach { it.delete() }
    }

    /**
     * Every persisted crash's raw JSON text, newest first — spans the current boot AND any
     * previous ones ([install] never clears the directory, only [prune] bounds it).
     */
    fun readAll(context: Context): List<String> {
        val crashDir = File(context.filesDir, "inspector/crashes")
        val files = crashDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }
            .mapNotNull { runCatching { it.readText() }.getOrNull() }
    }

    private fun isoNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}
