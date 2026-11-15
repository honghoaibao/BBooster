package com.honghoaibao.bbooster.core.diagnostics.report

import com.honghoaibao.bbooster.core.diagnostics.device.BatteryDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.DeviceDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.MemoryDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.StorageDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.shizuku.ShizukuDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.shizuku.ShizukuTransportDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.transport.TransportDiagnosticsSnapshot
import com.honghoaibao.bbooster.core.diagnostics.transport.TransportStatusSnapshot
import com.honghoaibao.bbooster.core.logging.LogEntry
import com.honghoaibao.bbooster.core.logging.LogRedactor
import java.time.Instant

/** Stable, dependency-free serializers for diagnostic report sharing. */
object DiagnosticReportFormatter {

    private val androidFilePath = Regex(
        """(?i)(?:file://)?(?:/data/|/storage/|/sdcard/|/mnt/|/system/|/vendor/|/proc/|/dev/)[^\s,;'"<>]*"""
    )

    fun format(report: DiagnosticReport, format: DiagnosticReportFormat): String =
        when (format) {
            DiagnosticReportFormat.TEXT -> toText(report)
            DiagnosticReportFormat.JSON -> toJson(report)
        }

    fun toJson(report: DiagnosticReport): String = jsonObject(
        "schema_version" to "1",
        "generated_at" to jsonString(timestamp(report.generatedAtMillis)),
        "app" to jsonObject(
            "name" to jsonString("BBooster"),
            "version" to jsonString(safe(report.appVersion))
        ),
        "device" to report.device?.let(::deviceJson).orNullJson(),
        "shizuku" to report.shizuku?.let(::shizukuJson).orNullJson(),
        "transports" to report.transports?.let(::transportsJson).orNullJson(),
        "recent_logs" to jsonArray(report.recentLogs.map(::logJson)),
        "collection_issues" to jsonArray(report.collectionIssues.map { jsonString(safe(it)) }),
        "privacy" to jsonObject(
            "command_stdout_included" to "false",
            "command_stderr_included" to "false",
            "raw_shell_commands_included" to "false",
            "file_paths_included" to "false",
            "sensitive_log_values_redacted" to "true"
        )
    )

    fun toText(report: DiagnosticReport): String = buildString {
        appendLine("BBooster Diagnostic Report")
        appendLine("==========================")
        appendLine("Generated: ${timestamp(report.generatedAtMillis)}")
        appendLine("App version: ${safe(report.appVersion)}")
        appendLine()

        appendLine("[DEVICE]")
        val device = report.device
        if (device == null) {
            appendLine("Not collected")
        } else {
            appendLine("Android: ${safe(device.android.release)} (SDK ${device.android.sdkLevel})")
            appendLine("Manufacturer: ${safe(device.device.manufacturer)}")
            appendLine("Model: ${safe(device.device.model)}")
            appendLine("CPU ABIs: ${device.device.cpuAbis.map(::safe).joinToString().ifBlank { "Unavailable" }}")
            appendLine("Uptime: ${device.uptimeMillis} ms")
            appendMemory(device.memory)
            appendStorage(device.storage)
            appendBattery(device.battery)
        }
        appendLine()

        appendLine("[SHIZUKU]")
        val shizuku = report.shizuku
        if (shizuku == null) {
            appendLine("Not collected")
        } else {
            appendLine("Connection: ${shizuku.connectionStatus.name}")
            appendLine("Binder available: ${shizuku.binderAvailable}")
            appendLine("Permission granted: ${shizuku.permissionGranted?.toString() ?: "Unknown"}")
            appendLine("Server API: ${shizuku.serverApiVersion?.toString() ?: "Unavailable"}")
            appendLine("Server UID: ${shizuku.serverUid?.toString() ?: "Unavailable"}")
            appendLine("Privilege: ${shizuku.privilege?.name ?: "Unknown"}")
            appendLine("Preferred transport: ${shizuku.preferredTransport.name}")
            appendLine("Last command transport: ${shizuku.lastCommandTransport?.name ?: "None"}")
            appendLine("UserService: ${transportSummary(shizuku.userService)}")
            appendLine("Direct Process: ${transportSummary(shizuku.directProcess)}")
            shizuku.lastCommand?.let {
                appendLine(
                    "Last command: ${safe(it.commandId)} · ${it.status.name} · " +
                        "dispatch=${it.dispatchState.name} · exit=${it.exitCode ?: "n/a"}"
                )
            } ?: appendLine("Last command: None")
            shizuku.lastError?.let {
                appendLine("Last error: ${it.source.name}/${safe(it.code)} · ${safe(it.message)}")
            }
        }
        appendLine()

        appendLine("[TRANSPORTS]")
        val transports = report.transports
        if (transports == null) {
            appendLine("Not collected")
        } else {
            appendLine("Preferred transport: ${transports.preferredTransport.name}")
            appendLine("Last command transport: ${transports.lastCommandTransport?.name ?: "None"}")
            appendLine("UserService: ${transportSummary(transports.userService)}")
            appendLine("Direct Process: ${transportSummary(transports.directProcess)}")
            appendLine("Last UserService check: ${transports.lastUserServiceCheckAtMillis?.let(::timestamp) ?: "Unknown"}")
            appendLine("Last Direct Process check: ${transports.lastDirectProcessCheckAtMillis?.let(::timestamp) ?: "Unknown"}")
            appendLine("Last command time: ${transports.lastCommandAtMillis?.let(::timestamp) ?: "Unknown"}")
        }
        appendLine()

        appendLine("[RECENT LOGS]")
        if (report.recentLogs.isEmpty()) {
            appendLine("No log entries")
        } else {
            report.recentLogs.forEach { entry ->
                appendLine(
                    "${timestamp(entry.timestampMillis)} " +
                        "[${entry.level.name}] [${entry.module.name}] " +
                        "${safe(entry.event)}: ${safe(entry.message)}"
                )
            }
        }
        appendLine()

        appendLine("[COLLECTION ISSUES]")
        if (report.collectionIssues.isEmpty()) {
            appendLine("None")
        } else {
            report.collectionIssues.forEach { appendLine("- ${safe(it)}") }
        }
        appendLine()

        appendLine("[PRIVACY]")
        appendLine("Command stdout/stderr and raw shell output are not included.")
        appendLine("File-system paths are not included.")
        appendLine("Sensitive values in log and diagnostic text are redacted.")
    }

