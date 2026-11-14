package com.honghoaibao.bbooster.core.shizuku.transport

import com.honghoaibao.bbooster.core.command.CommandDefinition
import com.honghoaibao.bbooster.core.command.CommandResult
import com.honghoaibao.bbooster.core.command.CommandTransportType
import kotlinx.coroutines.flow.StateFlow

/**
 * Common execution contract for Shizuku-backed transports.
 *
 * Implementations accept only definitions issued by CommandRegistry. They must
 * not expose APIs that accept arbitrary shell text from callers.
 */
interface ShizukuTransport {

    val type: CommandTransportType

    val status: StateFlow<TransportStatus>

    /** Re-checks this transport without executing a command. */
    suspend fun refresh(): TransportStatus

    /** Executes one registered definition and returns a normalized result. */
    suspend fun execute(command: CommandDefinition): CommandResult

    /** Releases bindings, processes and streams owned by this transport. */
    suspend fun close()
}
