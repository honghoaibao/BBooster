package com.honghoaibao.bbooster.core.logging

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * A bounded, two-file log store. Both files share the same per-file size cap.
 * The previous file is replaced when the active file rotates.
 */
class FileLogStore(
    private val directory: File,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES
) : LogStore {

    init {
        require(maxFileBytes > 0L) { "Maximum log file size must be positive." }
    }

    private val lock = Any()
    private val activeFile = File(directory, ACTIVE_FILE_NAME)
    private val archiveFile = File(directory, ARCHIVE_FILE_NAME)

    override fun append(entry: LogEntry) = synchronized(lock) {
        ensureDirectory()

        val line = (LogEntryCodec.encode(entry) + "\n")
            .toByteArray(StandardCharsets.UTF_8)

        if (line.size.toLong() > maxFileBytes) {
            throw IOException("Encoded log entry exceeds the configured file limit.")
        }

        if (activeFile.exists() && activeFile.length() + line.size > maxFileBytes) {
            rotateFiles()
        }

        FileOutputStream(activeFile, true).use { output ->
            output.write(line)
            output.flush()
        }
    }

    override fun readAll(): List<LogEntry> = synchronized(lock) {
        val entries = ArrayList<LogEntry>()
        readFile(archiveFile, entries)
        readFile(activeFile, entries)
        entries.sortedBy(LogEntry::timestampMillis)
    }

    override fun clear() = synchronized(lock) {
        if (activeFile.exists() && !activeFile.delete()) {
            throw IOException("Could not delete the active log file.")
        }
        if (archiveFile.exists() && !archiveFile.delete()) {
            throw IOException("Could not delete the archived log file.")
        }
    }

    override fun sizeBytes(): Long = synchronized(lock) {
        (if (activeFile.isFile) activeFile.length() else 0L) +
            (if (archiveFile.isFile) archiveFile.length() else 0L)
    }

    private fun ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the log directory.")
        }
        if (!directory.isDirectory) {
            throw IOException("The log directory path is not a directory.")
        }
    }

    private fun rotateFiles() {
        if (archiveFile.exists() && !archiveFile.delete()) {
            throw IOException("Could not remove the previous log archive.")
        }

        if (activeFile.exists() && !activeFile.renameTo(archiveFile)) {
            activeFile.copyTo(archiveFile, overwrite = true)
            if (!activeFile.delete()) {
                throw IOException("Could not rotate the active log file.")
            }
        }
    }

    private fun readFile(file: File, destination: MutableList<LogEntry>) {
        if (!file.isFile) return

        runCatching {
            BufferedReader(
                InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)
            ).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    LogEntryCodec.decode(line)?.let(destination::add)
                }
            }
        }
    }

    private object LogEntryCodec {
        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()

        fun encode(entry: LogEntry): String = listOf(
            entry.timestampMillis.toString(),
            entry.level.name,
            entry.module.name,
            encodeText(entry.event),
            encodeText(entry.message)
        ).joinToString("\t")

        fun decode(line: String): LogEntry? = runCatching {
            val columns = line.split('\t', limit = 5)
            require(columns.size == 5)
            LogEntry(
                timestampMillis = columns[0].toLong(),
                level = LogLevel.valueOf(columns[1]),
                module = LogModule.valueOf(columns[2]),
                event = decodeText(columns[3]),
                message = decodeText(columns[4])
            )
        }.getOrNull()

        private fun encodeText(value: String): String =
            encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

        private fun decodeText(value: String): String =
            String(decoder.decode(value), StandardCharsets.UTF_8)
    }

    companion object {
        const val DEFAULT_MAX_FILE_BYTES = 512L * 1024L
        const val DEFAULT_TOTAL_MAX_BYTES = DEFAULT_MAX_FILE_BYTES * 2L

        private const val ACTIVE_FILE_NAME = "bbooster.log"
        private const val ARCHIVE_FILE_NAME = "bbooster.log.1"
    }
}
