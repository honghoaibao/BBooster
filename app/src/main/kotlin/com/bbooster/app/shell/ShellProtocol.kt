package com.bbooster.app.shell

import android.os.Bundle

/** Bundle format exchanged between [ShizukuExecutor] (app process) and [ShellService] (shell process). */
object ShellProtocol {
    const val MIN_TIMEOUT_MS = 100L
    const val MAX_TIMEOUT_MS = 600_000L

    /** Output per stream is capped so the Binder transaction (about 1 MB) is never exceeded. */
    const val MAX_OUTPUT_BYTES = 256 * 1024

    private const val KEY_STATUS = "status"
    private const val KEY_EXIT_CODE = "exitCode"
    private const val KEY_STDOUT = "stdout"
    private const val KEY_STDERR = "stderr"
    private const val KEY_MESSAGE = "message"

    private const val STATUS_COMPLETED = "completed"
    private const val STATUS_TIMEOUT = "timeout"
    private const val STATUS_REJECTED = "rejected"
    private const val STATUS_ERROR = "error"

    fun completed(exitCode: Int, stdout: String, stderr: String): Bundle = Bundle().apply {
        putString(KEY_STATUS, STATUS_COMPLETED)
        putInt(KEY_EXIT_CODE, exitCode)
        putString(KEY_STDOUT, stdout)
        putString(KEY_STDERR, stderr)
    }

    fun timeout(): Bundle = Bundle().apply { putString(KEY_STATUS, STATUS_TIMEOUT) }

    fun rejected(reason: String): Bundle = Bundle().apply {
        putString(KEY_STATUS, STATUS_REJECTED)
        putString(KEY_MESSAGE, reason)
    }

    fun error(message: String): Bundle = Bundle().apply {
        putString(KEY_STATUS, STATUS_ERROR)
        putString(KEY_MESSAGE, message)
    }

    fun toResult(bundle: Bundle): CommandResult = when (bundle.getString(KEY_STATUS)) {
        STATUS_COMPLETED -> {
            val exit = bundle.getInt(KEY_EXIT_CODE, -1)
            val out = bundle.getString(KEY_STDOUT).orEmpty()
            if (exit == 0) {
                CommandResult.Success(out)
            } else {
                CommandResult.NonZeroExit(exit, out, bundle.getString(KEY_STDERR).orEmpty())
            }
        }
        STATUS_TIMEOUT -> CommandResult.TimedOut
        STATUS_REJECTED -> CommandResult.Rejected(bundle.getString(KEY_MESSAGE).orEmpty())
        else -> CommandResult.Error(bundle.getString(KEY_MESSAGE) ?: "Malformed service response")
    }
}
