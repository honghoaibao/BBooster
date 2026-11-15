package com.honghoaibao.bbooster.core.diagnostics.device

/**
 * Calculates process CPU use from two cumulative CPU-time samples.
 *
 * The first sample establishes a baseline and therefore returns null. Invalid
 * or regressing counters also return null; the current valid sample becomes the
 * next baseline so one bad interval does not poison later measurements.
 */
internal class ProcessCpuUsageTracker {

    private var previousProcessCpuTimeMillis: Long? = null
    private var previousElapsedRealtimeMillis: Long? = null

    @Synchronized
    fun sample(
        processCpuTimeMillis: Long,
        elapsedRealtimeMillis: Long,
        processorCount: Int
    ): Double? {
        if (
            processCpuTimeMillis < 0L ||
            elapsedRealtimeMillis < 0L ||
            processorCount <= 0
        ) {
            reset()
            return null
        }

        val previousCpuTime = previousProcessCpuTimeMillis
        val previousElapsedRealtime = previousElapsedRealtimeMillis

        previousProcessCpuTimeMillis = processCpuTimeMillis
        previousElapsedRealtimeMillis = elapsedRealtimeMillis

        if (previousCpuTime == null || previousElapsedRealtime == null) {
            return null
        }

        val cpuDelta = processCpuTimeMillis - previousCpuTime
        val elapsedDelta = elapsedRealtimeMillis - previousElapsedRealtime

        return DeviceDiagnosticsMath.cpuUsagePercent(
            cpuDeltaMillis = cpuDelta,
            elapsedDeltaMillis = elapsedDelta,
            processorCount = processorCount
        )
    }

    @Synchronized
    fun reset() {
        previousProcessCpuTimeMillis = null
        previousElapsedRealtimeMillis = null
    }
}
