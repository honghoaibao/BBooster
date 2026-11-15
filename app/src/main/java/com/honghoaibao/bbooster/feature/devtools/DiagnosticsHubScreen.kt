package com.honghoaibao.bbooster.feature.devtools

import android.os.Process
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honghoaibao.bbooster.BuildConfig
import com.honghoaibao.bbooster.R
import com.honghoaibao.bbooster.core.diagnostics.device.BatteryStatus
import com.honghoaibao.bbooster.core.diagnostics.device.DeviceDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.DeviceMonitoringController
import com.honghoaibao.bbooster.core.diagnostics.device.ThermalStatus
import com.honghoaibao.bbooster.core.diagnostics.report.DiagnosticReportFormat
import com.honghoaibao.bbooster.core.logging.AppLog
import com.honghoaibao.bbooster.core.logging.LogQuery
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionState
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionStatus
import com.honghoaibao.bbooster.core.shizuku.transport.TransportAvailability
import com.honghoaibao.bbooster.core.shizuku.transport.TransportManagerState
import com.honghoaibao.bbooster.ui.theme.Amber
import com.honghoaibao.bbooster.ui.theme.DarkSlate
import com.honghoaibao.bbooster.ui.theme.ElectricCyan
import com.honghoaibao.bbooster.ui.theme.Emerald
import com.honghoaibao.bbooster.ui.theme.MutedText
import com.honghoaibao.bbooster.ui.theme.Obsidian
import com.honghoaibao.bbooster.ui.theme.SlateBorder
import com.honghoaibao.bbooster.ui.theme.SoftWhite
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_LOG_SUMMARY_ENTRIES = 500

