package com.honghoaibao.bbooster.core.logging

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileLogStoreTest {

    @Test
    fun appendsAndReadsEntries() {
        val directory = Files.createTempDirectory("bbooster-logs").toFile()
        val store = FileLogStore(directory)

        store.append(entry(100L, "first event"))
        store.append(entry(200L, "second event"))

        val entries = store.readAll()
        assertEquals(2, entries.size)
        assertEquals("first event", entries[0].message)
        assertEquals("second event", entries[1].message)
        assertTrue(store.sizeBytes() > 0L)
    }

    @Test
    fun rotatesFilesWithinConfiguredStorageLimit() {
        val directory = Files.createTempDirectory("bbooster-rotation").toFile()
        val perFileLimit = 220L
        val store = FileLogStore(directory, maxFileBytes = perFileLimit)

        repeat(12) { index ->
            store.append(entry(index.toLong(), "diagnostic-message-" + index + "-".repeat(28)))
        }

        assertTrue(store.sizeBytes() <= perFileLimit * 2L)
        assertTrue(store.readAll().size < 12)
    }

    @Test
    fun clearRemovesActiveAndArchivedLogs() {
        val directory = Files.createTempDirectory("bbooster-clear").toFile()
        val store = FileLogStore(directory, maxFileBytes = 100L)

        repeat(5) { index ->
            store.append(entry(index.toLong(), "message-" + index + "-".repeat(30)))
        }

        store.clear()

        assertEquals(0L, store.sizeBytes())
        assertTrue(store.readAll().isEmpty())
    }

    private fun entry(timestamp: Long, message: String) = LogEntry(
        timestampMillis = timestamp,
        level = LogLevel.INFO,
        module = LogModule.APP,
        event = "test_event",
        message = message
    )
}
