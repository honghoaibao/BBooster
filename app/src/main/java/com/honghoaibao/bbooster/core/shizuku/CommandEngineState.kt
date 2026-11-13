package com.honghoaibao.bbooster.core.shizuku

data class CommandEngineState(
    val serviceStatus: CommandServiceStatus = CommandServiceStatus.DISCONNECTED,
    val isRunning: Boolean = false,
    val lastResult: CommandBatchResult? = null,
    val error: CommandEngineError? = null
)

enum class CommandServiceStatus {
    DISCONNECTED,
    BINDING,
    READY,
    ERROR
}

enum class CommandEngineError {
    SHIZUKU_UNAVAILABLE,
    PERMISSION_REQUIRED,
    BIND_TIMEOUT,
    SERVICE_DISCONNECTED,
    REMOTE_CALL_FAILED
}

data class CommandBatchResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean
)
