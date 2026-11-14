package com.honghoaibao.bbooster.core.shizuku.remote

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.honghoaibao.bbooster.core.command.CommandExecutionMode
import com.honghoaibao.bbooster.core.command.CommandRegistry
import com.honghoaibao.bbooster.core.command.CommandTransportType
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Shizuku UserService that executes only commands declared in CommandRegistry.
 * Command arguments always come from the registry, never from AIDL caller input.
 */
@Keep
class BBoosterUserService : IUserService.Stub {

    private val runner = RegisteredCommandRunner()
    private val commandExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_COMMANDS) { runnable ->
        Thread(runnable, "BBooster-user-command").apply { isDaemon = true }
    }

    constructor() : super() {
        Log.i(TAG, "UserService instance created.")
    }

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    override fun execute(
        actionId: String?,
        timeoutMs: Long,
        callback: ICommandCallback?
    ) {
        if (callback == null) {
            Log.w(TAG, "AIDL execute called without a callback.")
            return
        }

        val definition = actionId?.let { CommandRegistry.find(it) }
        if (definition == null ||
            CommandTransportType.USER_SERVICE !in definition.supportedTransports ||
            definition.executionMode != CommandExecutionMode.ONE_SHOT ||
            definition.requiresConfirmation
        ) {
            reject(callback, "Command is not present in the UserService allowlist.")
            return
        }

        if (timeoutMs <= 0L) {
            reject(callback, "Command timeout must be positive.")
            return
        }

        try {
            commandExecutor.execute {
                val result = runner.execute(definition, timeoutMs)
                try {
                    if (!result.started) {
                        callback.onRejected(
                            result.rejectionReason ?: "UserService could not start the command."
                        )
                    } else {
                        callback.onCompleted(
                            result.exitCode,
                            result.stdout,
                            result.stderr,
                            result.timedOut,
                            result.outputTruncated
                        )
                    }
                } catch (error: Exception) {
                    Log.w(TAG, "Unable to deliver command callback.", error)
                }
            }
        } catch (_: RejectedExecutionException) {
            reject(callback, "UserService is shutting down or is at its command capacity.")
        }
    }

    override fun destroy() {
        Log.i(TAG, "Destroy requested.")
        commandExecutor.shutdownNow()
        runner.close()
        System.exit(0)
    }

    private fun reject(callback: ICommandCallback, reason: String) {
        try {
            callback.onRejected(reason)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to deliver rejection callback.", error)
        }
    }

    private companion object {
        const val TAG = "BBoosterUserService"
        const val MAX_CONCURRENT_COMMANDS = 2
    }
}
