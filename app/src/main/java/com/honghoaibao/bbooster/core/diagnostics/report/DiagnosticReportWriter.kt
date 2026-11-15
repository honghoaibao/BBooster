package com.honghoaibao.bbooster.core.diagnostics.report

import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes reports only into the app-private cache directory.
 * Old reports created by this writer are pruned after each successful export.
 */
class DiagnosticReportWriter(
    private val directory: File,
    private val maxRetainedReports: Int = DEFAULT_RETAINED_REPORTS
) {

    init {
        require(maxRetainedReports > 0) { "At least one report must be retained." }
    }

    suspend fun write(
        report: DiagnosticReport,
        format: DiagnosticReportFormat
    ): File = withContext(Dispatchers.IO) {
        writeBlocking(report, format)
    }

    internal fun writeBlocking(
        report: DiagnosticReport,
        format: DiagnosticReportFormat
    ): File {
        ensureDirectory()
        val file = nextAvailableFile(report.generatedAtMillis, format)
        val temporaryFile = File(directory, file.name + TEMP_SUFFIX)

        try {
            temporaryFile.writeText(
                DiagnosticReportFormatter.format(report, format),
                Charsets.UTF_8
            )
            if (!temporaryFile.renameTo(file)) {
                temporaryFile.copyTo(file, overwrite = true)
                temporaryFile.delete()
            }
            pruneOldReports()
            return file
        } catch (error: Exception) {
            temporaryFile.delete()
            file.delete()
            throw IOException("Could not write the diagnostic report.", error)
        }
    }

    private fun ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the private report cache.")
        }
        if (!directory.isDirectory) {
            throw IOException("The report cache path is not a directory.")
        }
    }

    private fun nextAvailableFile(
        generatedAtMillis: Long,
        format: DiagnosticReportFormat
    ): File {
        val timestamp = runCatching {
            FILE_TIMESTAMP_FORMAT.withZone(ZoneId.of("UTC"))
                .format(Instant.ofEpochMilli(generatedAtMillis.coerceAtLeast(0L)))
        }.getOrDefault(generatedAtMillis.coerceAtLeast(0L).toString())
        val baseName = FILE_PREFIX + timestamp
        var candidate = File(directory, "$baseName.${format.extension}")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "$baseName-$suffix.${format.extension}")
            suffix++
        }
        return candidate
    }

    suspend fun countReports(): Int = withContext(Dispatchers.IO) {
        listReportFiles().size
    }

    suspend fun pruneToRetention(): Int = withContext(Dispatchers.IO) {
        pruneOldReports()
        listReportFiles().size
    }

    suspend fun clearAllReports(): Boolean = withContext(Dispatchers.IO) {
        val files = directory.listFiles()?.filter(::isReportOrTemporaryFile).orEmpty()
        files.all { !it.exists() || it.delete() }
    }

    private fun pruneOldReports() {
        listReportFiles()
            .sortedByDescending(File::lastModified)
            .drop(maxRetainedReports)
            .forEach { it.delete() }
    }

    private fun listReportFiles(): List<File> =
        directory.listFiles()
            ?.filter { file -> file.isFile && isReportFile(file) }
            .orEmpty()

    private fun isReportFile(file: File): Boolean =
        file.name.startsWith(FILE_PREFIX) &&
            (file.name.endsWith(".txt") || file.name.endsWith(".json"))

    private fun isReportOrTemporaryFile(file: File): Boolean =
        isReportFile(file) || file.name.startsWith(FILE_PREFIX) && file.name.endsWith(TEMP_SUFFIX)

    companion object {
        const val DEFAULT_RETAINED_REPORTS = 4
        private const val FILE_PREFIX = "bbooster-diagnostic-"
        private const val TEMP_SUFFIX = ".tmp"
        private val FILE_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
