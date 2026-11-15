package com.honghoaibao.bbooster.core.diagnostics.transport

import com.honghoaibao.bbooster.core.command.CommandCapability
import com.honghoaibao.bbooster.core.command.CommandDispatchState
import com.honghoaibao.bbooster.core.command.CommandExecutionStatus
import com.honghoaibao.bbooster.core.command.CommandFailureCode
import com.honghoaibao.bbooster.core.command.CommandTransportType
import com.honghoaibao.bbooster.core.shizuku.transport.TransportAvailability

data class TransportDiagnosticsSnapshot(
    val collectedAtMillis: Long,
    val preferredTransport: CommandTransportType,
    val lastCommandTransport: CommandTransportType?,
    val isRefreshing: Boolean,
    val isExecuting: Boolean,
    val userService: TransportStatusSnapshot,
    val directProcess: TransportStatusSnapshot,
    val lastUserServiceCheckAtMillis: Long?,
    val lastDirectProcessCheckAtMillis: Long?,
    val lastCommandAtMillis: Long?
)

data class TransportStatusSnapshot(
    val transport: CommandTransportType,
    val availability: TransportAvailability,
    val capabilities: Set<CommandCapability>,
    val detail: String?
)

data class TransportTestResult(
    val transport: CommandTransportType,
    val testedAtMillis: Long,
    val connectionAvailability: TransportAvailability,
    val connectionResponseTimeMillis: Long,
    val command: TransportCommandSummary?,
    val commandResponseTimeMillis: Long?,
    val totalResponseTimeMillis: Long,
    val outcome: TransportTestOutcome,
    val failureCode: CommandFailureCode?
)

data class TransportCommandSummary(
    val commandId: String,
    val status: CommandExecutionStatus,
    val dispatchState: CommandDispatchState,
    val exitCode: Int?,
    val durationMillis: Long,
    val failureCode: CommandFailureCode?
)

enum class TransportTestOutcome {
    PASSED,
    CONNECTION_FAILED,
    UNSUPPORTED,
    COMMAND_REJECTED,
    NOT_DISPATCHED,
    DISPATCH_UNKNOWN,
    DISPATCHED_TIMEOUT,
    COMMAND_FAILED
}
