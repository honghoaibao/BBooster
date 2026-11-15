package com.honghoaibao.bbooster.core.diagnostics.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDiagnosticsMathTest {

    @Test
    fun calculatesBatteryPercentage() {
        assertEquals(75, DeviceDiagnosticsMath.batteryPercent(3, 4))
        assertEquals(100, DeviceDiagnosticsMath.batteryPercent(120, 100))
        assertEquals(0, DeviceDiagnosticsMath.batteryPercent(0, 100))
    }

    @Test
    fun rejectsInvalidBatteryScaleAndLevel() {
        assertNull(DeviceDiagnosticsMath.batteryPercent(-1, 100))
        assertNull(DeviceDiagnosticsMath.batteryPercent(50, 0))
        assertNull(DeviceDiagnosticsMath.batteryPercent(50, -1))
    }

    @Test
    fun clampsAvailableStorageToTotal() {
        assertEquals(80L, DeviceDiagnosticsMath.availableBytes(100L, 80L))
        assertEquals(100L, DeviceDiagnosticsMath.availableBytes(100L, 150L))
        assertEquals(0L, DeviceDiagnosticsMath.availableBytes(-1L, 10L))
        assertEquals(0L, DeviceDiagnosticsMath.availableBytes(100L, -1L))
    }

    @Test
    fun safelyMultipliesBlockCounts() {
        assertEquals(4096L, DeviceDiagnosticsMath.multiplyBytes(4L, 1024L))
        assertEquals(0L, DeviceDiagnosticsMath.multiplyBytes(0L, 1024L))
        assertEquals(Long.MAX_VALUE, DeviceDiagnosticsMath.multiplyBytes(Long.MAX_VALUE, 2L))
    }

    @Test
    fun calculatesProcessCpuPercentageAcrossLogicalProcessors() {
        assertEquals(25.0, DeviceDiagnosticsMath.cpuUsagePercent(100L, 100L, 4)!!, 0.001)
        assertEquals(6.25, DeviceDiagnosticsMath.cpuUsagePercent(25L, 100L, 4)!!, 0.001)
        assertEquals(0.0, DeviceDiagnosticsMath.cpuUsagePercent(0L, 100L, 4)!!, 0.001)
    }

    @Test
    fun rejectsInvalidCpuSamplingIntervals() {
        assertNull(DeviceDiagnosticsMath.cpuUsagePercent(-1L, 100L, 4))
        assertNull(DeviceDiagnosticsMath.cpuUsagePercent(10L, 0L, 4))
        assertNull(DeviceDiagnosticsMath.cpuUsagePercent(10L, 100L, 0))
    }

    @Test
    fun capsCpuUsageAtTotalLogicalCapacity() {
        assertEquals(100.0, DeviceDiagnosticsMath.cpuUsagePercent(300L, 100L, 2)!!, 0.001)
    }

    @Test
    fun mapsThermalStatusAndApiSupport() {
        assertEquals(ThermalStatus.NOT_SUPPORTED, DeviceDiagnosticsMath.thermalStatus(28, 0))
        assertEquals(ThermalStatus.NONE, DeviceDiagnosticsMath.thermalStatus(29, 0))
        assertEquals(ThermalStatus.LIGHT, DeviceDiagnosticsMath.thermalStatus(29, 1))
        assertEquals(ThermalStatus.MODERATE, DeviceDiagnosticsMath.thermalStatus(29, 2))
        assertEquals(ThermalStatus.SEVERE, DeviceDiagnosticsMath.thermalStatus(29, 3))
        assertEquals(ThermalStatus.CRITICAL, DeviceDiagnosticsMath.thermalStatus(29, 4))
        assertEquals(ThermalStatus.EMERGENCY, DeviceDiagnosticsMath.thermalStatus(29, 5))
        assertEquals(ThermalStatus.SHUTDOWN, DeviceDiagnosticsMath.thermalStatus(29, 6))
        assertEquals(ThermalStatus.UNKNOWN, DeviceDiagnosticsMath.thermalStatus(29, null))
        assertEquals(ThermalStatus.UNKNOWN, DeviceDiagnosticsMath.thermalStatus(35, 99))
    }

    @Test
    fun convertsBatteryTemperatureAndVoltage() {
        assertEquals(32.5, DeviceDiagnosticsMath.temperatureCelsius(325)!!, 0.001)
        assertNull(DeviceDiagnosticsMath.temperatureCelsius(-1))
        assertEquals(4000, DeviceDiagnosticsMath.voltageMillivolts(4000))
        assertNull(DeviceDiagnosticsMath.voltageMillivolts(0))
    }
}
