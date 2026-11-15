package com.honghoaibao.bbooster.feature.devtools

import com.honghoaibao.bbooster.core.logging.LogEntry
import com.honghoaibao.bbooster.core.logging.LogLevel

internal data class LogHealthSummary(
    val totalEntries: Int = 0,
    val debugCount: Int = 0,
    val infoCount: Int = 0,
    val warningCount: Int = 0,
    val errorCount: Int = 0,
    val latestErrorEvent: String? = null
) {
    companion object {
        fun from(entries: List<LogEntry>): LogHealthSummary = LogHealthSummary(
            totalEntries = entries.size,
            debugCount = entries.count { it.level == LogLevel.DEBUG },
            infoCount = entries.count { it.level == LogLevel.INFO },
            warningCount = entries.count { it.level == LogLevel.WARN },
            errorCount = entries.count { it.level == LogLevel.ERROR },
            latestErrorEvent = entries
                .asSequence()
                .filter { it.level == LogLevel.ERROR }
                .maxByOrNull(LogEntry::timestampMillis)
                ?.event
        )
    }
}
