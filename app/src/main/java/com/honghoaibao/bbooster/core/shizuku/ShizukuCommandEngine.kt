package com.honghoaibao.bbooster.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.honghoaibao.bbooster.BuildConfig
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
 * Legacy dashboard bridge retained until UnifiedCommandEngine integration.
 * New command execution should use UserServiceTransport.
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

    private sealed interface BindingOutcome {
        data class Connected(val service: IUserService) : BindingOutcome
        data class Failed(val detail: String) : BindingOutcome
    }

    private val bindingOutcomeFlow = MutableStateFlow<BindingOutcome?>(null)
    private var remoteService: IUserService? = null
    private var bindingRequested = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(LOG_TAG, "UserService onServiceConnected; binderPresent=${service != null}")
            val connectedService = service?.let { IUserService.Stub.asInterface(it) }
            remoteService = connectedService
            bindingOutcomeFlow.value = if (connectedService != null) {
                BindingOutcome.Connected(connectedService)
            } else {
                BindingOutcome.Failed("Shizuku delivered a null UserService binder.")
            }
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
            Log.w(LOG_TAG, "UserService disconnected.")
            remoteService = null
            bindingOutcomeFlow.value = BindingOutcome.Failed("UserService disconnected before diagnostics completed.")
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
            error = null,
            errorDetail = null
        )

        try {
            val remote = awaitService()
            val combined = executeAction(remote, DIAGNOSTIC_ACTION_ID)

            mutableState.value = mutableState.value.copy(
                serviceStatus = CommandServiceStatus.READY,
                isRunning = false,
                lastResult = combined,
                error = null
            )
        } catch (_: ShizukuBinderUnavailableException) {
            mutableState.value = mutableState.value.copy(
                serviceStatus = CommandServiceStatus.ERROR,
                isRunning = false,
                error = CommandEngineError.SHIZUKU_UNAVAILABLE,
                errorDetail = null
            )
        } catch (bindError: UserServiceBindException) {
            mutableState.value = mutableState.value.copy(
                serviceStatus = CommandServiceStatus.ERROR,
                isRunning = false,
                error = CommandEngineError.BIND_FAILED,
                errorDetail = bindError.cause?.let { cause ->
                    "${cause.javaClass.simpleName}: ${cause.message.orEmpty()}"
                } ?: bindError.message
            )
        } catch (_: SecurityException) {
            mutableState.value = mutableState.value.copy(
                serviceStatus = CommandServiceStatus.ERROR,
                isRunning = false,
                error = CommandEngineError.PERMISSION_REQUIRED,
                errorDetail = null
            )
        } catch (_: TimeoutCancellationException) {
            resetUserServiceBinding()
            mutableState.value = mutableState.value.copy(
                serviceStatus = CommandServiceStatus.ERROR,
                isRunning = false,
                error = CommandEngineError.BIND_TIMEOUT,
                errorDetail = "No UserService connection callback within ${BIND_TIMEOUT_MS} ms; pending bind was cleared."
            )
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(isRunning = false)
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(
                serviceStatus = CommandServiceStatus.ERROR,
                isRunning = false,
                error = CommandEngineError.REMOTE_CALL_FAILED,
                errorDetail = error.javaClass.simpleName + ": " + error.message.orEmpty()
            )
        }
    }

    fun unbind() {
        resetUserServiceBinding()
        mutableState.value = mutableState.value.copy(
            serviceStatus = CommandServiceStatus.DISCONNECTED,
            isRunning = false,
            errorDetail = null
        )
    }

    private fun resetUserServiceBinding() {
        if (bindingRequested) {
            try {
                if (Shizuku.pingBinder()) {
                    Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
                }
            } catch (_: Exception) {
                // Shizuku may already be stopped or the bind may not have completed.
            }
        }
        bindingRequested = false
        remoteService = null
        bindingOutcomeFlow.value = null
    }

    private suspend fun awaitService(): IUserService {
        remoteService?.let { return it }

        if (!Shizuku.pingBinder()) throw ShizukuBinderUnavailableException()
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Shizuku permission is not granted.")
        }

        mutableState.value = mutableState.value.copy(
            serviceStatus = CommandServiceStatus.BINDING,
            error = null,
            errorDetail = null
        )
        bindingOutcomeFlow.value = null

        if (!bindingRequested) {
            bindingRequested = true
            try {
                Log.i(LOG_TAG, "Requesting Shizuku UserService bind.")
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            } catch (error: Exception) {
                bindingRequested = false
                throw UserServiceBindException(error)
            }
        }

        return when (val outcome = kotlinx.coroutines.withTimeout(BIND_TIMEOUT_MS) {
            bindingOutcomeFlow.filterNotNull().first()
        }) {
            is BindingOutcome.Connected -> outcome.service
            is BindingOutcome.Failed -> throw UserServiceBindException(
                IllegalStateException(outcome.detail)
            )
        }
    }

    private suspend fun executeAction(
        service: IUserService,
        actionId: String
    ): CommandBatchResult = kotlinx.coroutines.withTimeoutOrNull(COMMAND_TIMEOUT_MS + CALLBACK_GRACE_MS) {
        suspendCancellableCoroutine { continuation ->
            try {
                service.execute(
                    actionId,
                    COMMAND_TIMEOUT_MS,
                    object : ICommandCallback.Stub() {
                        override fun onCompleted(
                            exitCode: Int,
                            stdout: String?,
                            stderr: String?,
                            timedOut: Boolean,
                            outputTruncated: Boolean
                        ) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    CommandBatchResult(
                                        exitCode = exitCode,
                                        stdout = stdout.orEmpty(),
                                        stderr = stderr.orEmpty(),
                                        timedOut = timedOut,
                                        outputTruncated = outputTruncated
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
    } ?: CommandBatchResult(
        exitCode = REMOTE_ERROR_EXIT_CODE,
        stdout = "",
        stderr = "UserService did not return an AIDL callback within the transport deadline.",
        timedOut = true
    )

    private class ShizukuBinderUnavailableException : IllegalStateException()
    private class UserServiceBindException(cause: Throwable) : Exception(cause)

    private companion object {
        const val LOG_TAG = "BBoosterShizuku"
        const val USER_SERVICE_TAG = "bbooster-readonly-command-service"
        const val USER_SERVICE_VERSION = 4
        const val USER_SERVICE_TEST_ACTION = "userservice_connection_test"
        const val DIAGNOSTIC_ACTION_ID = "caller_identity"
        const val USER_SERVICE_PROCESS_SUFFIX = "bbooster"
        const val BIND_TIMEOUT_MS = 35_000L
        const val COMMAND_TIMEOUT_MS = 8_000L
        const val CALLBACK_GRACE_MS = 4_000L
        const val REMOTE_ERROR_EXIT_CODE = -1
        const val REJECTED_EXIT_CODE = -3
    }
}
