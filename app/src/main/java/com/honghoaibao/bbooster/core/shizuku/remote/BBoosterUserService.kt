package com.honghoaibao.bbooster.core.shizuku.remote

import android.content.Context
import android.os.Binder
import android.os.Process
import android.util.Log
import androidx.annotation.Keep
import com.honghoaibao.bbooster.BuildConfig
import com.honghoaibao.bbooster.core.command.CommandExecutionMode
import com.honghoaibao.bbooster.core.command.CommandRegistry
import com.honghoaibao.bbooster.core.command.CommandTransportType
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor

/**
 * Shizuku UserService that executes only commands declared in CommandRegistry.
 * Command arguments always come from the registry, never from AIDL caller input.
 */
@Keep
class BBoosterUserService private constructor(
    private val expectedCallerUid: Int?
) : IUserService.Stub() {

    private val runner = RegisteredCommandRunner()
    private val commandExecutor: ThreadPoolExecutor =
        UserServiceCommandExecutorFactory.create(
            maxConcurrentCommands = MAX_CONCURRENT_COMMANDS,
            maxQueuedCommands = MAX_QUEUED_COMMANDS,
            threadNamePrefix = "BBooster-user-command"
        )

    init {
        if (expectedCallerUid == null) {
            Log.w(
                TAG,
                "Caller UID could not be verified; privileged UserService execution is disabled."
            )
        } else {
            Log.i(TAG, "UserService instance created with caller verification enabled.")
        }
    }

    /** Compatibility constructor used by Shizuku servers older than v13. */
    @Keep
    constructor() : this(null)

    /** Shizuku v13+ supplies the owning application context. */
    @Keep
    constructor(context: Context) : this(resolveExpectedCallerUid(context))

    override fun execute(
        actionId: String?,
        timeoutMs: Long,
        callback: ICommandCallback?
    ) {
        if (callback == null) {
            Log.w(TAG, "AIDL execute called without a callback.")
            return
        }

        val callingUid = Binder.getCallingUid()
        if (!UserServiceCallerPolicy.isAuthorized(expectedCallerUid, callingUid)) {
            Log.w(TAG, "Rejected UserService request from an unverified Binder caller.")
            reject(callback, "UserService could not verify the Binder caller.")
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
        if (!UserServiceCallerPolicy.isAuthorizedServiceController(
                serviceUid = Process.myUid(),
                callingUid = Binder.getCallingUid()
            )
        ) {
            Log.w(TAG, "Rejected UserService destroy from a non-Shizuku controller.")
            return
        }

        Log.i(TAG, "Destroy requested by Shizuku.")
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
        const val MAX_QUEUED_COMMANDS = 4

        fun resolveExpectedCallerUid(context: Context): Int? = runCatching {
            if (context.packageName != BuildConfig.APPLICATION_ID) {
                null
            } else {
                context.applicationInfo.uid.takeIf { it >= 0 }
            }
        }.getOrNull()
    }
}
