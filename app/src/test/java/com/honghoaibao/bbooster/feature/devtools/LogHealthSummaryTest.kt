package com.honghoaibao.bbooster.feature.devtools

import com.honghoaibao.bbooster.core.logging.LogEntry
import com.honghoaibao.bbooster.core.logging.LogLevel
import com.honghoaibao.bbooster.core.logging.LogModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogHealthSummaryTest {

    @Test
    fun countsEntriesByLevelAndFindsTheNewestErrorEvent() {
        val summary = LogHealthSummary.from(
            listOf(
                entry(1L, LogLevel.DEBUG, "debug_event"),
                entry(2L, LogLevel.INFO, "info_event"),
                entry(3L, LogLevel.WARN, "warning_event"),
                entry(4L, LogLevel.ERROR, "old_error"),
                entry(5L, LogLevel.ERROR, "latest_error")
            )
        )

        assertEquals(5, summary.totalEntries)
        assertEquals(1, summary.debugCount)
        assertEquals(1, summary.infoCount)
        assertEquals(1, summary.warningCount)
        assertEquals(2, summary.errorCount)
        assertEquals("latest_error", summary.latestErrorEvent)
    }

    @Test
    fun emptyLogHistoryReturnsZeroCountsAndNoErrorEvent() {
        val summary = LogHealthSummary.from(emptyList())

        assertEquals(0, summary.totalEntries)
        assertEquals(0, summary.errorCount)
        assertNull(summary.latestErrorEvent)
    }

    private fun entry(
        timestamp: Long,
        level: LogLevel,
        event: String
    ) = LogEntry(
        timestampMillis = timestamp,
        level = level,
        module = LogModule.DEVICE,
        event = event,
        message = "test"
    )
}