@Composable
fun DiagnosticsHubScreen(
    diagnostics: DeviceDiagnostics?,
    telemetryHistory: List<DeviceDiagnostics>,
    isMonitoring: Boolean,
    isRefreshingDeviceDiagnostics: Boolean,
    shizukuState: ShizukuConnectionState,
    transportState: TransportManagerState,
    isPreparingReport: Boolean,
    onRefreshDeviceDiagnostics: () -> Unit,
    onOpenLogViewer: () -> Unit,
    onShareDiagnosticReport: (DiagnosticReportFormat) -> Unit,
    onBack: () -> Unit
) {
    val logRevision by AppLog.revision.collectAsState()
    var refreshCounter by remember { mutableIntStateOf(0) }
    var logSummary by remember { mutableStateOf(LogHealthSummary()) }
    var logStorageBytes by remember { mutableLongStateOf(0L) }
    var runtimeRefreshCounter by remember { mutableIntStateOf(0) }
    val runtimeSnapshot = remember(runtimeRefreshCounter) { readRuntimeSnapshot() }

    LaunchedEffect(logRevision, refreshCounter) {
        val result = withContext(Dispatchers.IO) {
            val entries = AppLog.readLogs(LogQuery(limit = MAX_LOG_SUMMARY_ENTRIES))
            LogHealthSummary.from(entries) to AppLog.logSizeBytes()
        }
        logSummary = result.first
        logStorageBytes = result.second
    }

    val validCpuSamples = telemetryHistory.mapNotNull { sample ->
        sample.processCpuUsagePercent?.takeIf { it.isFinite() }
    }
    val averageCpu = validCpuSamples.average().takeIf { validCpuSamples.isNotEmpty() && it.isFinite() }
    val peakCpu = validCpuSamples.maxOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    stringResource(R.string.inspector_title),
                    color = SoftWhite,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.inspector_subtitle),
                    color = MutedText,
                    fontSize = 11.sp
                )
            }
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.log_back), color = SoftWhite)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    refreshCounter++
                    runtimeRefreshCounter++
                    onRefreshDeviceDiagnostics()
                },
                enabled = !isRefreshingDeviceDiagnostics,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = Obsidian,
                    disabledContainerColor = SlateBorder,
                    disabledContentColor = MutedText
                )
            ) {
                Text(
                    stringResource(
                        if (isRefreshingDeviceDiagnostics) R.string.device_refreshing
                        else R.string.inspector_refresh
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
            TextButton(
                onClick = onOpenLogViewer,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(R.string.inspector_open_logs),
                    color = ElectricCyan,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }

        InspectorSection(
            title = stringResource(R.string.inspector_app_section),
            subtitle = stringResource(R.string.inspector_app_note)
        ) {
            InspectorValue(stringResource(R.string.inspector_app_id), BuildConfig.APPLICATION_ID)
            InspectorValue(stringResource(R.string.inspector_app_version), BuildConfig.VERSION_NAME)
            InspectorValue(stringResource(R.string.inspector_process_id), runtimeSnapshot.processId.toString())
            InspectorValue(
                stringResource(R.string.inspector_heap_used),
                formatBytes(runtimeSnapshot.heapUsedBytes)
            )
            InspectorValue(
                stringResource(R.string.inspector_heap_max),
                formatBytes(runtimeSnapshot.heapMaxBytes)
            )
            Text(
                stringResource(R.string.inspector_heap_note),
                color = MutedText,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }

        InspectorSection(
            title = stringResource(R.string.inspector_device_section),
            subtitle = stringResource(R.string.inspector_device_note)
        ) {
            if (diagnostics == null) {
                Text(
                    stringResource(R.string.device_unavailable),
                    color = MutedText,
                    fontSize = 12.sp
                )
            } else {
                InspectorValue(
                    stringResource(R.string.inspector_device_model),
                    "${diagnostics.device.manufacturer} ${diagnostics.device.model}".trim()
                )
                InspectorValue(
                    stringResource(R.string.device_android),
                    stringResource(
                        R.string.device_android_value,
                        diagnostics.android.release,
                        diagnostics.android.sdkLevel
                    )
                )
                InspectorValue(
                    stringResource(R.string.device_cpu_abi),
                    diagnostics.device.cpuAbis.joinToString().ifBlank {
                        stringResource(R.string.device_value_unavailable)
                    }
                )
                InspectorValue(
                    stringResource(R.string.device_ram),
                    diagnostics.memory?.let {
                        stringResource(
                            R.string.device_available_of_total,
                            formatBytes(it.availableBytes),
                            formatBytes(it.totalBytes)
                        )
                    } ?: stringResource(R.string.device_value_unavailable)
                )
                InspectorValue(
                    stringResource(R.string.device_storage),
                    diagnostics.storage?.let {
                        stringResource(
                            R.string.device_available_of_total,
                            formatBytes(it.availableBytes),
                            formatBytes(it.totalBytes)
                        )
                    } ?: stringResource(R.string.device_value_unavailable)
                )
                InspectorValue(
                    stringResource(R.string.device_battery),
                    diagnostics.battery?.let { battery ->
                        val level = battery.levelPercent?.let { "$it%" }
                            ?: stringResource(R.string.device_value_unavailable)
                        "$level · ${battery.status.localizedLabel()}"
                    } ?: stringResource(R.string.device_value_unavailable)
                )
                InspectorValue(
                    stringResource(R.string.device_cpu_usage),
                    diagnostics.processCpuUsagePercent?.let {
                        stringResource(R.string.device_cpu_usage_value, it)
                    } ?: stringResource(R.string.device_value_unavailable)
                )
                InspectorValue(
                    stringResource(R.string.device_thermal_status),
                    diagnostics.thermal.status.localizedLabel()
                )
            }
        }

        InspectorSection(
            title = stringResource(R.string.inspector_connection_section),
            subtitle = stringResource(R.string.inspector_connection_note)
        ) {
            InspectorValue(
                stringResource(R.string.inspector_shizuku_status),
                shizukuState.status.localizedLabel(),
                if (shizukuState.status == ShizukuConnectionStatus.READY) Emerald else Amber
            )
            InspectorValue(
                stringResource(R.string.inspector_binder),
                yesNo(shizukuState.binderAvailable),
                if (shizukuState.binderAvailable) Emerald else MutedText
            )
            InspectorValue(
                stringResource(R.string.inspector_permission),
                shizukuState.permissionGranted?.let { yesNo(it) }
                    ?: stringResource(R.string.inspector_unknown)
            )
            InspectorValue(
                stringResource(R.string.inspector_server_api),
                shizukuState.serverVersion?.toString()
                    ?: stringResource(R.string.inspector_unknown)
            )
            InspectorValue(
                stringResource(R.string.inspector_server_uid),
                shizukuState.serverUid?.toString()
                    ?: stringResource(R.string.inspector_unknown)
            )
            InspectorDivider()
            InspectorValue(
                stringResource(R.string.inspector_user_service),
                transportState.userService.state.localizedLabel(),
                transportState.userService.state.statusColor()
            )
            InspectorValue(
                stringResource(R.string.inspector_direct_process),
                transportState.directProcess.state.localizedLabel(),
                transportState.directProcess.state.statusColor()
            )
            InspectorValue(
                stringResource(R.string.inspector_command_state),
                when {
                    transportState.isExecuting -> stringResource(R.string.inspector_command_running)
                    transportState.isRefreshing -> stringResource(R.string.inspector_transport_refreshing)
                    else -> stringResource(R.string.inspector_idle)
                }
            )
            val lastCommand = transportState.lastResult
            InspectorValue(
                stringResource(R.string.inspector_last_command),
                lastCommand?.let {
                    "${it.commandId} · ${it.status.name} · ${it.dispatchState.name}"
                } ?: stringResource(R.string.inspector_unknown)
            )
        }

        InspectorSection(
            title = stringResource(R.string.inspector_monitor_section),
            subtitle = stringResource(R.string.inspector_monitor_note)
        ) {
            InspectorValue(
                stringResource(R.string.inspector_monitor_status),
                stringResource(
                    if (isMonitoring) R.string.device_monitoring_active
                    else R.string.device_monitoring_paused,
                    telemetryHistory.size,
                    DeviceMonitoringController.MAX_SAMPLES
                ),
                if (isMonitoring) Emerald else MutedText
            )
            InspectorValue(
                stringResource(R.string.inspector_sample_count),
                telemetryHistory.size.toString()
            )
            InspectorValue(
                stringResource(R.string.inspector_valid_cpu_samples),
                validCpuSamples.size.toString()
            )
            InspectorValue(
                stringResource(R.string.inspector_cpu_average),
                averageCpu?.let { stringResource(R.string.device_cpu_usage_value, it) }
                    ?: stringResource(R.string.device_value_unavailable)
            )
            InspectorValue(
                stringResource(R.string.inspector_cpu_peak),
                peakCpu?.let { stringResource(R.string.device_cpu_usage_value, it) }
                    ?: stringResource(R.string.device_value_unavailable)
            )
        }

        InspectorSection(
            title = stringResource(R.string.inspector_logs_section),
            subtitle = stringResource(R.string.inspector_logs_note, MAX_LOG_SUMMARY_ENTRIES)
        ) {
            InspectorValue(stringResource(R.string.inspector_logs_total), logSummary.totalEntries.toString())
            InspectorValue(stringResource(R.string.log_level_debug), logSummary.debugCount.toString())
            InspectorValue(stringResource(R.string.log_level_info), logSummary.infoCount.toString())
            InspectorValue(stringResource(R.string.log_level_warn), logSummary.warningCount.toString())
            InspectorValue(stringResource(R.string.log_level_error), logSummary.errorCount.toString())
            InspectorValue(
                stringResource(R.string.inspector_log_storage),
                formatBytes(logStorageBytes)
            )
            InspectorValue(
                stringResource(R.string.inspector_latest_error),
                logSummary.latestErrorEvent ?: stringResource(R.string.inspector_no_errors),
                if (logSummary.latestErrorEvent == null) Emerald else Amber
            )
        }

        InspectorSection(
            title = stringResource(R.string.inspector_report_section),
            subtitle = stringResource(R.string.inspector_report_note)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onShareDiagnosticReport(DiagnosticReportFormat.TEXT) },
                    enabled = !isPreparingReport,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Obsidian,
                        disabledContainerColor = SlateBorder,
                        disabledContentColor = MutedText
                    )
                ) {
                    Text(stringResource(R.string.report_export_txt), fontSize = 11.sp)
                }
                Button(
                    onClick = { onShareDiagnosticReport(DiagnosticReportFormat.JSON) },
                    enabled = !isPreparingReport,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF263747),
                        contentColor = SoftWhite,
                        disabledContainerColor = SlateBorder,
                        disabledContentColor = MutedText
                    )
                ) {
                    Text(stringResource(R.string.report_export_json), fontSize = 11.sp)
                }
            }
            if (isPreparingReport) {
                Text(
                    stringResource(R.string.report_generating),
                    color = MutedText,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private data class RuntimeSnapshot(
    val processId: Int,
    val heapUsedBytes: Long,
    val heapMaxBytes: Long
)

private fun readRuntimeSnapshot(): RuntimeSnapshot {
    val runtime = Runtime.getRuntime()
    val usedBytes = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
    return RuntimeSnapshot(
        processId = Process.myPid(),
        heapUsedBytes = usedBytes,
        heapMaxBytes = runtime.maxMemory().coerceAtLeast(0L)
    )
}

@Composable
private fun InspectorSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                Text(
                    title,
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    color = MutedText,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                content()
            }
        )
    }
}

