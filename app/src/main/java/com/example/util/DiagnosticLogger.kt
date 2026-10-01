package com.example.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object DiagnosticLogger {

    private const val TAG_PREFIX = "TeleDriveDiag"
    private const val MAX_LOGS = 300

    enum class Level {
        DEBUG, INFO, WARN, ERROR, SUCCESS
    }

    data class LogEntry(
        val id: Long,
        val timestamp: Long,
        val formattedTime: String,
        val tag: String,
        val level: Level,
        val message: String,
        val details: String? = null
    )

    private val idCounter = AtomicLong(1)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    @Synchronized
    fun log(
        tag: String,
        message: String,
        level: Level = Level.INFO,
        details: String? = null
    ) {
        val now = System.currentTimeMillis()
        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = now,
            formattedTime = timeFormat.format(Date(now)),
            tag = tag,
            level = level,
            message = message,
            details = details
        )

        // Console / Logcat output
        val fullTag = "$TAG_PREFIX:$tag"
        val logcatMsg = if (details.isNullOrBlank()) message else "$message | Details: $details"
        when (level) {
            Level.DEBUG -> Log.d(fullTag, logcatMsg)
            Level.INFO, Level.SUCCESS -> Log.i(fullTag, logcatMsg)
            Level.WARN -> Log.w(fullTag, logcatMsg)
            Level.ERROR -> Log.e(fullTag, logcatMsg)
        }

        val updated = (_logs.value + entry).takeLast(MAX_LOGS)
        _logs.value = updated
    }

    fun logBotAdminCheck(
        channelId: String,
        attempt: Int,
        maxAttempts: Int,
        status: String,
        level: Level = Level.INFO,
        details: String? = null
    ) {
        log(
            tag = "AdminVerification",
            message = "[$attempt/$maxAttempts] القناة: $channelId -> $status",
            level = level,
            details = details
        )
    }

    fun logUploadStream(
        fileName: String,
        stage: String,
        level: Level = Level.INFO,
        details: String? = null
    ) {
        log(
            tag = "UploadStream",
            message = "ملف: $fileName -> $stage",
            level = level,
            details = details
        )
    }

    fun logTdlib(
        event: String,
        level: Level = Level.INFO,
        details: String? = null
    ) {
        log(
            tag = "TDLibEngine",
            message = event,
            level = level,
            details = details
        )
    }

    fun logError(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        rawResponse: String? = null
    ) {
        val detailParts = mutableListOf<String>()
        if (throwable != null) {
            detailParts.add("Exception: ${throwable.javaClass.simpleName} - ${throwable.localizedMessage}")
        }
        if (!rawResponse.isNullOrBlank()) {
            detailParts.add("Raw Response: $rawResponse")
        }
        val fullDetails = if (detailParts.isNotEmpty()) detailParts.joinToString(" | ") else null

        log(
            tag = tag,
            message = message,
            level = Level.ERROR,
            details = fullDetails
        )
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun getFormattedLogs(): String {
        return _logs.value.joinToString("\n") { entry ->
            val levelIcon = when (entry.level) {
                Level.DEBUG -> "🔍"
                Level.INFO -> "ℹ️"
                Level.SUCCESS -> "✅"
                Level.WARN -> "⚠️"
                Level.ERROR -> "❌"
            }
            "[$levelIcon ${entry.formattedTime}] [${entry.tag}] ${entry.message}" +
                    if (entry.details != null) "\n   ↳ ${entry.details}" else ""
        }
    }
}
