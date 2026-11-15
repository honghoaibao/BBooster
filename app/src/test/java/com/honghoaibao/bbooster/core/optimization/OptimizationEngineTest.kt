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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OptimizationEngineTest {

    @Test
    fun returnsNoFindingsWhenSnapshotIsUnavailable() {
        assertTrue(OptimizationEngine.analyze(null).isEmpty())
    }

    @Test
    fun returnsNoFindingsForHealthySnapshot() {
        assertTrue(OptimizationEngine.analyze(snapshot()).isEmpty())
    }

    @Test
    fun classifiesThermalStatesWithoutTreatingLightAsAnIssue() {
        assertEquals(
            OptimizationFinding(OptimizationRule.THERMAL_MODERATE, OptimizationPriority.NOTICE),
            OptimizationEngine.analyze(snapshot(thermalStatus = ThermalStatus.MODERATE)).single()
        )
        assertEquals(
            OptimizationFinding(OptimizationRule.THERMAL_HIGH, OptimizationPriority.CRITICAL),
            OptimizationEngine.analyze(snapshot(thermalStatus = ThermalStatus.SEVERE)).single()
        )
        assertTrue(OptimizationEngine.analyze(snapshot(thermalStatus = ThermalStatus.LIGHT)).isEmpty())
    }

    @Test
    fun reportsOnlyTheSystemLowMemorySignal() {
        val finding = OptimizationEngine.analyze(snapshot(memoryLow = true)).single()
        assertEquals(OptimizationRule.SYSTEM_LOW_MEMORY, finding.rule)
        assertEquals(OptimizationPriority.WARNING, finding.priority)
        assertNull(finding.observedPercent)
    }

    @Test
    fun flagsStorageAtOrBelowTenPercentFree() {
        val finding = OptimizationEngine.analyze(
            snapshot(storageTotalBytes = 1_000L, storageAvailableBytes = 100L)
        ).single()
        assertEquals(OptimizationRule.LOW_STORAGE, finding.rule)
        assertEquals(10, finding.observedPercent)
        assertTrue(
            OptimizationEngine.analyze(
                snapshot(storageTotalBytes = 1_000L, storageAvailableBytes = 101L)
            ).isEmpty()
        )
    }

    @Test
    fun flagsLowBatteryOnlyWhileDischarging() {
        val discharging = OptimizationEngine.analyze(
            snapshot(batteryLevel = 20, batteryStatus = BatteryStatus.DISCHARGING)
        ).single()
        assertEquals(OptimizationRule.LOW_BATTERY, discharging.rule)
        assertEquals(20, discharging.observedPercent)
        assertTrue(
            OptimizationEngine.analyze(
                snapshot(batteryLevel = 10, batteryStatus = BatteryStatus.CHARGING)
            ).isEmpty()
        )
    }

    @Test
    fun ignoresInvalidOrUnavailableMeasurements() {
        val findings = OptimizationEngine.analyze(
            snapshot(
                thermalStatus = ThermalStatus.UNKNOWN,
                storageTotalBytes = 0L,
                storageAvailableBytes = 0L,
                batteryLevel = 120
            )
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun sortsFindingsByPriorityAndKeepsStableRuleOrder() {
        val findings = OptimizationEngine.analyze(
            snapshot(
                thermalStatus = ThermalStatus.CRITICAL,
                memoryLow = true,
                storageTotalBytes = 1_000L,
                storageAvailableBytes = 50L,
                batteryLevel = 15,
                batteryStatus = BatteryStatus.DISCHARGING
            )
        )
        assertEquals(
            listOf(
                OptimizationRule.THERMAL_HIGH,
                OptimizationRule.SYSTEM_LOW_MEMORY,
                OptimizationRule.LOW_STORAGE,
                OptimizationRule.LOW_BATTERY
            ),
            findings.map { it.rule }
        )
        assertEquals(
            listOf(
                OptimizationPriority.CRITICAL,
                OptimizationPriority.WARNING,
                OptimizationPriority.WARNING,
                OptimizationPriority.NOTICE
            ),
            findings.map { it.priority }
        )
    }

    private fun snapshot(
        thermalStatus: ThermalStatus = ThermalStatus.NONE,
        memoryLow: Boolean = false,
        storageTotalBytes: Long = 10_000L,
        storageAvailableBytes: Long = 5_000L,
        batteryLevel: Int? = 80,
        batteryStatus: BatteryStatus = BatteryStatus.DISCHARGING
    ) = DeviceDiagnostics(
        collectedAtMillis = 1_000L,
        android = AndroidDiagnostics(release = "13", sdkLevel = 33),
        device = DeviceHardwareDiagnostics("Test", "Test Device", listOf("arm64-v8a")),
        memory = MemoryDiagnostics(8_000L, 4_000L, 500L, memoryLow),
        storage = StorageDiagnostics(storageTotalBytes, storageAvailableBytes),
        battery = BatteryDiagnostics(
            batteryLevel, batteryStatus, BatteryPlugged.NONE, null, null
        ),
        uptimeMillis = 2_000L,
        thermal = ThermalDiagnostics(thermalStatus)
    )
}
