package com.bbooster.app.shell

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Backend-agnostic way to run whitelisted commands with elevated (shell) privileges.
 * Implementations: [ShizukuExecutor] now; a Wireless-ADB executor later.
 */
interface PrivilegedExecutor {
    val state: StateFlow<ConnectionState>

    /** Never throws (except coroutine cancellation). Returns [CommandResult.Unavailable] if not ready. */
    suspend fun execute(command: ShellCommand, timeout: Duration = DEFAULT_TIMEOUT): CommandResult

    /** Re-reads the backend state (call on resume, after the user returns from the Shizuku app). */
    fun refresh()

    /** Asks the backend for access where it can be requested in-app (Shizuku permission dialog). */
    fun requestAccess()

    companion object {
        val DEFAULT_TIMEOUT: Duration = 10.seconds
    }
}