    private fun deviceJson(device: DeviceDiagnostics): String = jsonObject(
        "collected_at" to jsonString(timestamp(device.collectedAtMillis)),
        "android" to jsonObject(
            "release" to jsonString(safe(device.android.release)),
            "sdk_level" to device.android.sdkLevel.toString()
        ),
        "hardware" to jsonObject(
            "manufacturer" to jsonString(safe(device.device.manufacturer)),
            "model" to jsonString(safe(device.device.model)),
            "cpu_abis" to jsonArray(device.device.cpuAbis.map { jsonString(safe(it)) })
        ),
        "memory" to device.memory?.let(::memoryJson).orNullJson(),
        "storage" to device.storage?.let(::storageJson).orNullJson(),
        "battery" to device.battery?.let(::batteryJson).orNullJson(),
        "uptime_millis" to device.uptimeMillis.toString()
    )

    private fun memoryJson(memory: MemoryDiagnostics): String = jsonObject(
        "total_bytes" to memory.totalBytes.toString(),
        "available_bytes" to memory.availableBytes.toString(),
        "threshold_bytes" to memory.thresholdBytes.toString(),
        "low_memory" to memory.isLowMemory.toString()
    )

    private fun storageJson(storage: StorageDiagnostics): String = jsonObject(
        "total_bytes" to storage.totalBytes.toString(),
        "available_bytes" to storage.availableBytes.toString(),
        "used_bytes" to storage.usedBytes.toString()
    )

    private fun batteryJson(battery: BatteryDiagnostics): String = jsonObject(
        "level_percent" to battery.levelPercent?.toString().orNullJson(),
        "status" to jsonString(battery.status.name),
        "plugged" to jsonString(battery.plugged.name),
        "temperature_celsius" to battery.temperatureCelsius?.takeIf { it.isFinite() }
            ?.let(::jsonDouble).orNullJson(),
        "voltage_millivolts" to battery.voltageMillivolts?.toString().orNullJson()
    )

    private fun shizukuJson(value: ShizukuDiagnostics): String = jsonObject(
        "collected_at" to jsonString(timestamp(value.collectedAtMillis)),
        "connection_status" to jsonString(value.connectionStatus.name),
        "binder_available" to value.binderAvailable.toString(),
        "permission_granted" to value.permissionGranted?.toString().orNullJson(),
        "server_api_version" to value.serverApiVersion?.toString().orNullJson(),
        "server_uid" to value.serverUid?.toString().orNullJson(),
        "privilege" to value.privilege?.name?.let(::jsonString).orNullJson(),
        "connected_at" to value.connectedAtMillis?.let(::timestamp)?.let(::jsonString).orNullJson(),
        "connection_duration_millis" to value.connectionDurationMillis?.toString().orNullJson(),
        "last_connection_check_at" to value.lastConnectionCheckAtMillis?.let(::timestamp)?.let(::jsonString).orNullJson(),
        "last_transport_check_at" to value.lastTransportCheckAtMillis?.let(::timestamp)?.let(::jsonString).orNullJson(),
        "preferred_transport" to jsonString(value.preferredTransport.name),
        "last_command_transport" to value.lastCommandTransport?.name?.let(::jsonString).orNullJson(),
        "is_transport_refreshing" to value.isTransportRefreshing.toString(),
        "is_command_executing" to value.isCommandExecuting.toString(),
        "user_service" to shizukuTransportJson(value.userService),
        "direct_process" to shizukuTransportJson(value.directProcess),
        "last_command" to value.lastCommand?.let {
            jsonObject(
                "command_id" to jsonString(safe(it.commandId)),
                "transport" to it.transport?.name?.let(::jsonString).orNullJson(),
                "status" to jsonString(it.status.name),
                "dispatch_state" to jsonString(it.dispatchState.name),
                "exit_code" to it.exitCode?.toString().orNullJson(),
                "duration_millis" to it.durationMillis.toString(),
                "failure_code" to it.failureCode?.name?.let(::jsonString).orNullJson(),
                "recorded_at" to it.recordedAtMillis?.let(::timestamp)?.let(::jsonString).orNullJson()
            )
        }.orNullJson(),
        "last_error" to value.lastError?.let {
            jsonObject(
                "source" to jsonString(it.source.name),
                "code" to jsonString(safe(it.code)),
                "message" to jsonString(safe(it.message)),
                "occurred_at" to it.occurredAtMillis?.let(::timestamp)?.let(::jsonString).orNullJson()
            )
        }.orNullJson()
    )

