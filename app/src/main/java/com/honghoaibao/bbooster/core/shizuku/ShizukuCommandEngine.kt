package com.honghoaibao.bbooster.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.honghoaibao.bbooster.BuildConfig
import com.honghoaibao.bbooster.core.command.CommandAction
import com.honghoaibao.bbooster.core.shizuku.remote.BBoosterUserService
import com.honghoaibao.bbooster.core.shizuku.remote.ICommandCallback
import com.honghoaibao.bbooster.core.shizuku.remote.IUserService
import java.util.concurrent.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Binds the app to the isolated Shizuku UserService and runs fixed diagnostics.
 */
class ShizukuCommandEngine(context: Context) {

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.applicationContext, BBoosterUserService::class.java)
    )
        .tag(USER_SERVICE_TAG)
        .version(USER_SERVICE_VERSION)
        .processNameSuffix(USER_SERVICE_PROCESS_SUFFIX)
        .debuggable(BuildConfig.DEBUG)
        .daemon(false)

    private val mutableState = MutableStateFlow(CommandEngineState())
    val state: StateFlow<CommandEngineState> = mutableState.asStateFlow()

    private val serviceFlow = MutableStateFlow<IUserService?>(null)
    private var remoteService: IUserService? = null
    private var bindingRequested = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val connectedService = service?.let { IUserService.Stub.asInterface(it) }
            remoteService = connectedService
            serviceFlow.value = connectedService
            mutableState.value = mutableState.value.copy(
                serviceStatus = if (connectedService != null) {
                    CommandServiceStatus.READY
                } else {
                    CommandServiceStatus.ERROR
                },
                error = if (connectedService == null) {
                    CommandEngineError.SERVICE_DISCONNECTED
                } else {
                    null
                }
            )
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteService = null
            serviceFlow.value = null
            bindingRequested = false
            mutableState.value = mutableState.value.copy(
                serviceStatus = CommandServiceStatus.DISCONNECTED,
                isRunning = false,
                error = CommandEngineError.SERVICE_DISCONNECTED
            )
        }
    }

    suspend fun runDiagnostics() {
        mutableState.value = mutableState.value.copy(
            isRunning = true,
            lastResult = null,
            error = null
        )

        try {
            val remote = awaitService()
            val actions = listOf(
                CommandAction.CALLER_IDENTITY,
                CommandAction.ANDROID_RELEASE,
                CommandAction.ANDROID_SDK
            )
            val results = actions.map { action -> executeAction(remote, action) }

            val combined = CommandBatchResult(
                exitCode = results.lastOrNull()?.exitCode ?: 0,
                stdout = results.mapIndexed { index, result ->
                    val label = actions[index].name.replace('_', ' ')
                    "${label}\n${result.stdout.trim()}"
                }.joinToString("\n\n"),
                stderr = results.mapNotNull { it.stderr.trim().takeIf(String::isNotEmpty) }
                    .joinToString("\n"),
                timedOut = results.any { it.timedOut }
            )

            mutableState.value = mutableState.value.copy(
                serviceStatus = CommandServiceStatus.READY,
                isRunning = false,
                lastResult = combined,
                error = null
            )
        } catch (_: SecurityException) {
            mutableState.value = mutableState.value.copy(
                serviceStatus = CommandServiceStatus.ERROR,
                isRunning = false,
                error = CommandEngineError.PERMISSION_REQUIRED
            )
        } catch (_: IllegalStateException) {
            mutableState.value = mutableState.value.copy(
                serviceStatus = CommandServiceStatus.ERROR,
                isRunning = false,
                error = CommandEngineError.SHIZUKU_UNAVAILABLE
            )
        } catch (_: TimeoutCancellationException) {
            mutableState.value = mutableState.value.copy(
                serviceStatus = CommandServiceStatus.ERROR,
                isRunning = false,
                error = CommandEngineError.BIND_TIMEOUT
            )
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(isRunning = false)
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(
                serviceStatus = CommandServiceStatus.ERROR,
                isRunning = false,
                error = CommandEngineError.REMOTE_CALL_FAILED
            )
        }
    }

    fun unbind() {
        if (bindingRequested) {
            try {
                if (Shizuku.pingBinder()) {
                    Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
                }
            } catch (_: Exception) {
                // Shizuku may already be stopped.
            }
        }

        bindingRequested = false
        remoteService = null
        serviceFlow.value = null
        mutableState.value = mutableState.value.copy(
            serviceStatus = CommandServiceStatus.DISCONNECTED,
            isRunning = false
        )
    }

    private suspend fun awaitService(): IUserService {
        remoteService?.let { return it }

        if (!Shizuku.pingBinder()) {
            throw IllegalStateException("Shizuku Binder is unavailable.")
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Shizuku permission is not granted.")
        }

        mutableState.value = mutableState.value.copy(
            serviceStatus = CommandServiceStatus.BINDING,
            error = null
        )

        if (!bindingRequested) {
            bindingRequested = true
            try {
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            } catch (error: Exception) {
                bindingRequested = false
                throw error
            }
        }

        return withTimeout(BIND_TIMEOUT_MS) {
            serviceFlow.filterNotNull().first()
        }
    }

    private suspend fun executeAction(
        service: IUserService,
        action: CommandAction
    ): CommandBatchResult = suspendCancellableCoroutine { continuation ->
        try {
            service.execute(
                action.id,
                COMMAND_TIMEOUT_MS,
                object : ICommandCallback.Stub() {
                    override fun onCompleted(
                        exitCode: Int,
                        stdout: String?,
                        stderr: String?,
                        timedOut: Boolean
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(
                                CommandBatchResult(
                                    exitCode = exitCode,
                                    stdout = stdout.orEmpty(),
                                    stderr = stderr.orEmpty(),
                                    timedOut = timedOut
                                )
                            )
                        }
                    }

                    override fun onRejected(reason: String?) {
                        if (continuation.isActive) {
                            continuation.resume(
                                CommandBatchResult(
                                    exitCode = REJECTED_EXIT_CODE,
                                    stdout = "",
                                    stderr = reason.orEmpty(),
                                    timedOut = false
                                )
                            )
                        }
                    }
                }
            )
        } catch (error: Exception) {
            if (continuation.isActive) {
                continuation.resume(
                    CommandBatchResult(
                        exitCode = REMOTE_ERROR_EXIT_CODE,
                        stdout = "",
                        stderr = error.message.orEmpty(),
                        timedOut = false
                    )
                )
            }
        }
    }

    private companion object {
        const val USER_SERVICE_TAG = "bbooster-readonly-command-service"
        const val USER_SERVICE_VERSION = 1
        const val USER_SERVICE_PROCESS_SUFFIX = "bbooster"
        const val BIND_TIMEOUT_MS = 12_000L
        const val COMMAND_TIMEOUT_MS = 8_000L
        const val REMOTE_ERROR_EXIT_CODE = -1
        const val REJECTED_EXIT_CODE = -3
    }
}
