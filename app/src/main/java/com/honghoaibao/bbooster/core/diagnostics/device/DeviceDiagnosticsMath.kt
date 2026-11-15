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

    fun temperatureCelsius(rawTenthsCelsius: Int): Double? {
        if (rawTenthsCelsius < 0) return null
        return rawTenthsCelsius / 10.0
    }

    fun voltageMillivolts(rawMillivolts: Int): Int? =
        rawMillivolts.takeIf { it > 0 }
}
