package com.honghoaibao.bbooster.core.optimization

import com.honghoaibao.bbooster.core.diagnostics.device.DeviceDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.ThermalStatus
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Derives short-term trends from the bounded, in-memory device monitoring history.
 *
 * Only real, valid Android snapshots are used. This analyzer does not persist data,
 * estimate hardware temperatures, or infer metrics that were not collected.
 */
object OptimizationHistoryAnalyzer {

    const val MIN_VALID_SAMPLES = 3
    const val PERCENT_CHANGE_THRESHOLD = 2.0
    const val THERMAL_CHANGE_THRESHOLD = 1.0
    private const val MEDIAN_WINDOW_LIMIT = 5

    fun analyze(history: List<DeviceDiagnostics>): List<OptimizationTrend> {
        if (history.isEmpty()) return emptyList()

        val snapshots = history
            .asSequence()
            .filter { it.collectedAtMillis >= 0L }
            .sortedBy { it.collectedAtMillis }
            .distinctBy { it.collectedAtMillis }
            .toList()

        return buildList {
            percentTrend(
                snapshots = snapshots,
                metric = OptimizationTrendMetric.AVAILABLE_RAM
            ) { snapshot ->
                snapshot.memory?.let { memory ->
                    percentage(memory.totalBytes, memory.availableBytes)
                }
            }?.let { add(it) }

            percentTrend(
                snapshots = snapshots,
                metric = OptimizationTrendMetric.FREE_STORAGE
            ) { snapshot ->
                snapshot.storage?.let { storage ->
                    percentage(storage.totalBytes, storage.availableBytes)
                }
            }?.let { add(it) }

            percentTrend(
                snapshots = snapshots,
                metric = OptimizationTrendMetric.BATTERY_LEVEL
            ) { snapshot ->
                snapshot.battery?.levelPercent
                    ?.takeIf { it in 0..100 }
                    ?.toDouble()
            }?.let { add(it) }

            thermalTrend(snapshots)?.let { add(it) }
        }
    }

    /** Converts a sampled Android thermal severity value back to its public status. */
    fun thermalStatusForSeverity(severity: Double): ThermalStatus =
        when (severity.roundToInt().coerceIn(0, 6)) {
            0 -> ThermalStatus.NONE
            1 -> ThermalStatus.LIGHT
            2 -> ThermalStatus.MODERATE
            3 -> ThermalStatus.SEVERE
            4 -> ThermalStatus.CRITICAL
            5 -> ThermalStatus.EMERGENCY
            else -> ThermalStatus.SHUTDOWN
        }

    private fun percentTrend(
        snapshots: List<DeviceDiagnostics>,
        metric: OptimizationTrendMetric,
        valueProvider: (DeviceDiagnostics) -> Double?
    ): OptimizationTrend? {
        val values = snapshots.mapNotNull { snapshot ->
            valueProvider(snapshot)?.let { value ->
                MetricSample(snapshot.collectedAtMillis, value)
            }
        }
        return createTrend(metric, values, thermal = false)
    }

    private fun thermalTrend(
        snapshots: List<DeviceDiagnostics>
    ): OptimizationTrend? {
        val values = snapshots.mapNotNull { snapshot ->
            snapshot.thermal.status.toSeverityOrNull()?.let { severity ->
                MetricSample(snapshot.collectedAtMillis, severity)
            }
        }
        return createTrend(
            metric = OptimizationTrendMetric.THERMAL_STATUS,
            samples = values,
            thermal = true
        )
    }

    private fun createTrend(
        metric: OptimizationTrendMetric,
        samples: List<MetricSample>,
        thermal: Boolean
    ): OptimizationTrend? {
        val ordered = samples
            .sortedBy { it.timestampMillis }
            .distinctBy { it.timestampMillis }
        if (ordered.size < MIN_VALID_SAMPLES) return null

        // Compare medians of small beginning/end windows to reduce one-sample noise.
        val windowSize = (ordered.size / 3)
            .coerceAtLeast(1)
            .coerceAtMost(MEDIAN_WINDOW_LIMIT)
        val baseline = median(ordered.take(windowSize).map { it.value })
        val latest = median(ordered.takeLast(windowSize).map { it.value })
        val change = latest - baseline
        val threshold = if (thermal) {
            THERMAL_CHANGE_THRESHOLD
        } else {
            PERCENT_CHANGE_THRESHOLD
        }

        val direction = when {
            abs(change) < threshold -> OptimizationTrendDirection.STABLE
            thermal && change > 0.0 -> OptimizationTrendDirection.WORSENING
            thermal && change < 0.0 -> OptimizationTrendDirection.COOLING
            !thermal && change > 0.0 -> OptimizationTrendDirection.IMPROVING
            else -> OptimizationTrendDirection.DECLINING
        }

        return OptimizationTrend(
            metric = metric,
            direction = direction,
            baselineValue = baseline,
            latestValue = latest,
            sampleCount = ordered.size
        )
    }

    private fun percentage(totalBytes: Long, availableBytes: Long): Double? {
        if (totalBytes <= 0L || availableBytes !in 0L..totalBytes) return null
        return availableBytes.toDouble() / totalBytes.toDouble() * 100.0
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    private fun ThermalStatus.toSeverityOrNull(): Double? = when (this) {
        ThermalStatus.NONE -> 0.0
        ThermalStatus.LIGHT -> 1.0
        ThermalStatus.MODERATE -> 2.0
        ThermalStatus.SEVERE -> 3.0
        ThermalStatus.CRITICAL -> 4.0
        ThermalStatus.EMERGENCY -> 5.0
        ThermalStatus.SHUTDOWN -> 6.0
        ThermalStatus.NOT_SUPPORTED,
        ThermalStatus.UNKNOWN -> null
    }

    private data class MetricSample(
        val timestampMillis: Long,
        val value: Double
    )
}

enum class OptimizationTrendMetric {
    AVAILABLE_RAM,
    FREE_STORAGE,
    BATTERY_LEVEL,
    THERMAL_STATUS
}

enum class OptimizationTrendDirection {
    IMPROVING,
    DECLINING,
    STABLE,
    COOLING,
    WORSENING
}

data class OptimizationTrend(
    val metric: OptimizationTrendMetric,
    val direction: OptimizationTrendDirection,
    val baselineValue: Double,
    val latestValue: Double,
    val sampleCount: Int
)
