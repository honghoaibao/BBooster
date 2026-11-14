package com.honghoaibao.bbooster.core.shizuku.transport

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.honghoaibao.bbooster.BuildConfig
import com.honghoaibao.bbooster.core.command.CommandCapability
import com.honghoaibao.bbooster.core.command.CommandDefinition
import com.honghoaibao.bbooster.core.command.CommandDispatchState
import com.honghoaibao.bbooster.core.command.CommandExecutionMode
import com.honghoaibao.bbooster.core.command.CommandExecutionStatus
import com.honghoaibao.bbooster.core.command.CommandFailureCode
import com.honghoaibao.bbooster.core.command.CommandRegistry
import com.honghoaibao.bbooster.core.command.CommandResult
import com.honghoaibao.bbooster.core.command.CommandTransportType
import com.honghoaibao.bbooster.core.shizuku.remote.BBoosterUserService
import com.honghoaibao.bbooster.core.shizuku.remote.ICommandCallback
import com.honghoaibao.bbooster.core.shizuku.remote.IUserService
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Persistent AIDL UserService transport.
 *
 * Only immutable definitions issued by CommandRegistry are accepted. The
 * transport never exposes arbitrary shell text or caller-provided arguments.
 */
class UserServiceTransport(context: Context) : ShizukuTransport {

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.applicationContext, BBoosterUserService::class.java)
    )
        .tag(USER_SERVICE_TAG)
        .version(USER_SERVICE_VERSION)
        .processNameSuffix(USER_SERVICE_PROCESS_SUFFIX)
        .debuggable(BuildConfig.DEBUG)
        .daemon(false)

    override val type = CommandTransportType.USER_SERVICE

    private val mutableStatus = MutableStateFlow(
        TransportStatus(transport = type, state = TransportAvailability.CHECKING)
    )
    override val status: StateFlow<TransportStatus> = mutableStatus.asStateFlow()

    private val bindingMutex = Mutex()
    private val bindingOutcome = MutableStateFlow<BindingOutcome?>(null)
    @Volatile private var remoteService: IUserService? = null
    @Volatile private var bindingRequested = false
    private val closed = AtomicBoolean(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (closed.get()) {
                if (safeBinderAvailable()) {
                    runCatching { Shizuku.unbindUserService(userServiceArgs, serviceConnection, true) }
                }
                return
            }
            val service = binder?.let { IUserService.Stub.asInterface(it) }
            remoteService = service
            if (service == null) bindingRequested = false
            bindingOutcome.value = if (service != null) {
                BindingOutcome.Connected(service)
            } else {
                BindingOutcome.Failed("Shizuku delivered a null UserService binder.")
            }
            mutableStatus.value = if (service != null) {
                readyStatus()
            } else {
                unavailableStatus(
                    TransportAvailability.DEGRADED,
                    "Shizuku delivered a null UserService binder."
                )
            }
            Log.i(TAG, "UserService connected; binderPresent=${service != null}")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteService = null
            bindingRequested = false
            bindingOutcome.value = BindingOutcome.Failed("UserService disconnected.")
            mutableStatus.value = unavailableStatus(
                TransportAvailability.DEGRADED,
                "UserService disconnected."
            )
            Log.w(TAG, "UserService disconnected.")
        }
    }

    override suspend fun refresh(): TransportStatus {
        return try {
            awaitService()
            readyStatus().also { mutableStatus.value = it }
        } catch (error: ConnectionFailure) {
            unavailableStatus(error.availability, error.message.orEmpty())
                .also { mutableStatus.value = it }
        } catch (error: Exception) {
            unavailableStatus(
                TransportAvailability.DEGRADED,
                "UserService check failed: ${error.javaClass.simpleName}."
            ).also { mutableStatus.value = it }
        }
    }

    override suspend fun execute(command: CommandDefinition): CommandResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.nanoTime()

            if (CommandRegistry.find(command.id) != command) {
                return@withContext result(
                    command, CommandExecutionStatus.REJECTED,
                    CommandDispatchState.NOT_DISPATCHED,
                    CommandFailureCode.COMMAND_REJECTED,
                    "Command is not registered.",
                    startedAt
                )
            }

            if (command.requiresConfirmation) {
                return@withContext result(
                    command, CommandExecutionStatus.REJECTED,
                    CommandDispatchState.NOT_DISPATCHED,
                    CommandFailureCode.USER_CONFIRMATION_REQUIRED,
                    "This transport does not accept commands requiring explicit confirmation.",
                    startedAt
                )
            }

            if (CommandTransportType.USER_SERVICE !in command.supportedTransports ||
                command.executionMode != CommandExecutionMode.ONE_SHOT
            ) {
                return@withContext result(
                    command, CommandExecutionStatus.REJECTED,
                    CommandDispatchState.NOT_DISPATCHED,
                    CommandFailureCode.TRANSPORT_UNSUPPORTED,
                    "This command is not supported by one-shot UserService.",
                    startedAt
                )
            }

            val service = try {
                awaitService()
            } catch (error: ConnectionFailure) {
                return@withContext result(
                    command, CommandExecutionStatus.NOT_STARTED,
                    CommandDispatchState.NOT_DISPATCHED,
                    error.failureCode, error.message, startedAt
                )
            } catch (error: Exception) {
                return@withContext result(
                    command, CommandExecutionStatus.NOT_STARTED,
                    CommandDispatchState.NOT_DISPATCHED,
                    CommandFailureCode.BIND_FAILED,
                    "UserService could not be connected: ${error.javaClass.simpleName}.",
                    startedAt
                )
            }

            val callbackResult = CompletableDeferred<RemoteCallbackResult>()
            try {
                service.execute(
                    command.id,
                    command.timeoutMs,
                    object : ICommandCallback.Stub() {
                        override fun onCompleted(
                            exitCode: Int,
                            stdout: String?,
                            stderr: String?,
                            timedOut: Boolean,
                            outputTruncated: Boolean
                        ) {
                            callbackResult.complete(
                                RemoteCallbackResult(
                                    exitCode = exitCode,
                                    stdout = stdout.orEmpty(),
                                    stderr = stderr.orEmpty(),
                                    timedOut = timedOut,
                                    outputTruncated = outputTruncated
                                )
                            )
                        }

                        override fun onRejected(reason: String?) {
                            callbackResult.complete(
                                RemoteCallbackResult(
                                    rejectedReason = reason ?: "UserService rejected the command."
                                )
                            )
                        }
                    }
                )
            } catch (error: Exception) {
                mutableStatus.value = unavailableStatus(
                    TransportAvailability.DEGRADED,
                    "AIDL dispatch outcome is unknown."
                )
                return@withContext result(
                    command, CommandExecutionStatus.UNKNOWN,
                    CommandDispatchState.UNKNOWN,
                    CommandFailureCode.REMOTE_CALL_FAILED,
                    "AIDL call failed after invocation began; the command will not be replayed.",
                    startedAt
                )
            }

            // A normal AIDL return means the request was dispatched. If its
            // callback is lost, do not replay it through another transport.
            val response = withTimeoutOrNull(command.timeoutMs + CALLBACK_GRACE_MS) {
                callbackResult.await()
            }

            if (response == null) {
                mutableStatus.value = unavailableStatus(
                    TransportAvailability.DEGRADED,
                    "UserService accepted the command but did not return a callback."
                )
                return@withContext result(
                    command, CommandExecutionStatus.UNKNOWN,
                    CommandDispatchState.DISPATCHED,
                    CommandFailureCode.REMOTE_CALL_FAILED,
                    "No AIDL callback arrived before the transport deadline; automatic fallback is unsafe.",
                    startedAt
                )
            }

            if (response.rejectedReason != null) {
                mutableStatus.value = readyStatus()
                return@withContext result(
                    command, CommandExecutionStatus.REJECTED,
                    CommandDispatchState.NOT_DISPATCHED,
                    CommandFailureCode.COMMAND_REJECTED,
                    response.rejectedReason,
                    startedAt
                )
            }

            mutableStatus.value = readyStatus()
            result(
                command = command,
                status = when {
                    response.timedOut -> CommandExecutionStatus.TIMED_OUT
                    response.exitCode == 0 -> CommandExecutionStatus.COMPLETED
                    else -> CommandExecutionStatus.FAILED
                },
                dispatchState = CommandDispatchState.DISPATCHED,
                failureCode = when {
                    response.timedOut -> CommandFailureCode.COMMAND_TIMED_OUT
                    response.exitCode != 0 -> CommandFailureCode.NON_ZERO_EXIT
                    else -> null
                },
                detail = when {
                    response.timedOut -> "The UserService command exceeded its execution timeout."
                    response.exitCode != 0 -> "The UserService command exited with a non-zero status."
                    else -> null
                },
                startedAt = startedAt,
                exitCode = response.exitCode,
                stdout = response.stdout,
                stderr = response.stderr,
                outputTruncated = response.outputTruncated
            )
        }

    override suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        bindingOutcome.value = BindingOutcome.Failed("UserService transport is closed.")
        resetBinding(clearOutcome = false)
        mutableStatus.value = unavailableStatus(
            TransportAvailability.UNAVAILABLE,
            "UserService transport is closed."
        )
    }

    private suspend fun awaitService(): IUserService = bindingMutex.withLock {
        if (closed.get()) {
            throw ConnectionFailure(
                CommandFailureCode.TRANSPORT_UNAVAILABLE,
                TransportAvailability.UNAVAILABLE,
                "UserService transport is closed."
            )
        }
        if (!safeBinderAvailable()) {
            throw ConnectionFailure(
                CommandFailureCode.SHIZUKU_UNAVAILABLE,
                TransportAvailability.UNAVAILABLE,
                "Shizuku Binder is unavailable."
            )
        }
        if (!safePermissionGranted()) {
            throw ConnectionFailure(
                CommandFailureCode.PERMISSION_REQUIRED,
                TransportAvailability.UNAVAILABLE,
                "BBooster does not have Shizuku permission."
            )
        }

        remoteService?.let { return@withLock it }

        bindingOutcome.value = null
        if (!bindingRequested) {
            bindingRequested = true
            mutableStatus.value = unavailableStatus(
                TransportAvailability.STARTING,
                "Starting Shizuku UserService."
            )
            try {
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            } catch (error: Exception) {
                bindingRequested = false
                throw ConnectionFailure(
                    CommandFailureCode.BIND_FAILED,
                    TransportAvailability.DEGRADED,
                    "Shizuku bind request failed: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                )
            }
        }

        val outcome = withTimeoutOrNull(BIND_TIMEOUT_MS) {
            bindingOutcome.filterNotNull().first()
        } ?: run {
            resetBinding()
            throw ConnectionFailure(
                CommandFailureCode.BIND_FAILED,
                TransportAvailability.DEGRADED,
                "No UserService connection callback arrived within ${BIND_TIMEOUT_MS} ms."
            )
        }

        when (outcome) {
            is BindingOutcome.Connected -> outcome.service
            is BindingOutcome.Failed -> {
                bindingRequested = false
                throw ConnectionFailure(
                    CommandFailureCode.BIND_FAILED,
                    TransportAvailability.DEGRADED,
                    outcome.detail
                )
            }
        }
    }

    private fun resetBinding(clearOutcome: Boolean = true) {
        val shouldUnbind = bindingRequested
        bindingRequested = false
        remoteService = null
        if (clearOutcome) bindingOutcome.value = null
        if (shouldUnbind && safeBinderAvailable()) {
            runCatching {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            }
        }
    }

    private fun safeBinderAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) {
        false
    }

    private fun safePermissionGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    private fun capabilities() = setOf(
        CommandCapability.PROCESS_EXECUTION,
        CommandCapability.PERSISTENT_SERVICE,
        CommandCapability.AIDL_CALLBACK
    )

    private fun readyStatus() = TransportStatus(
        transport = type,
        state = TransportAvailability.READY,
        capabilities = capabilities()
    )

    private fun unavailableStatus(
        state: TransportAvailability,
        detail: String
    ) = TransportStatus(
        transport = type,
        state = state,
        capabilities = if (state == TransportAvailability.READY) capabilities() else emptySet(),
        detail = detail
    )

    private fun result(
        command: CommandDefinition,
        status: CommandExecutionStatus,
        dispatchState: CommandDispatchState,
        failureCode: CommandFailureCode? = null,
        detail: String? = null,
        startedAt: Long = System.nanoTime(),
        exitCode: Int? = null,
        stdout: String = "",
        stderr: String = "",
        outputTruncated: Boolean = false
    ) = CommandResult(
        commandId = command.id,
        transport = type,
        status = status,
        dispatchState = dispatchState,
        exitCode = exitCode,
        stdout = stdout,
        stderr = stderr,
        durationMs = ((System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND).coerceAtLeast(0L),
        failureCode = failureCode,
        failureDetail = detail,
        outputTruncated = outputTruncated
    )

    private sealed interface BindingOutcome {
        data class Connected(val service: IUserService) : BindingOutcome
        data class Failed(val detail: String) : BindingOutcome
    }

    private data class RemoteCallbackResult(
        val exitCode: Int? = null,
        val stdout: String = "",
        val stderr: String = "",
        val timedOut: Boolean = false,
        val outputTruncated: Boolean = false,
        val rejectedReason: String? = null
    )

    private class ConnectionFailure(
        val failureCode: CommandFailureCode,
        val availability: TransportAvailability,
        override val message: String
    ) : Exception(message)

    private companion object {
        const val TAG = "BBoosterUserServiceTransport"
        const val USER_SERVICE_TAG = "bbooster-command-service"
        const val USER_SERVICE_VERSION = 4
        const val USER_SERVICE_PROCESS_SUFFIX = "bbooster"
        const val BIND_TIMEOUT_MS = 35_000L
        const val CALLBACK_GRACE_MS = 2_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
