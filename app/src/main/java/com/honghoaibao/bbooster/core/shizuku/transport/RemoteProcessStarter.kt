package com.honghoaibao.bbooster.core.shizuku.transport

import com.honghoaibao.bbooster.core.command.CommandDefinition
import com.honghoaibao.bbooster.core.command.CommandDispatchState
import com.honghoaibao.bbooster.core.command.CommandFailureCode

internal enum class ProcessStarterAvailability {
    AVAILABLE,
    UNSUPPORTED
}

internal interface RemoteProcessStarter {
    fun checkAvailability(): ProcessStarterAvailability

    @Throws(ProcessStartException::class)
    fun start(command: CommandDefinition): Process
}

internal class ProcessStartException(
    val dispatchState: CommandDispatchState,
    val failureCode: CommandFailureCode,
    val detail: String,
    cause: Throwable? = null
) : Exception(detail, cause)
