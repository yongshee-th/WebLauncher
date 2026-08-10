package com.yongsheeth.weblauncher

import android.webkit.ConsoleMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LogEntry(
    val level: ConsoleMessage.MessageLevel,
    val message: String,
    val sourceId: String,
    val lineNumber: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class NetworkEntry(
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

object DiagnosticRepository {

    private val _consoleLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val consoleLogs: StateFlow<List<LogEntry>> = _consoleLogs.asStateFlow()

    private val _networkLogs = MutableStateFlow<List<NetworkEntry>>(emptyList())
    val networkLogs: StateFlow<List<NetworkEntry>> = _networkLogs.asStateFlow()

    private const val MAX_LOGS = 100

    fun addConsoleLog(message: ConsoleMessage) {
        val entry = LogEntry(
            message.messageLevel(),
            message.message(),
            message.sourceId(),
            message.lineNumber()
        )
        _consoleLogs.update { (listOf(entry) + it).take(MAX_LOGS) }
    }

    fun addNetworkRequest(url: String) {
        val entry = NetworkEntry(url)
        _networkLogs.update { (listOf(entry) + it).take(MAX_LOGS) }
    }

    fun clearLogs() {
        _consoleLogs.value = emptyList()
        _networkLogs.value = emptyList()
    }
}