    private fun shizukuTransportJson(value: ShizukuTransportDiagnostics): String = jsonObject(
        "transport" to jsonString(value.transport.name),
        "availability" to jsonString(value.availability.name),
        "capabilities" to jsonArray(value.capabilities.map { jsonString(it.name) }.sorted()),
        "detail" to value.detail?.let(::safe)?.let(::jsonString).orNullJson()
    )

    private fun transportsJson(value: TransportDiagnosticsSnapshot): String = jsonObject(
        "collected_at" to jsonString(timestamp(value.collectedAtMillis)),
        "preferred_transport" to jsonString(value.preferredTransport.name),
        "last_command_transport" to value.lastCommandTransport?.name?.let(::jsonString).orNullJson(),
        "is_refreshing" to value.isRefreshing.toString(),
        "is_executing" to value.isExecuting.toString(),
        "user_service" to transportStatusJson(value.userService),
        "direct_process" to transportStatusJson(value.directProcess),
        "last_user_service_check_at" to value.lastUserServiceCheckAtMillis?.let(::timestamp)?.let(::jsonString).orNullJson(),
        "last_direct_process_check_at" to value.lastDirectProcessCheckAtMillis?.let(::timestamp)?.let(::jsonString).orNullJson(),
        "last_command_at" to value.lastCommandAtMillis?.let(::timestamp)?.let(::jsonString).orNullJson()
    )

    private fun transportStatusJson(value: TransportStatusSnapshot): String = jsonObject(
        "transport" to jsonString(value.transport.name),
        "availability" to jsonString(value.availability.name),
        "capabilities" to jsonArray(value.capabilities.map { jsonString(it.name) }.sorted()),
        "detail" to value.detail?.let(::safe)?.let(::jsonString).orNullJson()
    )

    private fun logJson(entry: LogEntry): String = jsonObject(
        "timestamp" to jsonString(timestamp(entry.timestampMillis)),
        "level" to jsonString(entry.level.name),
        "module" to jsonString(entry.module.name),
        "event" to jsonString(LogRedactor.sanitizeEvent(entry.event)),
        "message" to jsonString(safe(entry.message))
    )

    private fun String?.orNullJson(): String = this ?: "null"

    private fun jsonObject(vararg fields: Pair<String, String>): String =
        fields.joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
            jsonString(key) + ":" + value
        }

    private fun jsonArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]", separator = ",")

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun jsonDouble(value: Double): String =
        if (value.isFinite()) value.toString() else "null"

    private fun safe(value: String): String =
        androidFilePath.replace(LogRedactor.redact(value), "[REDACTED_PATH]")

    private fun timestamp(value: Long): String = runCatching {
        Instant.ofEpochMilli(value.coerceAtLeast(0L)).toString()
    }.getOrDefault(value.toString())

    private fun transportSummary(value: ShizukuTransportDiagnostics): String =
        "${value.availability.name}" + (value.detail?.let { " · ${safe(it)}" } ?: "")

    private fun transportSummary(value: TransportStatusSnapshot): String =
        "${value.availability.name}" + (value.detail?.let { " · ${safe(it)}" } ?: "")

    private fun StringBuilder.appendMemory(memory: MemoryDiagnostics?) {
        if (memory == null) {
            appendLine("Memory: Unavailable")
        } else {
            appendLine(
                "Memory: total=${memory.totalBytes} bytes; " +
                    "available=${memory.availableBytes} bytes; low=${memory.isLowMemory}"
            )
        }
    }

    private fun StringBuilder.appendStorage(storage: StorageDiagnostics?) {
        if (storage == null) {
            appendLine("Storage: Unavailable")
        } else {
            appendLine(
                "Storage: total=${storage.totalBytes} bytes; " +
                    "available=${storage.availableBytes} bytes; used=${storage.usedBytes} bytes"
            )
        }
    }

    private fun StringBuilder.appendBattery(battery: BatteryDiagnostics?) {
        if (battery == null) {
            appendLine("Battery: Unavailable")
        } else {
            appendLine(
                "Battery: level=${battery.levelPercent?.let { "$it%" } ?: "Unknown"}; " +
                    "status=${battery.status.name}; plugged=${battery.plugged.name}; " +
                    "temperatureC=${battery.temperatureCelsius ?: "Unknown"}; " +
                    "voltageMv=${battery.voltageMillivolts ?: "Unknown"}"
            )
        }
    }
}
