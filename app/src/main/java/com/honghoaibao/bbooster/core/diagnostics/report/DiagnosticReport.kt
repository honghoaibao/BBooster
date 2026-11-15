package com.honghoaibao.bbooster.core.diagnostics.report

import com.honghoaibao.bbooster.core.diagnostics.device.DeviceDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.shizuku.ShizukuDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.transport.TransportDiagnosticsSnapshot
import com.honghoaibao.bbooster.core.logging.LogEntry

enum class DiagnosticReportFormat(val extension: String, val mimeType: String) {
    TEXT("txt", "text/plain"),
    JSON("json", "application/json")
}

data class DiagnosticReport(
    val generatedAtMillis: Long,
    val appVersion: String,
    val device: DeviceDiagnostics?,
    val shizuku: ShizukuDiagnostics?,
    val transports: TransportDiagnosticsSnapshot?,
    val recentLogs: List<LogEntry>,
    val collectionIssues: List<String>
)
