package com.honghoaibao.bbooster.core.command

/**
 * Transport-neutral outcome of one registered command.
 *
 * A null transport means that execution never reached a transport (for example,
 * authorization failed before dispatch).
 */
data class CommandResult(
    val commandId: String,
    val transport: CommandTransportType?,
    val status: CommandExecutionStatus,
    val dispatchState: CommandDispatchState,
    val exitCode: Int? = null,
    val stdout: String = "",
    val stderr: String = "",
    val durationMs: Long = 0L,
    val failureCode: CommandFailureCode? = null,
    val failureDetail: String? = null,
    val outputTruncated: Boolean = false
) {
    val isSuccess: Boolean
        get() = status == CommandExecutionStatus.COMPLETED && exitCode == 0

    /**
     * A transport fallback is safe only when the current attempt is known not
     * to have dispatched the command. UNKNOWN must never be treated as safe.
     */
    val canSafelyFallback: Boolean
        get() = dispatchState == CommandDispatchState.NOT_DISPATCHED

    init {
        require(commandId.isNotBlank()) { "Command ID must not be blank." }
        require(durationMs >= 0L) { "Command duration cannot be negative." }
    }
}

enum class CommandExecutionStatus {
    NOT_STARTED,
    COMPLETED,
    FAILED,
    REJECTED,
    TIMED_OUT,
    CANCELLED,
    INTERRUPTED,
    UNKNOWN
}

enum class CommandDispatchState {
    NOT_DISPATCHED,
    DISPATCHED,
    UNKNOWN
}

enum class CommandFailureCode {
    SHIZUKU_UNAVAILABLE,
    PERMISSION_REQUIRED,
    TRANSPORT_UNAVAILABLE,
    TRANSPORT_UNSUPPORTED,
    USER_CONFIRMATION_REQUIRED,
    BIND_FAILED,
    PROCESS_START_FAILED,
    PROCESS_INTERRUPTED,
    REMOTE_CALL_FAILED,
    COMMAND_TIMED_OUT,
    COMMAND_REJECTED,
    OUTPUT_LIMIT_EXCEEDED,
    INTERNAL_ERROR
}
