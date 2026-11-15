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

enum class ShizukuConnectionErrorCode {
    BINDER_CHECK_FAILED,
    BINDER_UNAVAILABLE,
    SERVER_UNSUPPORTED,
    PERMISSION_REQUIRED,
    PERMISSION_CHECK_DENIED,
    BINDER_STATE_INVALID,
    CONNECTION_CHECK_FAILED,
    PERMISSION_REQUEST_FAILED
}

data class ShizukuConnectionState(
    val status: ShizukuConnectionStatus = ShizukuConnectionStatus.CHECKING,
    val privilege: ShizukuPrivilege? = null,
    val serverUid: Int? = null,
    /** Version returned by Shizuku.getVersion(); this is not Android SDK level. */
    val serverVersion: Int? = null,
    val binderAvailable: Boolean = false,
    val permissionGranted: Boolean? = null,
    val lastCheckedAtMillis: Long? = null,
    val connectedAtMillis: Long? = null,
    val lastErrorCode: ShizukuConnectionErrorCode? = null,
    val lastErrorAtMillis: Long? = null
)
