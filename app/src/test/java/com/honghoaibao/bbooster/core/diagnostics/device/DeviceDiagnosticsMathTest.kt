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
    fun convertsBatteryTemperatureAndVoltage() {
        assertEquals(32.5, DeviceDiagnosticsMath.temperatureCelsius(325)!!, 0.001)
        assertNull(DeviceDiagnosticsMath.temperatureCelsius(-1))
        assertEquals(4000, DeviceDiagnosticsMath.voltageMillivolts(4000))
        assertNull(DeviceDiagnosticsMath.voltageMillivolts(0))
    }
}
