package com.honghoaibao.bbooster.core.diagnostics.device

data class DeviceDiagnostics(
    val collectedAtMillis: Long,
    val android: AndroidDiagnostics,
    val device: DeviceHardwareDiagnostics,
    val memory: MemoryDiagnostics?,
    val storage: StorageDiagnostics?,
    val battery: BatteryDiagnostics?,
    val uptimeMillis: Long,
    /**
     * BBooster process CPU use as a percentage of the total logical CPU capacity.
     * Null means that a valid interval has not been measured yet.
     */
    val processCpuUsagePercent: Double? = null,
    /**
     * Overall Android thermal-throttling status, not a direct CPU/GPU temperature.
     */
    val thermal: ThermalDiagnostics = ThermalDiagnostics(ThermalStatus.UNKNOWN)
)

data class AndroidDiagnostics(
    val release: String,
    val sdkLevel: Int
)

data class DeviceHardwareDiagnostics(
    val manufacturer: String,
    val model: String,
    val cpuAbis: List<String>
)

data class MemoryDiagnostics(
    val totalBytes: Long,
    val availableBytes: Long,
    val thresholdBytes: Long,
    val isLowMemory: Boolean
)

data class StorageDiagnostics(
    val totalBytes: Long,
    val availableBytes: Long
) {
    val usedBytes: Long
        get() = (totalBytes - availableBytes).coerceAtLeast(0L)
}

data class BatteryDiagnostics(
    val levelPercent: Int?,
    val status: BatteryStatus,
    val plugged: BatteryPlugged,
    val temperatureCelsius: Double?,
    val voltageMillivolts: Int?
)

data class ThermalDiagnostics(
    val status: ThermalStatus
)

enum class ThermalStatus {
    NOT_SUPPORTED,
    UNKNOWN,
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN
}

enum class BatteryStatus {
    CHARGING,
    DISCHARGING,
    FULL,
    NOT_CHARGING,
    UNKNOWN
}

enum class BatteryPlugged {
    NONE,
    AC,
    USB,
    WIRELESS,
    OTHER,
    UNKNOWN
}
