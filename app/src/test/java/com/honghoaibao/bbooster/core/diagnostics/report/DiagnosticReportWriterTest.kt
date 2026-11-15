package com.honghoaibao.bbooster.core.diagnostics.report

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportWriterTest {

    @Test
    fun writesTextAndJsonIntoThePrivateReportDirectory() = runBlocking {
        val directory = Files.createTempDirectory("bbooster-report").toFile()
        val writer = DiagnosticReportWriter(directory)
        val report = emptyReport()

        val textFile = writer.write(report, DiagnosticReportFormat.TEXT)
        val jsonFile = writer.write(
            report.copy(generatedAtMillis = report.generatedAtMillis + 1_000L),
            DiagnosticReportFormat.JSON
        )

        assertTrue(textFile.isFile)
        assertTrue(textFile.name.endsWith(".txt"))
        assertTrue(textFile.readText().contains("BBooster Diagnostic Report"))
        assertTrue(jsonFile.isFile)
        assertTrue(jsonFile.name.endsWith(".json"))
        assertTrue(jsonFile.readText().contains("\"schema_version\":1"))
        assertEquals(2, writer.countReports())
    }

    @Test
    fun prunesOlderReportsToConfiguredRetentionLimit() = runBlocking {
        val directory = Files.createTempDirectory("bbooster-report-retention").toFile()
        val writer = DiagnosticReportWriter(directory, maxRetainedReports = 2)

        repeat(3) { index ->
            writer.write(
                emptyReport().copy(generatedAtMillis = 1_700_000_000_000L + index * 60_000L),
                DiagnosticReportFormat.TEXT
            )
        }

        assertEquals(2, writer.pruneToRetention())
    }

    @Test
    fun clearAllReportsDeletesOnlyBBoosterDiagnosticFiles() = runBlocking {
        val directory = Files.createTempDirectory("bbooster-report-clear").toFile()
        val writer = DiagnosticReportWriter(directory)
        writer.write(emptyReport(), DiagnosticReportFormat.TEXT)
        val unrelated = directory.resolve("keep-this.txt")
        unrelated.writeText("unrelated")
        val temporary = directory.resolve("bbooster-diagnostic-interrupted.json.tmp")
        temporary.writeText("partial")

        assertTrue(writer.clearAllReports())
        assertEquals(0, writer.countReports())
        assertTrue(unrelated.isFile)
        assertFalse(temporary.exists())
    }

    private fun emptyReport() = DiagnosticReport(
        generatedAtMillis = 1_700_000_000_000L,
        appVersion = "test",
        device = null,
        shizuku = null,
        transports = null,
        recentLogs = emptyList(),
        collectionIssues = emptyList()
    )
}
