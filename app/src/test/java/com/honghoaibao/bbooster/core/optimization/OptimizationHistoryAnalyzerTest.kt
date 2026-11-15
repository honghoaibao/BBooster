package com.honghoaibao.bbooster.core.optimization

import com.honghoaibao.bbooster.core.diagnostics.device.AndroidDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.BatteryDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.BatteryPlugged
import com.honghoaibao.bbooster.core.diagnostics.device.BatteryStatus
import com.honghoaibao.bbooster.core.diagnostics.device.DeviceDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.DeviceHardwareDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.MemoryDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.StorageDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.ThermalDiagnostics
import com.honghoaibao.bbooster.core.diagnostics.device.ThermalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptimizationHistoryAnalyzerTest {

    @Test
    fun analyzesAllFourMetricsFromRecordedSnapshots() {
        val trends = OptimizationHistoryAnalyzer.analyze(
            (0 until 6).map { index ->
                snapshot(
                    timestamp = index * 5_000L,
                    memoryAvailable = if (index < 3) 8_000L else 4_000L,
                    storageAvailable = if (index < 3) 7_000L else 5_000L,
                    batteryLevel = if (index < 3) 90 else 70,
                    thermalStatus = if (index < 3) ThermalStatus.NONE
                    else ThermalStatus.MODERATE
                )
            }
        )

        assertEquals(
            listOf(
                OptimizationTrendMetric.AVAILABLE_RAM,
                OptimizationTrendMetric.FREE_STORAGE,
                OptimizationTrendMetric.BATTERY_LEVEL,
                OptimizationTrendMetric.THERMAL_STATUS
            ),
            trends.map { it.metric }
        )
        assertEquals(OptimizationTrendDirection.DECLINING, trends[0].direction)
        assertEquals(40.0, trends[0].latestValue, 0.001)
        assertEquals(OptimizationTrendDirection.DECLINING, trends[1].direction)
        assertEquals(OptimizationTrendDirection.DECLINING, trends[2].direction)
        assertEquals(OptimizationTrendDirection.WORSENING, trends[3].direction)
        assertEquals(OptimizationTrendAnalyzerSampleCount, trends[0].sampleCount)
    }

    @Test
    fun requiresAtLeastThreeValidSamplesForEachMetric() {
        val history = listOf(
            snapshot(timestamp = 0L),
            snapshot(timestamp = 5_000L)
        )

        assertTrue(OptimizationHistoryAnalyzer.analyze(history).isEmpty())
    }

    @Test
    fun ignoresInvalidMeasurementsWithoutInventingValues() {
        val history = (0 until 3).map { index ->
            snapshot(
                timestamp = index * 5_000L,
                memoryTotal = 0L,
                storageTotal = 0L,
                batteryLevel = 150,
                thermalStatus = ThermalStatus.UNKNOWN
            )
        }

        assertTrue(OptimizationHistoryAnalyzer.analyze(history).isEmpty())
    }

    @Test
    fun ordersSnapshotsByCollectionTimeBeforeCalculatingTrend() {
        val history = listOf(
            snapshot(timestamp = 10_000L, memoryAvailable = 4_000L),
            snapshot(timestamp = 0L, memoryAvailable = 8_000L),
            snapshot(timestamp = 5_000L, memoryAvailable = 6_000L)
        )

        val trend = OptimizationHistoryAnalyzer.analyze(history)
            .single { it.metric == OptimizationTrendMetric.AVAILABLE_RAM }

        assertEquals(OptimizationTrendDirection.DECLINING, trend.direction)
    }

    @Test
    fun classifiesSmallChangesAsStable() {
        val history = listOf(
            snapshot(timestamp = 0L, memoryAvailable = 8_000L, storageAvailable = 6_000L, batteryLevel = 70),
            snapshot(timestamp = 5_000L, memoryAvailable = 8_050L, storageAvailable = 6_050L, batteryLevel = 70),
            snapshot(timestamp = 10_000L, memoryAvailable = 8_100L, storageAvailable = 6_100L, batteryLevel = 71)
        )

        val trends = OptimizationHistoryAnalyzer.analyze(history)

        assertEquals(OptimizationTrendDirection.STABLE, trends.single {
            it.metric == OptimizationTrendMetric.AVAILABLE_RAM
        }.direction)
        assertEquals(OptimizationTrendDirection.STABLE, trends.single {
            it.metric == OptimizationTrendMetric.FREE_STORAGE
        }.direction)
        assertEquals(OptimizationTrendDirection.STABLE, trends.single {
            it.metric == OptimizationTrendMetric.BATTERY_LEVEL
        }.direction)
    }

    @Test
    fun mapsOnlySupportedThermalStatusesIntoHistory() {
        val history = listOf(
            snapshot(timestamp = 0L, thermalStatus = ThermalStatus.NOT_SUPPORTED),
            snapshot(timestamp = 5_000L, thermalStatus = ThermalStatus.UNKNOWN),
            snapshot(timestamp = 7_500L, thermalStatus = ThermalStatus.LIGHT),
            snapshot(timestamp = 10_000L, thermalStatus = ThermalStatus.MODERATE),
            snapshot(timestamp = 15_000L, thermalStatus = ThermalStatus.SEVERE)
        )

        val trend = OptimizationHistoryAnalyzer.analyze(history)
            .single { it.metric == OptimizationTrendMetric.THERMAL_STATUS }

        assertEquals(OptimizationTrendDirection.WORSENING, trend.direction)
        assertEquals(
            ThermalStatus.SEVERE,
            OptimizationHistoryAnalyzer.thermalStatusForSeverity(trend.latestValue)
        )
    }

    private fun snapshot(
        timestamp: Long,
        memoryTotal: Long = 10_000L,
        memoryAvailable: Long = 8_000L,
        storageTotal: Long = 10_000L,
        storageAvailable: Long = 6_000L,
        batteryLevel: Int? = 70,
        thermalStatus: ThermalStatus = ThermalStatus.NONE
    ) = DeviceDiagnostics(
        collectedAtMillis = timestamp,
        android = AndroidDiagnostics(release = "13", sdkLevel = 33),
        device = DeviceHardwareDiagnostics("Test", "Test Device", listOf("arm64-v8a")),
        memory = MemoryDiagnostics(memoryTotal, memoryAvailable, 0L, false)
            .takeIf { memoryTotal > 0L },
        storage = StorageDiagnostics(storageTotal, storageAvailable)
            .takeIf { storageTotal > 0L },
        battery = BatteryDiagnostics(
            batteryLevel, BatteryStatus.DISCHARGING, BatteryPlugged.NONE, null, null
        ),
        uptimeMillis = timestamp,
        thermal = ThermalDiagnostics(thermalStatus)
    )

    private companion object {
        const val OptimizationTrendAnalyzerSampleCount = 6
    }
}
