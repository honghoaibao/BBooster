package com.honghoaibao.bbooster.core.diagnostics.transport

import com.honghoaibao.bbooster.core.command.CommandCapability
import com.honghoaibao.bbooster.core.command.CommandDispatchState
import com.honghoaibao.bbooster.core.command.CommandExecutionStatus
import com.honghoaibao.bbooster.core.command.CommandFailureCode
import com.honghoaibao.bbooster.core.command.CommandTransportType
import com.honghoaibao.bbooster.core.logging.AppLog
import com.honghoaibao.bbooster.core.logging.LogModule
import com.honghoaibao.bbooster.core.logging.LogRedactor
import com.honghoaibao.bbooster.core.shizuku.transport.ShizukuTransportManager
import com.honghoaibao.bbooster.core.shizuku.transport.TransportAvailability
import com.honghoaibao.bbooster.core.shizuku.transport.TransportStatus

/**
 * Inspects the existing transports and can run one explicit diagnostic test on
 * one selected transport. It never accepts arbitrary command IDs or shell text.
 */
class TransportDiagnosticsProvider(
    private val transportManager: ShizukuTransportManager,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val nanoTime: () -> Long = System::nanoTime
) {

    fun snapshot(): TransportDiagnosticsSnapshot {
        val state = transportManager.state.value
        return TransportDiagnosticsSnapshot(
            collectedAtMillis = clockMillis().coerceAtLeast(0L),
            preferredTransport = CommandTransportType.USER_SERVICE,
            lastCommandTransport = state.activeTransport,
            isRefreshing = state.isRefreshing,
            isExecuting = state.isExecuting,
            userService = state.userService.toSnapshot(),
            directProcess = state.directProcess.toSnapshot(),
            lastUserServiceCheckAtMillis = state.lastUserServiceCheckAtMillis,
            lastDirectProcessCheckAtMillis = state.lastDirectProcessCheckAtMillis,
            lastCommandAtMillis = state.lastCommandAtMillis
        )
    }

    /**
     * Performs one selected transport refresh. The registered read-only
     * diagnostic command is executed only when the selected transport reports
     * the expected execution capability and a ready state.
     */
    suspend fun testTransport(
        transport: CommandTransportType
    ): TransportTestResult {
        val totalStarted = nanoTime()
        val testedAt = clockMillis().coerceAtLeast(0L)
        AppLog.info(
            LogModule.TRANSPORT,
            "transport_test_started",
            "transport=" + transport.name
        )

        val connectionStarted = nanoTime()
        val connectionStatus = transportManager.refreshTransport(transport)
        val connectionDuration = elapsedMillis(connectionStarted, nanoTime())

        if (!connectionStatus.supportsDiagnosticExecution()) {
            val outcome = when (connectionStatus.state) {
                TransportAvailability.UNSUPPORTED -> TransportTestOutcome.UNSUPPORTED
                TransportAvailability.UNAVAILABLE,
                TransportAvailability.DEGRADED,
                TransportAvailability.CHECKING,
                TransportAvailability.STARTING -> TransportTestOutcome.CONNECTION_FAILED
                TransportAvailability.READY,
                TransportAvailability.AVAILABLE -> TransportTestOutcome.UNSUPPORTED
            }
            AppLog.warn(
                LogModule.TRANSPORT,
                "transport_connection_test_failed",
                "transport=" + transport.name +
                    "; state=" + connectionStatus.state.name +
                    "; responseTimeMs=" + connectionDuration
            )
            return TransportTestResult(
                transport = transport,
                testedAtMillis = testedAt,
                connectionAvailability = connectionStatus.state,
                connectionResponseTimeMillis = connectionDuration,
                command = null,
                commandResponseTimeMillis = null,
                totalResponseTimeMillis = elapsedMillis(totalStarted, nanoTime()),
                outcome = outcome,
                failureCode = connectionStatus.state.toFailureCode()
            )
        }

        val commandStarted = nanoTime()
        val commandResult = transportManager.executeDiagnosticCommand(transport)
        val commandDuration = elapsedMillis(commandStarted, nanoTime())
        val summary = TransportCommandSummary(
            commandId = commandResult.commandId,
            status = commandResult.status,
            dispatchState = commandResult.dispatchState,
            exitCode = commandResult.exitCode,
            durationMillis = commandResult.durationMs,
            failureCode = commandResult.failureCode
        )

        val outcome = commandResult.toTestOutcome()
        val logSummary = "transport=" + transport.name +
            "; outcome=" + outcome.name +
            "; status=" + commandResult.status.name +
            "; dispatch=" + commandResult.dispatchState.name +
            "; responseTimeMs=" + commandDuration +
            "; failure=" + (commandResult.failureCode?.name ?: "none")
        if (outcome == TransportTestOutcome.PASSED) {
            AppLog.info(LogModule.TRANSPORT, "transport_test_completed", logSummary)
        } else {
            AppLog.warn(LogModule.TRANSPORT, "transport_test_not_completed", logSummary)
        }

        return TransportTestResult(
            transport = transport,
            testedAtMillis = testedAt,
            connectionAvailability = connectionStatus.state,
            connectionResponseTimeMillis = connectionDuration,
            command = summary,
            commandResponseTimeMillis = commandDuration,
            totalResponseTimeMillis = elapsedMillis(totalStarted, nanoTime()),
            outcome = outcome,
            failureCode = commandResult.failureCode
        )
    }

    private fun TransportStatus.supportsDiagnosticExecution(): Boolean {
        val stateReady = when (transport) {
            CommandTransportType.USER_SERVICE -> state == TransportAvailability.READY
            CommandTransportType.DIRECT_PROCESS -> state == TransportAvailability.AVAILABLE
        }
        return stateReady && CommandCapability.PROCESS_EXECUTION in capabilities
    }

    private fun TransportStatus.toSnapshot() = TransportStatusSnapshot(
        transport = transport,
        availability = state,
        capabilities = capabilities,
        detail = detail?.let(LogRedactor::redact)
    )

    private fun TransportAvailability.toFailureCode(): CommandFailureCode = when (this) {
        TransportAvailability.UNSUPPORTED -> CommandFailureCode.TRANSPORT_UNSUPPORTED
        TransportAvailability.UNAVAILABLE,
        TransportAvailability.DEGRADED,
        TransportAvailability.CHECKING,
        TransportAvailability.STARTING -> CommandFailureCode.TRANSPORT_UNAVAILABLE
        TransportAvailability.READY,
        TransportAvailability.AVAILABLE -> CommandFailureCode.TRANSPORT_UNSUPPORTED
    }

    private fun com.honghoaibao.bbooster.core.command.CommandResult.toTestOutcome():
        TransportTestOutcome = when {
            status == CommandExecutionStatus.REJECTED ->
                TransportTestOutcome.COMMAND_REJECTED
            dispatchState == CommandDispatchState.UNKNOWN ->
                TransportTestOutcome.DISPATCH_UNKNOWN
            dispatchState == CommandDispatchState.NOT_DISPATCHED ->
                TransportTestOutcome.NOT_DISPATCHED
            status == CommandExecutionStatus.TIMED_OUT ->
                TransportTestOutcome.DISPATCHED_TIMEOUT
            isSuccess -> TransportTestOutcome.PASSED
            else -> TransportTestOutcome.COMMAND_FAILED
        }

    private fun elapsedMillis(startedNanos: Long, endedNanos: Long): Long =
        ((endedNanos - startedNanos).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