@Composable
private fun InspectorValue(
    label: String,
    value: String,
    valueColor: Color = SoftWhite
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = MutedText,
            fontSize = 10.sp,
            modifier = Modifier.weight(0.9f)
        )
        Text(
            value,
            color = valueColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
private fun InspectorDivider() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        color = SlateBorder,
        content = {}
    )
}

@Composable
private fun ShizukuConnectionStatus.localizedLabel(): String = when (this) {
    ShizukuConnectionStatus.CHECKING -> stringResource(R.string.shizuku_checking_title)
    ShizukuConnectionStatus.SERVICE_NOT_RUNNING -> stringResource(R.string.shizuku_off_title)
    ShizukuConnectionStatus.PERMISSION_REQUIRED -> stringResource(R.string.shizuku_permission_title)
    ShizukuConnectionStatus.REQUESTING_PERMISSION -> stringResource(R.string.shizuku_requesting_title)
    ShizukuConnectionStatus.READY -> stringResource(R.string.shizuku_ready_title)
    ShizukuConnectionStatus.UNSUPPORTED_API -> stringResource(R.string.shizuku_unsupported_title)
    ShizukuConnectionStatus.ERROR -> stringResource(R.string.shizuku_error_title)
}

@Composable
private fun TransportAvailability.localizedLabel(): String = when (this) {
    TransportAvailability.CHECKING -> stringResource(R.string.transport_checking)
    TransportAvailability.READY,
    TransportAvailability.AVAILABLE -> stringResource(R.string.transport_ready)
    TransportAvailability.STARTING -> stringResource(R.string.transport_starting)
    TransportAvailability.DEGRADED -> stringResource(R.string.transport_degraded)
    TransportAvailability.UNAVAILABLE -> stringResource(R.string.transport_unavailable)
    TransportAvailability.UNSUPPORTED -> stringResource(R.string.transport_unsupported)
}

