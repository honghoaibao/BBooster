package com.honghoaibao.bbooster.core.diagnostics.device

import com.honghoaibao.bbooster.core.logging.AppLog
import com.honghoaibao.bbooster.core.logging.LogModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns foreground device sampling and a bounded in-memory snapshot history.
 *
 * The first sample is collected immediately. Subsequent samples are scheduled
 * at a fixed interval while the supplied lifecycle scope remains active.
 * Calling [stop] pauses collection without discarding the retained history.
 */
class DeviceMonitoringController(
    private val provider: DeviceDiagnosticsProvider,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    private val maxSamples: Int = MAX_SAMPLES,
    private val clockMillis: () -> Long = {
        System.nanoTime() / NANOS_PER_MILLISECOND
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val jobLock = Any()
    private val samplesLock = Any()
    private var monitoringJob: Job? = null

    private val mutableSamples = MutableStateFlow<List<DeviceDiagnostics>>(emptyList())
    val samples: StateFlow<List<DeviceDiagnostics>> = mutableSamples.asStateFlow()

    private val mutableLatestSnapshot = MutableStateFlow<DeviceDiagnostics?>(null)
    val latestSnapshot: StateFlow<DeviceDiagnostics?> = mutableLatestSnapshot.asStateFlow()

    val isMonitoring: Boolean
        get() = synchronized(jobLock) { monitoringJob?.isActive == true }

    init {
        require(pollIntervalMillis > 0L) {
            "The monitoring interval must be greater than zero."
        }
        require(maxSamples in 1..MAX_SAMPLES) {
            "The in-memory history must contain between 1 and $MAX_SAMPLES samples."
        }
    }

    /**
     * Starts sampling in the supplied lifecycle scope. Repeated starts are safe.
     */
    fun start(scope: CoroutineScope) {
        synchronized(jobLock) {
            if (monitoringJob?.isActive == true) return
            monitoringJob = scope.launch(dispatcher) {
                monitorLoop()
            }
        }
    }

    /**
     * Stops periodic sampling. Existing samples remain available in memory.
     */
    fun stop() {
        synchronized(jobLock) {
            monitoringJob?.cancel()
            monitoringJob = null
        }
    }

    /**
     * Collects one snapshot and adds it to the bounded history.
     * This is also used by the Dashboard's manual refresh action.
     */
    suspend fun collectOnce(): DeviceDiagnostics {
        val snapshot = provider.collect()
        synchronized(samplesLock) {
            val updated = (mutableSamples.value + snapshot).takeLast(maxSamples)
            mutableSamples.value = updated
            mutableLatestSnapshot.value = snapshot
        }
        return snapshot
    }

    private suspend fun monitorLoop() {
        while (currentCoroutineContext().isActive) {
            val cycleStartedAt = clockMillis()
            try {
                collectOnce()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.warn(
                    LogModule.DEVICE,
                    "device_monitoring_sample_failed",
                    "exception=" + error.javaClass.simpleName
                )
            }

            val elapsed = (clockMillis() - cycleStartedAt).coerceAtLeast(0L)
            val remaining = (pollIntervalMillis - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) {
                delay(remaining)
            } else {
                yield()
            }
        }
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS = 5_000L
        const val MAX_SAMPLES = 60
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
