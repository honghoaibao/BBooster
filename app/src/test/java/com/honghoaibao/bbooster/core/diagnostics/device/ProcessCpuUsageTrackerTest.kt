package com.honghoaibao.bbooster.core.diagnostics.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessCpuUsageTrackerTest {

    @Test
    fun firstSampleOnlyEstablishesBaseline() {
        val tracker = ProcessCpuUsageTracker()

        assertNull(tracker.sample(100L, 1_000L, 4))
    }

    @Test
    fun calculatesUsageFromConsecutiveSamples() {
        val tracker = ProcessCpuUsageTracker()

        assertNull(tracker.sample(1_000L, 5_000L, 4))
        assertEquals(2.5, tracker.sample(1_100L, 6_000L, 4)!!, 0.001)
    }

    @Test
    fun reportsZeroWhenProcessConsumesNoAdditionalCpuTime() {
        val tracker = ProcessCpuUsageTracker()

        assertNull(tracker.sample(1_000L, 5_000L, 4))
        assertEquals(0.0, tracker.sample(1_000L, 6_000L, 4)!!, 0.001)
    }

    @Test
    fun counterRegressionReturnsUnavailableAndRecoversOnNextSample() {
        val tracker = ProcessCpuUsageTracker()

        assertNull(tracker.sample(1_000L, 5_000L, 4))
        assertNull(tracker.sample(900L, 6_000L, 4))
        assertEquals(0.25, tracker.sample(910L, 7_000L, 4)!!, 0.001)
    }

    @Test
    fun invalidSampleResetsTheBaseline() {
        val tracker = ProcessCpuUsageTracker()

        assertNull(tracker.sample(1_000L, 5_000L, 4))
        assertNull(tracker.sample(1_100L, 6_000L, 0))
        assertNull(tracker.sample(1_200L, 7_000L, 4))
        assertEquals(0.25, tracker.sample(1_210L, 8_000L, 4)!!, 0.001)
    }

    @Test
    fun resetRequiresANewBaseline() {
        val tracker = ProcessCpuUsageTracker()

        assertNull(tracker.sample(1_000L, 5_000L, 4))
        tracker.reset()
        assertNull(tracker.sample(1_100L, 6_000L, 4))
    }
}
