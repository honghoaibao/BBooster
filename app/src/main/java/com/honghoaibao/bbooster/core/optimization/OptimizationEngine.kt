package com.honghoaibao.bbooster.core.optimization

import com.honghoaibao.bbooster.core.diagnostics.device.BatteryStatus
import com.honghoaibao.bbooster.core.diagnostics.device.DeviceDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.ThermalStatus

/**
 * A read-only assessment produced from an existing Android diagnostics snapshot.
 *
 * Findings are advisory signals only. This engine never executes commands,
 * changes system settings, stops applications, or requests additional access.
 */
object OptimizationEngine {

    const val LOW_STORAGE_FREE_PERCENT = 10
    const val LOW_BATTERY_PERCENT = 20

    fun analyze(diagnostics: DeviceDiagnostics?): List<OptimizationFinding> {
        if (diagnostics == null) return emptyList()

        val findings = mutableListOf<OptimizationFinding>()

        when (diagnostics.thermal.status) {
            ThermalStatus.MODERATE -> findings += OptimizationFinding(
                rule = OptimizationRule.THERMAL_MODERATE,
                priority = OptimizationPriority.NOTICE
            )
            ThermalStatus.SEVERE,
            ThermalStatus.CRITICAL,
            ThermalStatus.EMERGENCY,
            ThermalStatus.SHUTDOWN -> findings += OptimizationFinding(
                rule = OptimizationRule.THERMAL_HIGH,
                priority = OptimizationPriority.CRITICAL
            )
            ThermalStatus.NOT_SUPPORTED,
            ThermalStatus.UNKNOWN,
            ThermalStatus.NONE,
            ThermalStatus.LIGHT -> Unit
        }

        diagnostics.memory
            ?.takeIf { it.totalBytes > 0L && it.isLowMemory }
            ?.let {
                findings += OptimizationFinding(
                    rule = OptimizationRule.SYSTEM_LOW_MEMORY,
                    priority = OptimizationPriority.WARNING
                )
            }

        diagnostics.storage
            ?.takeIf { it.totalBytes > 0L && it.availableBytes in 0L..it.totalBytes }
            ?.let { storage ->
                val freeRatio = storage.availableBytes.toDouble() / storage.totalBytes.toDouble()
                if (freeRatio <= LOW_STORAGE_FREE_PERCENT / 100.0) {
                    findings += OptimizationFinding(
                        rule = OptimizationRule.LOW_STORAGE,
                        priority = OptimizationPriority.WARNING,
                        observedPercent = (freeRatio * 100.0).toInt()
                    )
                }
            }

        diagnostics.battery
            ?.takeIf {
                it.status == BatteryStatus.DISCHARGING &&
                    it.levelPercent != null &&
                    it.levelPercent in 0..LOW_BATTERY_PERCENT
            }
            ?.let { battery ->
                findings += OptimizationFinding(
                    rule = OptimizationRule.LOW_BATTERY,
                    priority = OptimizationPriority.NOTICE,
                    observedPercent = battery.levelPercent
                )
            }

        return findings.sortedBy { it.priority.ordinal }
    }
}

enum class OptimizationPriority {
    CRITICAL,
    WARNING,
    NOTICE
}

enum class OptimizationRule {
    THERMAL_HIGH,
    THERMAL_MODERATE,
    SYSTEM_LOW_MEMORY,
    LOW_STORAGE,
    LOW_BATTERY
}

/**
 * A detected condition, not an executable optimization action.
 *
 * [observedPercent] is populated only when the finding has a meaningful
 * percentage measurement, such as free storage or battery level.
 */
data class OptimizationFinding(
    val rule: OptimizationRule,
    val priority: OptimizationPriority,
    val observedPercent: Int? = null
)
