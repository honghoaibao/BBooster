package com.honghoaibao.bbooster.core.diagnostics.transport

import com.honghoaibao.bbooster.core.command.CommandCapability
import com.honghoaibao.bbooster.core.command.CommandDefinition
import com.honghoaibao.bbooster.core.command.CommandDispatchState
import com.honghoaibao.bbooster.core.command.CommandExecutionStatus
import com.honghoaibao.bbooster.core.command.CommandFailureCode
import com.honghoaibao.bbooster.core.command.CommandPolicyContext
import com.honghoaibao.bbooster.core.command.CommandResult
import com.honghoaibao.bbooster.core.command.CommandTransportType
import com.honghoaibao.bbooster.core.shizuku.transport.ShizukuTransport
import com.honghoaibao.bbooster.core.shizuku.transport.ShizukuTransportManager
import com.honghoaibao.bbooster.core.shizuku.transport.TransportAvailability
import com.honghoaibao.bbooster.core.shizuku.transport.TransportStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportDiagnosticsProviderTest {

    @Test
    fun successfulDirectProcessTestDoesNotExecuteUserService() = runBlocking {
        val user = FakeTransport(CommandTransportType.USER_SERVICE)
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS)
        var clock = 0L
        val manager = manager(user, direct)
        val provider = TransportDiagnosticsProvider(
            manager,
            clockMillis = { 100L },
            nanoTime = {
                clock += 5_000_000L
                clock
            }
        )

        val result = provider.testTransport(CommandTransportType.DIRECT_PROCESS)

        assertEquals(TransportTestOutcome.PASSED, result.outcome)
        assertEquals(CommandTransportType.DIRECT_PROCESS, result.transport)
        assertEquals(0, user.executeCount)
        assertEquals(1, direct.executeCount)
        assertEquals(0, user.refreshCount)
        assertEquals(1, direct.refreshCount)
        assertEquals("caller_identity", result.command?.commandId)
        assertTrue(result.connectionResponseTimeMillis >= 0L)
        assertTrue(result.commandResponseTimeMillis != null)
        assertNull(result.failureCode)
        manager.close()
    }

    @Test
    fun userServiceConnectionFailureDoesNotRunCommandOrFallback() = runBlocking {
        val user = FakeTransport(
            CommandTransportType.USER_SERVICE,
            refreshState = TransportAvailability.DEGRADED
        )
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS)
        val manager = manager(user, direct)
        val provider = TransportDiagnosticsProvider(manager)

        val result = provider.testTransport(CommandTransportType.USER_SERVICE)

        assertEquals(TransportTestOutcome.CONNECTION_FAILED, result.outcome)
        assertNull(result.command)
        assertEquals(CommandFailureCode.TRANSPORT_UNAVAILABLE, result.failureCode)
        assertEquals(0, user.executeCount)
        assertEquals(0, direct.executeCount)
        assertEquals(1, user.refreshCount)
        assertEquals(0, direct.refreshCount)
        manager.close()
    }

    @Test
    fun unknownDispatchIsReportedAndNeverRunsOtherTransport() = runBlocking {
        val user = FakeTransport(
            CommandTransportType.USER_SERVICE,
            commandResult = { command ->
                CommandResult(
                    commandId = command.id,
                    transport = CommandTransportType.USER_SERVICE,
                    status = CommandExecutionStatus.UNKNOWN,
                    dispatchState = CommandDispatchState.UNKNOWN,
                    failureCode = CommandFailureCode.REMOTE_CALL_FAILED
                )
            }
        )
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS)
        val manager = manager(user, direct)
        val provider = TransportDiagnosticsProvider(manager)

        val result = provider.testTransport(CommandTransportType.USER_SERVICE)

        assertEquals(TransportTestOutcome.DISPATCH_UNKNOWN, result.outcome)
        assertEquals(CommandDispatchState.UNKNOWN, result.command?.dispatchState)
        assertEquals(1, user.executeCount)
        assertEquals(0, direct.executeCount)
        manager.close()
    }

    @Test
    fun preDispatchFailureIsReportedWithoutFallback() = runBlocking {
        val user = FakeTransport(
            CommandTransportType.USER_SERVICE,
            commandResult = { command ->
                CommandResult(
                    commandId = command.id,
                    transport = CommandTransportType.USER_SERVICE,
                    status = CommandExecutionStatus.NOT_STARTED,
                    dispatchState = CommandDispatchState.NOT_DISPATCHED,
                    failureCode = CommandFailureCode.BIND_FAILED
                )
            }
        )
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS)
        val manager = manager(user, direct)
        val provider = TransportDiagnosticsProvider(manager)

        val result = provider.testTransport(CommandTransportType.USER_SERVICE)

        assertEquals(TransportTestOutcome.NOT_DISPATCHED, result.outcome)
        assertEquals(CommandDispatchState.NOT_DISPATCHED, result.command?.dispatchState)
        assertEquals(1, user.executeCount)
        assertEquals(0, direct.executeCount)
        manager.close()
    }

    @Test
    fun dispatchedTimeoutIsNotReplayed() = runBlocking {
        val user = FakeTransport(
            CommandTransportType.USER_SERVICE,
            commandResult = { command ->
                CommandResult(
                    commandId = command.id,
                    transport = CommandTransportType.USER_SERVICE,
                    status = CommandExecutionStatus.TIMED_OUT,
                    dispatchState = CommandDispatchState.DISPATCHED,
                    failureCode = CommandFailureCode.COMMAND_TIMED_OUT
                )
            }
        )
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS)
        val manager = manager(user, direct)
        val provider = TransportDiagnosticsProvider(manager)

        val result = provider.testTransport(CommandTransportType.USER_SERVICE)

        assertEquals(TransportTestOutcome.DISPATCHED_TIMEOUT, result.outcome)
        assertEquals(1, user.executeCount)
        assertEquals(0, direct.executeCount)
        manager.close()
    }

    @Test
    fun permissionFailureStopsBeforeTransportExecution() = runBlocking {
        val user = FakeTransport(CommandTransportType.USER_SERVICE)
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS)
        val manager = ShizukuTransportManager(
            userServiceTransport = user,
            directProcessTransport = direct,
            authorizationContextProvider = {
                CommandPolicyContext(
                    binderAvailable = true,
                    shizukuPermissionGranted = false
                )
            }
        )
        val provider = TransportDiagnosticsProvider(manager)

        val result = provider.testTransport(CommandTransportType.DIRECT_PROCESS)

        assertEquals(TransportTestOutcome.COMMAND_REJECTED, result.outcome)
        assertEquals(CommandFailureCode.PERMISSION_REQUIRED, result.failureCode)
        assertEquals(0, direct.executeCount)
        assertEquals(0, user.executeCount)
        manager.close()
    }

    @Test
    fun snapshotKeepsPreferredAndLastCommandTransportSeparate() = runBlocking {
        val user = FakeTransport(CommandTransportType.USER_SERVICE)
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS)
        val manager = manager(user, direct)
        val provider = TransportDiagnosticsProvider(manager)

        provider.testTransport(CommandTransportType.DIRECT_PROCESS)
        val snapshot = provider.snapshot()

        assertEquals(CommandTransportType.USER_SERVICE, snapshot.preferredTransport)
        assertEquals(CommandTransportType.DIRECT_PROCESS, snapshot.lastCommandTransport)
        assertTrue(snapshot.lastDirectProcessCheckAtMillis != null)
        assertNull(snapshot.lastUserServiceCheckAtMillis)
        manager.close()
    }

    private fun manager(
        user: FakeTransport,
        direct: FakeTransport
    ) = ShizukuTransportManager(
        userServiceTransport = user,
        directProcessTransport = direct,
        authorizationContextProvider = {
            CommandPolicyContext(binderAvailable = true, shizukuPermissionGranted = true)
        }
    )

    private class FakeTransport(
        override val type: CommandTransportType,
        private val refreshState: TransportAvailability? = null,
        private val commandResult: ((CommandDefinition) -> CommandResult)? = null
    ) : ShizukuTransport {
        private val mutableStatus = MutableStateFlow(defaultStatus())
        override val status: StateFlow<TransportStatus> = mutableStatus.asStateFlow()
        var refreshCount = 0
            private set
        var executeCount = 0
            private set

        override suspend fun refresh(): TransportStatus {
            refreshCount++
            refreshState?.let { state ->
                mutableStatus.value = mutableStatus.value.copy(state = state)
            }
            return mutableStatus.value
        }

        override suspend fun execute(command: CommandDefinition): CommandResult {
            executeCount++
            return commandResult?.invoke(command) ?: CommandResult(
                commandId = command.id,
                transport = type,
                status = CommandExecutionStatus.COMPLETED,
                dispatchState = CommandDispatchState.DISPATCHED,
                exitCode = 0,
                stdout = "uid=2000(shell)"
            )
        }

        override suspend fun close() = Unit

        private fun defaultStatus() = TransportStatus(
            transport = type,
            state = if (type == CommandTransportType.USER_SERVICE) {
                TransportAvailability.READY
            } else {
                TransportAvailability.AVAILABLE
            },
            capabilities = setOf(CommandCapability.PROCESS_EXECUTION)
        )
    }
}
