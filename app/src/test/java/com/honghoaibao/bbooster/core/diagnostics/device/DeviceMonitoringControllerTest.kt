package com.honghoaibao.bbooster.core.diagnostics.device

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceMonitoringControllerTest {

    @Test
    fun startsImmediatelyAndStopsPollingWhenRequested() = runTest {
        val provider = CountingDeviceDiagnosticsProvider()
        val controller = DeviceMonitoringController(
            provider = provider,
            pollIntervalMillis = 20L,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        controller.start(this)
        controller.start(this)
        runCurrent()

        assertEquals(1, provider.calls.get())

        advanceTimeBy(40L)
        runCurrent()

        assertTrue(provider.calls.get() >= 3)

        controller.stop()
        val callsAtStop = provider.calls.get()
        advanceTimeBy(80L)
        runCurrent()

        assertFalse(controller.isMonitoring)
        assertEquals(callsAtStop, provider.calls.get())
        assertNotNull(controller.latestSnapshot.value)
    }

    @Test
    fun retainsOnlyTheConfiguredNumberOfMostRecentSamples() = runTest {
        val provider = CountingDeviceDiagnosticsProvider()
        val controller = DeviceMonitoringController(
            provider = provider,
            pollIntervalMillis = 5L,
            maxSamples = 3,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        controller.start(this)
        runCurrent()
        advanceTimeBy(26L)
        runCurrent()
        controller.stop()

        val samples = controller.samples.value
        assertEquals(3, samples.size)
        assertEquals(
            listOf(4L, 5L, 6L),
            samples.map { it.collectedAtMillis }
        )
        assertEquals(samples.last(), controller.latestSnapshot.value)
    }

    @Test
    fun stoppingAndStartingAgainRetainsExistingHistory() = runTest {
        val provider = CountingDeviceDiagnosticsProvider()
        val controller = DeviceMonitoringController(
            provider = provider,
            pollIntervalMillis = 10L,
            maxSamples = 5,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        controller.start(this)
        runCurrent()
        controller.stop()
        val previousCount = controller.samples.value.size

        controller.start(this)
        runCurrent()
        controller.stop()

        assertEquals(1, previousCount)
        assertEquals(2, controller.samples.value.size)
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
