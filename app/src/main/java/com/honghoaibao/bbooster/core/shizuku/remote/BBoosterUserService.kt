package com.honghoaibao.bbooster.core.shizuku.remote

import android.content.Context
import androidx.annotation.Keep
import com.honghoaibao.bbooster.core.command.CommandAction
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Runs inside Shizuku's isolated shell/root process.
 * Only predefined read-only diagnostic actions are accepted.
 */
@Keep
class BBoosterUserService : IUserService.Stub {

    private val commandExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val streamExecutor: ExecutorService = Executors.newCachedThreadPool()

    constructor() : super()

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    override fun execute(
        actionId: String?,
        timeoutMs: Long,
        callback: ICommandCallback?
    ) {
        if (callback == null) return

        val action = actionId?.let(CommandAction::fromId)
        if (action == null) {
            notifyRejected(callback, "Unsupported diagnostic action.")
            return
        }

        val safeTimeoutMs = timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        commandExecutor.execute {
            executeReadOnlyAction(action, safeTimeoutMs, callback)
        }
    }

    override fun destroy() {
        commandExecutor.shutdownNow()
        streamExecutor.shutdownNow()
        System.exit(0)
    }

    private fun executeReadOnlyAction(
        action: CommandAction,
        timeoutMs: Long,
        callback: ICommandCallback
    ) {
        var stdoutFuture: Future<String>? = null
        var stderrFuture: Future<String>? = null
        var runningProcess: Process? = null

        try {
            // Fixed argument arrays prevent shell interpolation and command injection.
            val process = ProcessBuilder(action.arguments).start()
            runningProcess = process

            stdoutFuture = streamExecutor.submit(Callable {
                readStream(process.inputStream)
            })
            stderrFuture = streamExecutor.submit(Callable {
                readStream(process.errorStream)
            })

            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                process.waitFor(PROCESS_CLEANUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }

            val stdout = collectStream(stdoutFuture)
            val stderr = collectStream(stderrFuture)
            val exitCode = if (completed) process.exitValue() else TIMEOUT_EXIT_CODE

            notifyCompleted(callback, exitCode, stdout, stderr, !completed)
        } catch (error: Exception) {
            runningProcess?.destroyForcibly()
            stdoutFuture?.cancel(true)
            stderrFuture?.cancel(true)
            notifyCompleted(
                callback,
                EXECUTION_ERROR_EXIT_CODE,
                "",
                error.message ?: error.javaClass.simpleName,
                false
            )
        }
    }

    private fun readStream(input: InputStream): String {
        return BufferedReader(
            InputStreamReader(input, StandardCharsets.UTF_8)
        ).use { reader -> reader.readText() }
    }

    private fun collectStream(future: Future<String>?): String {
        if (future == null) return ""
        return try {
            future.get(STREAM_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            future.cancel(true)
            ""
        }
    }

    private fun notifyCompleted(
        callback: ICommandCallback,
        exitCode: Int,
        stdout: String,
        stderr: String,
        timedOut: Boolean
    ) {
        try {
            callback.onCompleted(exitCode, stdout, stderr, timedOut)
        } catch (_: Exception) {
            // The caller may have disconnected.
        }
    }

    private fun notifyRejected(callback: ICommandCallback, reason: String) {
        try {
            callback.onRejected(reason)
        } catch (_: Exception) {
            // The caller may have disconnected.
        }
    }

    private companion object {
        const val MIN_TIMEOUT_MS = 1_000L
        const val MAX_TIMEOUT_MS = 15_000L
        const val STREAM_READ_TIMEOUT_MS = 2_000L
        const val PROCESS_CLEANUP_TIMEOUT_MS = 1_000L
        const val TIMEOUT_EXIT_CODE = -2
        const val EXECUTION_ERROR_EXIT_CODE = -1
    }
}
