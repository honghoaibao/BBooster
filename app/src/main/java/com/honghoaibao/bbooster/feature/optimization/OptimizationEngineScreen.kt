package com.honghoaibao.bbooster.feature.optimization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honghoaibao.bbooster.R
import com.honghoaibao.bbooster.core.diagnostics.device.DeviceDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.ThermalStatus
import kotlin.math.roundToInt
import com.honghoaibao.bbooster.core.optimization.OptimizationEngine
import com.honghoaibao.bbooster.core.optimization.OptimizationFinding
import com.honghoaibao.bbooster.core.optimization.OptimizationHistoryAnalyzer
import com.honghoaibao.bbooster.core.optimization.OptimizationTrend
import com.honghoaibao.bbooster.core.optimization.OptimizationTrendDirection
import com.honghoaibao.bbooster.core.optimization.OptimizationTrendMetric
import com.honghoaibao.bbooster.core.optimization.OptimizationPriority
import com.honghoaibao.bbooster.core.optimization.OptimizationRule
import com.honghoaibao.bbooster.ui.theme.Amber
import com.honghoaibao.bbooster.ui.theme.DarkSlate
import com.honghoaibao.bbooster.ui.theme.ElectricCyan
import com.honghoaibao.bbooster.ui.theme.Emerald
import com.honghoaibao.bbooster.ui.theme.MutedText
import com.honghoaibao.bbooster.ui.theme.Obsidian
import com.honghoaibao.bbooster.ui.theme.SlateBorder
import com.honghoaibao.bbooster.ui.theme.SoftWhite