private fun TransportAvailability.statusColor(): Color = when (this) {
    TransportAvailability.READY,
    TransportAvailability.AVAILABLE -> Emerald
    TransportAvailability.STARTING,
    TransportAvailability.CHECKING,
    TransportAvailability.DEGRADED -> Amber
    TransportAvailability.UNAVAILABLE,
    TransportAvailability.UNSUPPORTED -> Color(0xFFFF858C)
}

@Composable
private fun ThermalStatus.localizedLabel(): String = when (this) {
    ThermalStatus.NOT_SUPPORTED -> stringResource(R.string.device_thermal_not_supported)
    ThermalStatus.UNKNOWN -> stringResource(R.string.device_thermal_unknown)
    ThermalStatus.NONE -> stringResource(R.string.device_thermal_none)
    ThermalStatus.LIGHT -> stringResource(R.string.device_thermal_light)
    ThermalStatus.MODERATE -> stringResource(R.string.device_thermal_moderate)
    ThermalStatus.SEVERE -> stringResource(R.string.device_thermal_severe)
    ThermalStatus.CRITICAL -> stringResource(R.string.device_thermal_critical)
    ThermalStatus.EMERGENCY -> stringResource(R.string.device_thermal_emergency)
    ThermalStatus.SHUTDOWN -> stringResource(R.string.device_thermal_shutdown)
}

@Composable
private fun BatteryStatus.localizedLabel(): String = when (this) {
    BatteryStatus.CHARGING -> stringResource(R.string.device_battery_charging)
    BatteryStatus.DISCHARGING -> stringResource(R.string.device_battery_discharging)
    BatteryStatus.FULL -> stringResource(R.string.device_battery_full)
    BatteryStatus.NOT_CHARGING -> stringResource(R.string.device_battery_not_charging)
    BatteryStatus.UNKNOWN -> stringResource(R.string.device_battery_unknown)
}

@Composable
private fun yesNo(value: Boolean): String =
    stringResource(if (value) R.string.inspector_yes else R.string.inspector_no)

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "${bytes.coerceAtLeast(0L)} B"
    bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
}
