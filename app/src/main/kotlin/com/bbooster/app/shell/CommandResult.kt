package com.bbooster.app.shell

/** Outcome of running one [ShellCommand]. Callers must handle every case; nothing throws. */
sealed interface CommandResult {
    data class Success(val stdout: String) : CommandResult

    data class NonZeroExit(val exitCode: Int, val stdout: String, val stderr: String) : CommandResult

    /** The command (or the Binder call carrying it) did not finish in time. */
    data object TimedOut : CommandResult

    /** The privileged side refused the argv (not whitelisted). Indicates a bug, not a user error. */
    data class Rejected(val reason: String) : CommandResult

    /** No privileged connection right now (Shizuku off, permission missing, ...). */
    data class Unavailable(val reason: String) : CommandResult

    /** Remote or transport failure (dead binder, security exception, process start failure). */
    data class Error(val message: String) : CommandResult
}
