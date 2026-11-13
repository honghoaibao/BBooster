package com.honghoaibao.bbooster.core.shizuku

enum class ShizukuConnectionStatus {
    CHECKING,
    SERVICE_NOT_RUNNING,
    PERMISSION_REQUIRED,
    REQUESTING_PERMISSION,
    READY,
    UNSUPPORTED_API,
    ERROR
}

enum class ShizukuPrivilege {
    ADB,
    ROOT,
    UNKNOWN
}

data class ShizukuConnectionState(
    val status: ShizukuConnectionStatus = ShizukuConnectionStatus.CHECKING,
    val privilege: ShizukuPrivilege? = null,
    val serverUid: Int? = null,
    val serverVersion: Int? = null,
    val message: String? = null
)
