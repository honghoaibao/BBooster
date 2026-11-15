package com.honghoaibao.bbooster.core.diagnostics.device

data class DeviceDiagnostics(
    val collectedAtMillis: Long,
    val android: AndroidDiagnostics,
    val device: DeviceHardwareDiagnostics,
    val memory: MemoryDiagnostics?,
    val storage: StorageDiagnostics?,
    val battery: BatteryDiagnostics?,
    val uptimeMillis: Long
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
