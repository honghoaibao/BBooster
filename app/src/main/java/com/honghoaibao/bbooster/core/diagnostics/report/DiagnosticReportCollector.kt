package com.honghoaibao.bbooster.core.diagnostics.report

import com.honghoaibao.bbooster.core.diagnostics.device.DeviceDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.DeviceDiagnosticsProvider
import com.honghoaibao.bbooster.core.diagnostics.shizuku.ShizukuDiagnosticsProvider
import com.honghoaibao.bbooster.core.diagnostics.transport.TransportDiagnosticsProvider
import com.honghoaibao.bbooster.core.logging.AppLog
import com.honghoaibao.bbooster.core.logging.LogModule
import com.honghoaibao.bbooster.core.logging.LogQuery
import com.honghoaibao.bbooster.core.logging.LogRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Collects a read-only, point-in-time report from existing providers.
 * It does not refresh Shizuku transports or execute commands.
 */
class DiagnosticReportCollector(
    private val deviceProvider: DeviceDiagnosticsProvider,
    private val shizukuProvider: ShizukuDiagnosticsProvider,
    private val transportProvider: TransportDiagnosticsProvider,
    private val appVersion: String,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val maxLogEntries: Int = DEFAULT_MAX_LOG_ENTRIES,
    private val deviceTelemetryHistory: List<DeviceDiagnostics> = emptyList()
) {

    init {
        require(maxLogEntries in 1..MAX_ALLOWED_LOG_ENTRIES) {
            "The diagnostic report log limit is outside the allowed range."
        }
    }

    suspend fun collect(): DiagnosticReport {
        val generatedAt = clockMillis().coerceAtLeast(0L)
        val device = capture { deviceProvider.collect() }
        val shizuku = capture { shizukuProvider.collect() }
        val transports = capture { transportProvider.snapshot() }
        val telemetryHistory = (deviceTelemetryHistory + listOfNotNull(device))
            .takeLast(MAX_DEVICE_TELEMETRY_SAMPLES)
        val logFlushCompleted = withContext(Dispatchers.IO) {
            AppLog.flush(LOG_FLUSH_TIMEOUT_MILLIS)
        }
        val logs = withContext(Dispatchers.IO) {
            AppLog.readLogs(LogQuery(limit = maxLogEntries))
                .asReversed()
                .map { entry ->
                    entry.copy(
                        event = LogRedactor.sanitizeEvent(entry.event),
                        message = LogRedactor.redact(entry.message)
                    )
                }
        }

        val issues = buildList {
            if (device == null) add("Device diagnostics could not be collected.")
            if (shizuku == null) add("Shizuku diagnostics could not be collected.")
            if (transports == null) add("Transport diagnostics could not be collected.")
            if (!logFlushCompleted) add("Some pending log writes may be missing from this report.")
        }

        AppLog.info(
            LogModule.REPORT,
            "diagnostic_report_collected",
            "device=" + (device != null) +
                "; shizuku=" + (shizuku != null) +
                "; transports=" + (transports != null) +
                "; logEntries=" + logs.size +
                "; telemetrySamples=" + telemetryHistory.size +
                "; issues=" + issues.size
        )

        return DiagnosticReport(
            generatedAtMillis = generatedAt,
            appVersion = LogRedactor.redact(appVersion),
            device = device,
            shizuku = shizuku,
            transports = transports,
            recentLogs = logs,
            collectionIssues = issues,
            deviceTelemetryHistory = telemetryHistory
        )
    }

    private suspend fun <T> capture(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    companion object {
        const val DEFAULT_MAX_LOG_ENTRIES = 60
        const val MAX_ALLOWED_LOG_ENTRIES = 200
        const val MAX_DEVICE_TELEMETRY_SAMPLES = 60
        private const val LOG_FLUSH_TIMEOUT_MILLIS = 2_500L
    }
}
