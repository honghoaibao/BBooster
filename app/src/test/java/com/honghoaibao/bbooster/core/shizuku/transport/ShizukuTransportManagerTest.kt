package com.honghoaibao.bbooster.core.shizuku.transport

import com.honghoaibao.bbooster.core.command.CommandDefinition
import com.honghoaibao.bbooster.core.command.CommandDispatchState
import com.honghoaibao.bbooster.core.command.CommandExecutionStatus
import com.honghoaibao.bbooster.core.command.CommandFailureCode
import com.honghoaibao.bbooster.core.command.CommandPolicyContext
import com.honghoaibao.bbooster.core.command.CommandRegistry
import com.honghoaibao.bbooster.core.command.CommandResult
import com.honghoaibao.bbooster.core.command.CommandTransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuTransportManagerTest {

    @Test
    fun userServiceIsPreferredAndDirectProcessIsNotStartedAfterSuccess() = runBlocking {
        val order = mutableListOf<CommandTransportType>()
        val user = FakeTransport(CommandTransportType.USER_SERVICE) { command ->
            order += CommandTransportType.USER_SERVICE
            success(command, CommandTransportType.USER_SERVICE)
        }
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS) { command ->
            order += CommandTransportType.DIRECT_PROCESS
            success(command, CommandTransportType.DIRECT_PROCESS)
        }
        val manager = manager(user, direct)

        val result = manager.execute("caller_identity")

        assertTrue(result.isSuccess)
        assertEquals(CommandTransportType.USER_SERVICE, result.transport)
        assertEquals(listOf(CommandTransportType.USER_SERVICE), order)
        assertEquals(0, direct.executeCount)
        assertEquals(CommandTransportType.USER_SERVICE, manager.state.value.activeTransport)
        manager.close()
    }

    @Test
    fun safeNotDispatchedFailureFallsBackToDirectProcess() = runBlocking {
        val order = mutableListOf<CommandTransportType>()
        val user = FakeTransport(CommandTransportType.USER_SERVICE) { command ->
            order += CommandTransportType.USER_SERVICE
            notDispatched(command, CommandTransportType.USER_SERVICE)
        }
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS) { command ->
            order += CommandTransportType.DIRECT_PROCESS
            success(command, CommandTransportType.DIRECT_PROCESS)
        }
        val manager = manager(user, direct)

        val result = manager.execute("caller_identity")

        assertTrue(result.isSuccess)
        assertEquals(CommandTransportType.DIRECT_PROCESS, result.transport)
        assertEquals(
            listOf(CommandTransportType.USER_SERVICE, CommandTransportType.DIRECT_PROCESS),
            order
        )
        assertTrue(manager.state.value.lastFallbackReason.orEmpty().isNotBlank())
        assertTrue(manager.state.value.userServiceRetryAtMs != null)
        manager.close()
    }

    @Test
    fun unknownDispatchOutcomeNeverFallsBack() = runBlocking {
        val user = FakeTransport(CommandTransportType.USER_SERVICE) { command ->
            CommandResult(
                commandId = command.id,
                transport = CommandTransportType.USER_SERVICE,
                status = CommandExecutionStatus.UNKNOWN,
                dispatchState = CommandDispatchState.UNKNOWN,
                failureCode = CommandFailureCode.REMOTE_CALL_FAILED
            )
        }
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS) { command ->
            success(command, CommandTransportType.DIRECT_PROCESS)
        }
        val manager = manager(user, direct)

        val result = manager.execute("caller_identity")

        assertEquals(CommandExecutionStatus.UNKNOWN, result.status)
        assertEquals(CommandDispatchState.UNKNOWN, result.dispatchState)
        assertEquals(0, direct.executeCount)
        manager.close()
    }

    @Test
    fun dispatchedTimeoutNeverFallsBack() = runBlocking {
        val user = FakeTransport(CommandTransportType.USER_SERVICE) { command ->
            CommandResult(
                commandId = command.id,
                transport = CommandTransportType.USER_SERVICE,
                status = CommandExecutionStatus.TIMED_OUT,
                dispatchState = CommandDispatchState.DISPATCHED,
                failureCode = CommandFailureCode.COMMAND_TIMED_OUT
            )
        }
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS) { command ->
            success(command, CommandTransportType.DIRECT_PROCESS)
        }
        val manager = manager(user, direct)

        val result = manager.execute("caller_identity")

        assertEquals(CommandExecutionStatus.TIMED_OUT, result.status)
        assertEquals(0, direct.executeCount)
        manager.close()
    }

    @Test
    fun missingPermissionRejectsBeforeEitherTransportRuns() = runBlocking {
        val user = FakeTransport(CommandTransportType.USER_SERVICE) { command ->
            success(command, CommandTransportType.USER_SERVICE)
        }
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS) { command ->
            success(command, CommandTransportType.DIRECT_PROCESS)
        }
        val manager = ShizukuTransportManager(
            userServiceTransport = user,
            directProcessTransport = direct,
            authorizationContextProvider = {
                CommandPolicyContext(binderAvailable = true, shizukuPermissionGranted = false)
            }
        )

        val result = manager.execute("caller_identity")

        assertEquals(CommandExecutionStatus.REJECTED, result.status)
        assertEquals(CommandFailureCode.PERMISSION_REQUIRED, result.failureCode)
        assertNull(result.transport)
        assertEquals(0, user.executeCount)
        assertEquals(0, direct.executeCount)
        manager.close()
    }

    @Test
    fun userServiceIsRetriedAfterAutomaticCooldown() = runBlocking {
        var now = 1_000L
        val user = FakeTransport(
            type = CommandTransportType.USER_SERVICE,
            handler = { command ->
                success(command, CommandTransportType.USER_SERVICE)
            }
        )
        user.setAvailability(TransportAvailability.DEGRADED)
        // Script a first pre-dispatch failure followed by recovery success.
        user.results.add(
            notDispatched(
                requireNotNull(CommandRegistry.find("caller_identity")),
                CommandTransportType.USER_SERVICE
            )
        )
        user.results.add(
            success(
                requireNotNull(CommandRegistry.find("caller_identity")),
                CommandTransportType.USER_SERVICE
            )
        )
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS) { command ->
            success(command, CommandTransportType.DIRECT_PROCESS)
        }
        val manager = ShizukuTransportManager(
            userServiceTransport = user,
            directProcessTransport = direct,
            authorizationContextProvider = { grantedContext() },
            clockMs = { now },
            userServiceRetryDelayMs = 5_000L
        )

        val first = manager.execute("caller_identity")
        now = 2_000L
        val second = manager.execute("caller_identity")
        now = 6_000L
        user.setAvailability(TransportAvailability.READY)
        val third = manager.execute("caller_identity")

        assertEquals(CommandTransportType.DIRECT_PROCESS, first.transport)
        assertEquals(CommandTransportType.DIRECT_PROCESS, second.transport)
        assertEquals(CommandTransportType.USER_SERVICE, third.transport)
        assertEquals(2, user.executeCount)
        assertEquals(2, direct.executeCount)
        assertNull(manager.state.value.userServiceRetryAtMs)
        manager.close()
    }

    @Test
    fun knownUnsupportedUserServiceUsesDirectProcessDuringCooldown() = runBlocking {
        val user = FakeTransport(CommandTransportType.USER_SERVICE) { command ->
            success(command, CommandTransportType.USER_SERVICE)
        }
        user.setAvailability(TransportAvailability.UNSUPPORTED)
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS) { command ->
            success(command, CommandTransportType.DIRECT_PROCESS)
        }
        val manager = manager(user, direct)

        val refreshed = manager.refresh()
        val result = manager.execute("caller_identity")

        assertEquals(TransportAvailability.UNSUPPORTED, refreshed.userService.state)
        assertTrue(manager.state.value.userServiceRetryAtMs != null)
        assertEquals(CommandTransportType.DIRECT_PROCESS, result.transport)
        assertEquals(0, user.executeCount)
        assertEquals(1, direct.executeCount)
        manager.close()
    }

    @Test
    fun transportStatusChangesArePublishedByManager() = runBlocking {
        val user = FakeTransport(CommandTransportType.USER_SERVICE) { command ->
            success(command, CommandTransportType.USER_SERVICE)
        }
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS) { command ->
            success(command, CommandTransportType.DIRECT_PROCESS)
        }
        val manager = manager(user, direct)

        user.setAvailability(TransportAvailability.DEGRADED)
        val observed = withTimeout(2_000L) {
            manager.state.first {
                it.userService.state == TransportAvailability.DEGRADED
            }
        }

        assertEquals(TransportAvailability.DEGRADED, observed.userService.state)
        manager.close()
    }

    @Test
    fun lifecycleRefreshDoesNotResetUserServiceCooldown() = runBlocking {
        var now = 10_000L
        val user = FakeTransport(CommandTransportType.USER_SERVICE) { command ->
            success(command, CommandTransportType.USER_SERVICE)
        }
        user.setAvailability(TransportAvailability.DEGRADED)
        user.results.add(
            notDispatched(
                requireNotNull(CommandRegistry.find("caller_identity")),
                CommandTransportType.USER_SERVICE
            )
        )
        user.results.add(
            success(
                requireNotNull(CommandRegistry.find("caller_identity")),
                CommandTransportType.USER_SERVICE
            )
        )
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS) { command ->
            success(command, CommandTransportType.DIRECT_PROCESS)
        }
        val manager = ShizukuTransportManager(
            userServiceTransport = user,
            directProcessTransport = direct,
            authorizationContextProvider = { grantedContext() },
            clockMs = { now },
            userServiceRetryDelayMs = 5_000L
        )

        val first = manager.execute("caller_identity")
        now = 11_000L
        manager.refresh(forceUserServiceProbe = false)
        val second = manager.execute("caller_identity")
        now = 16_000L
        val third = manager.execute("caller_identity")

        assertEquals(CommandTransportType.DIRECT_PROCESS, first.transport)
        assertEquals(CommandTransportType.DIRECT_PROCESS, second.transport)
        assertEquals(CommandTransportType.USER_SERVICE, third.transport)
        assertEquals(2, user.executeCount)
        assertEquals(2, direct.executeCount)
        manager.close()
    }

    @Test
    fun unknownCommandIsRejectedWithoutCallingTransports() = runBlocking {
        val user = FakeTransport(CommandTransportType.USER_SERVICE) { command ->
            success(command, CommandTransportType.USER_SERVICE)
        }
        val direct = FakeTransport(CommandTransportType.DIRECT_PROCESS) { command ->
            success(command, CommandTransportType.DIRECT_PROCESS)
        }
        val manager = manager(user, direct)

        val result = manager.execute("settings put global test 1")

        assertEquals(CommandExecutionStatus.REJECTED, result.status)
        assertEquals(CommandFailureCode.COMMAND_REJECTED, result.failureCode)
        assertEquals(0, user.executeCount)
        assertEquals(0, direct.executeCount)
        manager.close()
    }

    private fun manager(
        user: FakeTransport,
        direct: FakeTransport
    ) = ShizukuTransportManager(
        userServiceTransport = user,
        directProcessTransport = direct,
        authorizationContextProvider = { grantedContext() }
    )

    private fun grantedContext() = CommandPolicyContext(
        binderAvailable = true,
        shizukuPermissionGranted = true
    )

    private fun success(
        command: CommandDefinition,
        transport: CommandTransportType
    ) = CommandResult(
        commandId = command.id,
        transport = transport,
        status = CommandExecutionStatus.COMPLETED,
        dispatchState = CommandDispatchState.DISPATCHED,
        exitCode = 0
    )

    private fun notDispatched(
        command: CommandDefinition,
        transport: CommandTransportType
    ) = CommandResult(
        commandId = command.id,
        transport = transport,
        status = CommandExecutionStatus.NOT_STARTED,
        dispatchState = CommandDispatchState.NOT_DISPATCHED,
        failureCode = CommandFailureCode.BIND_FAILED,
        failureDetail = "Simulated pre-dispatch bind failure."
    )


    private class FakeTransport(
        override val type: CommandTransportType,
        private val handler: (CommandDefinition) -> CommandResult
    ) : ShizukuTransport {
        private val mutableStatus = MutableStateFlow(
            TransportStatus(
                transport = type,
                state = if (type == CommandTransportType.USER_SERVICE) {
                    TransportAvailability.READY
                } else {
                    TransportAvailability.AVAILABLE
                }
            )
        )
        override val status: StateFlow<TransportStatus> = mutableStatus.asStateFlow()
        var executeCount: Int = 0
            private set
        val results = mutableListOf<CommandResult>()

        fun setAvailability(availability: TransportAvailability) {
            mutableStatus.value = mutableStatus.value.copy(state = availability)
        }

        override suspend fun refresh(): TransportStatus = mutableStatus.value

        override suspend fun execute(command: CommandDefinition): CommandResult {
            executeCount++
            return if (results.isNotEmpty()) results.removeAt(0) else handler(command)
        }

        override suspend fun close() {
            mutableStatus.value = mutableStatus.value.copy(
                state = TransportAvailability.UNAVAILABLE
            )
        }
    }
}
