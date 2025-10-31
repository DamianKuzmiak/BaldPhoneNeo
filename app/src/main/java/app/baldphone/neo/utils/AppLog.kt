package app.baldphone.neo.utils

import android.util.Log

import com.bald.uriah.baldphone.BuildConfig

import java.util.concurrent.atomic.AtomicInteger

/**
 * AppLog – Optimized logging utility.
 * Uses inline functions to avoid string allocation when logging is disabled.
 */
object AppLog {
    private const val MAX_LINES = 200
    private val buffer = arrayOfNulls<LogEntry>(MAX_LINES)
    private val index = AtomicInteger(0)

    private data class LogEntry(
        val timestamp: Long, val level: Int, val tag: String, val msg: String
    )

    inline fun v(tag: String, msg: () -> String) = logIfEnabled(Log.VERBOSE, tag, msg)
    inline fun d(tag: String, msg: () -> String) = logIfEnabled(Log.DEBUG, tag, msg)
    inline fun i(tag: String, msg: () -> String) = logIfEnabled(Log.INFO, tag, msg)
    inline fun w(tag: String, msg: () -> String) = logIfEnabled(Log.WARN, tag, msg)
    inline fun e(tag: String, msg: () -> String) = logIfEnabled(Log.ERROR, tag, msg)

    /** Overloads for Throwable */
    fun w(t: Throwable?, msg: String? = null) = add(Log.WARN, "AppLog", msg ?: "", t)
    fun e(t: Throwable?, msg: String? = null) = add(Log.ERROR, "AppLog", msg ?: "", t)

    /** Backward compatibility overloads */
    @JvmStatic
    fun v(tag: String, msg: String) = add(Log.VERBOSE, tag, msg, null)

    @JvmStatic
    fun d(tag: String, msg: String) = add(Log.DEBUG, tag, msg, null)

    @JvmStatic
    fun i(tag: String, msg: String) = add(Log.INFO, tag, msg, null)

    @JvmStatic
    fun w(tag: String, msg: String) = add(Log.WARN, tag, msg, null)

    @JvmStatic
    fun e(tag: String, msg: String) = add(Log.ERROR, tag, msg, null)

    @PublishedApi
    internal inline fun logIfEnabled(level: Int, tag: String, msg: () -> String) {
        if (BuildConfig.DEBUG || level >= Log.WARN) {
            add(level, tag, msg(), null)
        }
    }

    @PublishedApi
    internal fun add(level: Int, tag: String, message: String, t: Throwable?) {
        // Keep for Java users
        if (!BuildConfig.DEBUG && level < Log.WARN) return

        val fullMsg = if (t != null) "$message\n${Log.getStackTraceString(t)}" else message

        // System Log
        Log.println(level, tag, fullMsg)

        // Ring Buffer
        val ts = System.currentTimeMillis()
        val i = (index.getAndIncrement() and Int.MAX_VALUE) % MAX_LINES
        buffer[i] = LogEntry(ts, level, tag, fullMsg)
    }

    @JvmStatic
    fun dumpRecent(): String {
        val sb = StringBuilder()
        val currentIndex = index.get()
        val count = currentIndex.coerceAtMost(MAX_LINES)
        val start =
            if (currentIndex < MAX_LINES) 0 else (currentIndex and Int.MAX_VALUE) % MAX_LINES

        for (i in 0 until count) {
            val pos = (start + i) % MAX_LINES
            val entry = buffer[pos] ?: continue
            val ts = entry.timestamp.toFullDateTimeString(includeMillis = true)
            sb.appendLine("$ts ${levelToString(entry.level)}/${entry.tag}: ${entry.msg}")
        }
        return sb.toString()
    }

    private fun levelToString(level: Int) = when (level) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        else -> "?"
    }
}
