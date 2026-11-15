package com.honghoaibao.bbooster.core.diagnostics.report

import com.honghoaibao.bbooster.core.diagnostics.device.AndroidDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.DeviceDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.DeviceHardwareDiagnostics
import com.honghoaibao.bbooster.core.logging.LogEntry
import com.honghoaibao.bbooster.core.logging.LogLevel
import com.honghoaibao.bbooster.core.logging.LogModule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportFormatterTest {

    @Test
    fun jsonContainsStructuredSectionsAndOmitsUnavailableSnapshots() {
        val output = DiagnosticReportFormatter.toJson(sampleReport())

        assertTrue(output.startsWith("{"))
        assertTrue(output.contains("\"schema_version\":1"))
        assertTrue(output.contains("\"device\":{"))
        assertTrue(output.contains("\"shizuku\":null"))
        assertTrue(output.contains("\"transports\":null"))
        assertTrue(output.contains("\"recent_logs\":["))
        assertTrue(output.contains("\"command_stdout_included\":false"))
        assertTrue(output.endsWith("}"))
    }

    @Test
    fun jsonEscapesQuotesAndBackslashesInDeviceFields() {
        val output = DiagnosticReportFormatter.toJson(
            sampleReport(model = "Demo \"Unit\" \\ Device")
        )

        assertTrue(output.contains("Demo \\\"Unit\\\" \\\\ Device"))
    }

    @Test
    fun jsonAndTextRedactSecretsEmailsAndAndroidPathsFromLogs() {
        val report = sampleReport(
            logMessage = "access_token=abc123 user=person@example.com path=/data/user/0/app/files/private.db"
        )
        val json = DiagnosticReportFormatter.toJson(report)
        val text = DiagnosticReportFormatter.toText(report)

        listOf(json, text).forEach { output ->
            assertTrue(output.contains("[REDACTED]"))
            assertTrue(output.contains("[REDACTED_EMAIL]"))
            assertTrue(output.contains("[REDACTED_PATH]"))
            assertFalse(output.contains("abc123"))
            assertFalse(output.contains("person@example.com"))
            assertFalse(output.contains("/data/user/0"))
            assertFalse(output.contains("private.db"))
        }
    }

    @Test
    fun textReportIncludesDeviceAndPrivacySections() {
        val output = DiagnosticReportFormatter.toText(sampleReport())

        assertTrue(output.contains("[DEVICE]"))
        assertTrue(output.contains("[SHIZUKU]"))
        assertTrue(output.contains("[TRANSPORTS]"))
        assertTrue(output.contains("[RECENT LOGS]"))
        assertTrue(output.contains("[COLLECTION ISSUES]"))
        assertTrue(output.contains("Command stdout/stderr and raw shell output are not included."))
    }

    private fun sampleReport(
        model: String = "Test Device",
        logMessage: String = "diagnostic event completed"
    ) = DiagnosticReport(
        generatedAtMillis = 1_700_000_000_000L,
        appVersion = "0.3.5",
        device = DeviceDiagnostics(
            collectedAtMillis = 1_700_000_000_000L,
            android = AndroidDiagnostics(release = "15", sdkLevel = 35),
            device = DeviceHardwareDiagnostics(
                manufacturer = "Example",
                model = model,
                cpuAbis = listOf("arm64-v8a")
            ),
            memory = null,
            storage = null,
            battery = null,
            uptimeMillis = 60_000L
        ),
        shizuku = null,
        transports = null,
        recentLogs = listOf(
            LogEntry(
                timestampMillis = 1_700_000_000_000L,
                level = LogLevel.INFO,
                module = LogModule.REPORT,
                event = "report_test",
                message = logMessage
            )
        ),
        collectionIssues = emptyList()
    )
}
