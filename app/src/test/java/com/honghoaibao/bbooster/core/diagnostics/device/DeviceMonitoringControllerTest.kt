package com.honghoaibao.bbooster.core.diagnostics.device

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMonitoringControllerTest {

    @Test
    fun startsImmediatelyAndStopsPollingWhenRequested() = runBlocking {
        val provider = CountingDeviceDiagnosticsProvider()
        val controller = DeviceMonitoringController(
            provider = provider,
            pollIntervalMillis = 20L
        )

        controller.start(this)
        controller.start(this)

        withTimeout(1_000L) {
            controller.samples.first { it.size >= 3 }
        }

        controller.stop()
        val callsAtStop = provider.calls.get()
        delay(80L)

        assertFalse(controller.isMonitoring)
        assertEquals(callsAtStop, provider.calls.get())
        assertNotNull(controller.latestSnapshot.value)
    }

    @Test
    fun retainsOnlyTheConfiguredNumberOfMostRecentSamples() = runBlocking {
        val provider = CountingDeviceDiagnosticsProvider()
        val controller = DeviceMonitoringController(
            provider = provider,
            pollIntervalMillis = 5L,
            maxSamples = 3
        )

        controller.start(this)
        withTimeout(1_000L) {
            controller.samples.first {
                (it.lastOrNull()?.collectedAtMillis ?: 0L) >= 6L
            }
        }
        controller.stop()

        val samples = controller.samples.value
        assertEquals(3, samples.size)
        assertTrue(samples[0].collectedAtMillis < samples[1].collectedAtMillis)
        assertTrue(samples[1].collectedAtMillis < samples[2].collectedAtMillis)
        assertEquals(samples.last(), controller.latestSnapshot.value)
    }

    @Test
    fun stoppingAndStartingAgainRetainsExistingHistory() = runBlocking {
        val provider = CountingDeviceDiagnosticsProvider()
        val controller = DeviceMonitoringController(
            provider = provider,
            pollIntervalMillis = 10L,
            maxSamples = 5
        )

        controller.start(this)
        withTimeout(1_000L) {
            controller.samples.first { it.isNotEmpty() }
        }
        controller.stop()
        val previousCount = controller.samples.value.size

        controller.start(this)
        withTimeout(1_000L) {
            controller.samples.first { it.size > previousCount }
        }
        controller.stop()

        assertTrue(controller.samples.value.size > previousCount)
    }

    private class CountingDeviceDiagnosticsProvider : DeviceDiagnosticsProvider {
        val calls = AtomicInteger(0)

        override suspend fun collect(): DeviceDiagnostics {
            val sample = calls.incrementAndGet()
            return DeviceDiagnostics(
                collectedAtMillis = sample.toLong(),
                android = AndroidDiagnostics(release = "13", sdkLevel = 33),
                device = DeviceHardwareDiagnostics(
                    manufacturer = "Test",
                    model = "Device",
                    cpuAbis = listOf("arm64-v8a")
                ),
                memory = null,
                storage = null,
                battery = null,
                uptimeMillis = sample.toLong()
            )
        }
    }
}
