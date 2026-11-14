package com.honghoaibao.bbooster.core.command

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandResultTest {

    @Test
    fun successRequiresCompletedStatusAndZeroExitCode() {
        assertTrue(
            result(
                status = CommandExecutionStatus.COMPLETED,
                dispatchState = CommandDispatchState.DISPATCHED,
                exitCode = 0
            ).isSuccess
        )

        assertFalse(
            result(
                status = CommandExecutionStatus.FAILED,
                dispatchState = CommandDispatchState.DISPATCHED,
                exitCode = 0
            ).isSuccess
        )
        assertFalse(
            result(
                status = CommandExecutionStatus.COMPLETED,
                dispatchState = CommandDispatchState.DISPATCHED,
                exitCode = 1
            ).isSuccess
        )
    }

    @Test
    fun fallbackIsAllowedOnlyWhenCommandWasNotDispatched() {
        assertTrue(
            result(
                status = CommandExecutionStatus.NOT_STARTED,
                dispatchState = CommandDispatchState.NOT_DISPATCHED,
                exitCode = null
            ).canSafelyFallback
        )

        assertFalse(
            result(
                status = CommandExecutionStatus.UNKNOWN,
                dispatchState = CommandDispatchState.UNKNOWN,
                exitCode = null
            ).canSafelyFallback
        )
        assertFalse(
            result(
                status = CommandExecutionStatus.TIMED_OUT,
                dispatchState = CommandDispatchState.DISPATCHED,
                exitCode = null
            ).canSafelyFallback
        )
    }

    private fun result(
        status: CommandExecutionStatus,
        dispatchState: CommandDispatchState,
        exitCode: Int?
    ) = CommandResult(
        commandId = "caller_identity",
        transport = CommandTransportType.DIRECT_PROCESS,
        status = status,
        dispatchState = dispatchState,
        exitCode = exitCode
    )
}