@Composable
fun OptimizationEngineScreen(
    diagnostics: DeviceDiagnostics?,
    telemetryHistory: List<DeviceDiagnostics>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val findings = OptimizationEngine.analyze(diagnostics)
    val historyTrends = OptimizationHistoryAnalyzer.analyze(telemetryHistory)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.optimization_back), color = ElectricCyan)
            }
            Text(
                stringResource(R.string.optimization_screen_title),
                color = SoftWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            stringResource(R.string.optimization_screen_subtitle),
            color = MutedText,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSlate),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.optimization_read_only_note),
                    color = MutedText,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Button(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (isRefreshing) R.string.optimization_refreshing
                            else R.string.optimization_refresh
                        ),
                        color = Obsidian,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        when {
            diagnostics == null -> EmptyStateCard(
                title = stringResource(R.string.optimization_snapshot_missing_title),
                detail = stringResource(R.string.optimization_snapshot_missing_detail)
            )

            findings.isEmpty() -> EmptyStateCard(
                title = stringResource(R.string.optimization_no_findings_title),
                detail = stringResource(R.string.optimization_no_findings_detail)
            )

            else -> {
                Text(
                    stringResource(R.string.optimization_findings_heading, findings.size),
                    color = SoftWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                findings.forEach { finding ->
                    OptimizationFindingCard(finding)
                }
            }
        }

        OptimizationHistorySection(historyTrends)

        Text(
            stringResource(R.string.optimization_measurement_note),
            color = MutedText,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun EmptyStateCard(title: String, detail: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, color = SoftWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(detail, color = MutedText, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun OptimizationFindingCard(finding: OptimizationFinding) {
    val (title, detail, recommendation) = when (finding.rule) {
        OptimizationRule.THERMAL_HIGH -> Triple(
            R.string.optimization_thermal_high_title,
            R.string.optimization_thermal_high_detail,
            R.string.optimization_thermal_high_action
        )
        OptimizationRule.THERMAL_MODERATE -> Triple(
            R.string.optimization_thermal_moderate_title,
            R.string.optimization_thermal_moderate_detail,
            R.string.optimization_thermal_moderate_action
        )
        OptimizationRule.SYSTEM_LOW_MEMORY -> Triple(
            R.string.optimization_low_memory_title,
            R.string.optimization_low_memory_detail,
            R.string.optimization_low_memory_action
        )
        OptimizationRule.LOW_STORAGE -> Triple(
            R.string.optimization_low_storage_title,
            R.string.optimization_low_storage_detail,
            R.string.optimization_low_storage_action
        )
        OptimizationRule.LOW_BATTERY -> Triple(
            R.string.optimization_low_battery_title,
            R.string.optimization_low_battery_detail,
            R.string.optimization_low_battery_action
        )
    }
    val priorityText = when (finding.priority) {
        OptimizationPriority.CRITICAL -> R.string.optimization_priority_critical
        OptimizationPriority.WARNING -> R.string.optimization_priority_warning
        OptimizationPriority.NOTICE -> R.string.optimization_priority_notice
    }
    val priorityColor = when (finding.priority) {
        OptimizationPriority.CRITICAL -> Amber
        OptimizationPriority.WARNING -> Amber
        OptimizationPriority.NOTICE -> Emerald
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(priorityText),
                color = priorityColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                stringResource(title),
                color = SoftWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(detail),
                color = MutedText,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            finding.observedPercent?.let { percent ->
                Text(
                    stringResource(R.string.optimization_observed_percent, percent),
                    color = ElectricCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                stringResource(R.string.optimization_suggested_step),
                color = SoftWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(recommendation),
                color = MutedText,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}


@Composable
private fun OptimizationHistorySection(trends: List<OptimizationTrend>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.optimization_history_title),
            color = SoftWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.optimization_history_subtitle),
            color = MutedText,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        if (trends.isEmpty()) {
            EmptyStateCard(
                title = stringResource(R.string.optimization_history_insufficient_title),
                detail = stringResource(R.string.optimization_history_insufficient_detail)
            )
        } else {
            trends.forEach { trend ->
                OptimizationTrendCard(trend)
            }
        }

        Text(
            stringResource(R.string.optimization_history_note),
            color = MutedText,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun OptimizationTrendCard(trend: OptimizationTrend) {
    val metricLabel = when (trend.metric) {
        OptimizationTrendMetric.AVAILABLE_RAM ->
            R.string.optimization_history_metric_ram
        OptimizationTrendMetric.FREE_STORAGE ->
            R.string.optimization_history_metric_storage
        OptimizationTrendMetric.BATTERY_LEVEL ->
            R.string.optimization_history_metric_battery
        OptimizationTrendMetric.THERMAL_STATUS ->
            R.string.optimization_history_metric_thermal
    }

    val directionLabel = when (trend.metric) {
        OptimizationTrendMetric.THERMAL_STATUS -> when (trend.direction) {
            OptimizationTrendDirection.COOLING -> R.string.optimization_history_cooling
            OptimizationTrendDirection.WORSENING -> R.string.optimization_history_worsening
            else -> R.string.optimization_history_stable
        }
        else -> when (trend.direction) {
            OptimizationTrendDirection.IMPROVING -> R.string.optimization_history_improving
            OptimizationTrendDirection.DECLINING -> R.string.optimization_history_declining
            else -> R.string.optimization_history_stable
        }
    }

    val directionColor = when (trend.direction) {
        OptimizationTrendDirection.IMPROVING,
        OptimizationTrendDirection.COOLING -> Emerald
        OptimizationTrendDirection.DECLINING,
        OptimizationTrendDirection.WORSENING -> Amber
        OptimizationTrendDirection.STABLE -> MutedText
    }

    val rangeText = if (trend.metric == OptimizationTrendMetric.THERMAL_STATUS) {
        stringResource(
            R.string.optimization_history_thermal_range,
            stringResource(
                thermalStatusString(
                    OptimizationHistoryAnalyzer.thermalStatusForSeverity(trend.baselineValue)
                )
            ),
            stringResource(
                thermalStatusString(
                    OptimizationHistoryAnalyzer.thermalStatusForSeverity(trend.latestValue)
                )
            )
        )
    } else {
        stringResource(
            R.string.optimization_history_percent_range,
            trend.baselineValue.roundToInt().coerceIn(0, 100),
            trend.latestValue.roundToInt().coerceIn(0, 100)
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(metricLabel),
                color = SoftWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(directionLabel),
                color = directionColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                rangeText,
                color = ElectricCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.optimization_history_sample_count, trend.sampleCount),
                color = MutedText,
                fontSize = 11.sp
            )
        }
    }
}

private fun thermalStatusString(status: ThermalStatus): Int = when (status) {
    ThermalStatus.NOT_SUPPORTED -> R.string.device_thermal_not_supported
    ThermalStatus.UNKNOWN -> R.string.device_thermal_unknown
    ThermalStatus.NONE -> R.string.device_thermal_none
    ThermalStatus.LIGHT -> R.string.device_thermal_light
    ThermalStatus.MODERATE -> R.string.device_thermal_moderate
    ThermalStatus.SEVERE -> R.string.device_thermal_severe
    ThermalStatus.CRITICAL -> R.string.device_thermal_critical
    ThermalStatus.EMERGENCY -> R.string.device_thermal_emergency
    ThermalStatus.SHUTDOWN -> R.string.device_thermal_shutdown
}
