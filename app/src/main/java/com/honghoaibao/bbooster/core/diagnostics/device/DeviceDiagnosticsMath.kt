package com.honghoaibao.bbooster.core.diagnostics.device

import kotlin.math.min

internal object DeviceDiagnosticsMath {

    fun batteryPercent(level: Int, scale: Int): Int? {
        if (level < 0 || scale <= 0) return null
        return ((level.toDouble() / scale.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)
    }

    fun availableBytes(totalBytes: Long, availableBytes: Long): Long {
        if (totalBytes <= 0L || availableBytes <= 0L) return 0L
        return min(totalBytes, availableBytes)
    }

    fun multiplyBytes(blockCount: Long, blockSize: Long): Long {
        if (blockCount <= 0L || blockSize <= 0L) return 0L
        return try {
            Math.multiplyExact(blockCount, blockSize)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
    }

    /**
     * Returns process CPU use as a percentage of total logical CPU capacity.
     * A process using one full core on a four-core device is reported as 25%.
     */
    fun cpuUsagePercent(
        cpuDeltaMillis: Long,
        elapsedDeltaMillis: Long,
        processorCount: Int
    ): Double? {
        if (cpuDeltaMillis < 0L || elapsedDeltaMillis <= 0L || processorCount <= 0) {
            return null
        }

        val percent = (
            cpuDeltaMillis.toDouble() / elapsedDeltaMillis.toDouble()
            ) / processorCount.toDouble() * 100.0

        return percent.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)
    }

    /**
     * Maps the stable PowerManager thermal status integer values.
     * API 29 introduced the public thermal status query.
     */
    fun thermalStatus(sdkLevel: Int, rawStatus: Int?): ThermalStatus {
        if (sdkLevel < 29) return ThermalStatus.NOT_SUPPORTED

        return when (rawStatus) {
            0 -> ThermalStatus.NONE
            1 -> ThermalStatus.LIGHT
            2 -> ThermalStatus.MODERATE
            3 -> ThermalStatus.SEVERE
            4 -> ThermalStatus.CRITICAL
            5 -> ThermalStatus.EMERGENCY
            6 -> ThermalStatus.SHUTDOWN
            else -> ThermalStatus.UNKNOWN
        }
    }

    fun temperatureCelsius(rawTenthsCelsius: Int): Double? {
        if (rawTenthsCelsius < 0) return null
        return rawTenthsCelsius / 10.0
    }

    fun voltageMillivolts(rawMillivolts: Int): Int? =
        rawMillivolts.takeIf { it > 0 }
}
