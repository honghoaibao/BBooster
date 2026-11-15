package com.honghoaibao.bbooster.core.shizuku.transport

import android.content.Context
import android.content.pm.PackageManager
import com.honghoaibao.bbooster.core.logging.AppLog
import com.honghoaibao.bbooster.core.logging.LogModule
import com.honghoaibao.bbooster.core.command.CommandAuthorization
import com.honghoaibao.bbooster.core.command.CommandDispatchState
import com.honghoaibao.bbooster.core.command.CommandExecutionStatus
import com.honghoaibao.bbooster.core.command.CommandFailureCode
import com.honghoaibao.bbooster.core.command.CommandPolicy
import com.honghoaibao.bbooster.core.command.CommandPolicyContext
import com.honghoaibao.bbooster.core.command.CommandPolicyRejection
import com.honghoaibao.bbooster.core.command.CommandRegistry
import com.honghoaibao.bbooster.core.command.CommandResult
import com.honghoaibao.bbooster.core.command.CommandRetryPolicy
import com.honghoaibao.bbooster.core.command.CommandTransportType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

/**
 * Owns transport selection, retry safety and recovery policy.
 *
 * UserService is always preferred when its retry window is open. Direct Process
 * is used only when the previous transport explicitly confirms that the command
 * was not dispatched. An UNKNOWN or dispatched result is never replayed.
 */
private fun createShizukuAuthorizationContext(): CommandPolicyContext {
    val binderAvailable = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    val permissionGranted = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
    return CommandPolicyContext(
        binderAvailable = binderAvailable,
        shizukuPermissionGranted = permissionGranted
    )
}

class ShizukuTransportManager internal constructor(
    userServiceTransport: ShizukuTransport,
    directProcessTransport: ShizukuTransport,
    authorizationContextProvider: () -> CommandPolicyContext,
    private val clockMs: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
    private val userServiceRetryDelayMs: Long = DEFAULT_USER_SERVICE_RETRY_DELAY_MS,
    private val wallClockMillis: () -> Long = System::currentTimeMillis
) {

    constructor(context: Context) : this(
        UserServiceTransport(context.applicationContext),
        DirectProcessTransport(),
        ::createShizukuAuthorizationContext
    )

    private val userServiceTransport = userServiceTransport
    private val directProcessTransport = directProcessTransport
    private val authorizationContextProvider = authorizationContextProvider

    private val executionMutex = Mutex()
    private val mutableState = MutableStateFlow(TransportManagerState())
    val state: StateFlow<TransportManagerState> = mutableState.asStateFlow()
    private val statusScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var retryUserServiceAtMs = 0L
    @Volatile private var isClosed = false
    @Volatile private var isForeground = false
    private var userServiceRecoveryJob: Job? = null

    init {
        require(userServiceTransport.type == CommandTransportType.USER_SERVICE) {
            "The primary transport must be USER_SERVICE."
        }
        require(directProcessTransport.type == CommandTransportType.DIRECT_PROCESS) {
            "The fallback transport must be DIRECT_PROCESS."
        }
        require(userServiceRetryDelayMs >= 0L) {
            "UserService retry delay cannot be negative."
        }

        statusScope.launch {
            var previousAvailability = userServiceTransport.status.value.state
            userServiceTransport.status.collect { status ->
                val recoveredDuringCooldown =
                    previousAvailability != TransportAvailability.READY &&
                        status.state == TransportAvailability.READY &&
                        retryUserServiceAtMs > clockMs()
                previousAvailability = status.state
                if (recoveredDuringCooldown) {
                    retryUserServiceAtMs = 0L
                    userServiceRecoveryJob?.cancel()
                    userServiceRecoveryJob = null
                }
                mutableState.update { current ->
                    current.copy(
                        userService = visibleUserServiceStatus(status),
                        userServiceRetryAtMs = if (recoveredDuringCooldown) {
                            null
                        } else {
                            current.userServiceRetryAtMs
                        },
                        lastFallbackReason = if (recoveredDuringCooldown) {
                            null
                        } else {
                            current.lastFallbackReason
                        }
                    )
                }
            }
        }
        statusScope.launch {
            directProcessTransport.status.collect { status ->
                mutableState.update { current ->
                    current.copy(directProcess = status)
                }
            }
        }
    }

    /**
     * Controls foreground-only recovery probes. No periodic Shizuku work is
     * scheduled while the app is backgrounded.
     */
    fun setForeground(foreground: Boolean) {
        if (isClosed || isForeground == foreground) return
        isForeground = foreground
        if (!foreground) {
            userServiceRecoveryJob?.cancel()
            userServiceRecoveryJob = null
        } else {
            scheduleUserServiceRecovery()
        }
    }

    /**
     * Explicit refresh can bypass the automatic cooldown. Lifecycle refreshes
     * leave it intact and check UserService only after the deadline.
     */
    suspend fun refresh(
        forceUserServiceProbe: Boolean = false
    ): TransportManagerState = executionMutex.withLock {
        if (isClosed) return@withLock mutableState.value

        mutableState.update { it.copy(isRefreshing = true) }
        try {
            if (forceUserServiceProbe) {
                retryUserServiceAtMs = 0L
                userServiceRecoveryJob?.cancel()
                userServiceRecoveryJob = null
            }
            val shouldProbeUserService =
                forceUserServiceProbe || clockMs() >= retryUserServiceAtMs

            val (userStatus, directStatus) = coroutineScope {
                val user = async {
                    if (shouldProbeUserService) {
                        refreshSafely(userServiceTransport)
                    } else {
                        userServiceTransport.status.value
                    }
                }
                val direct = async { refreshSafely(directProcessTransport) }
                user.await() to direct.await()
            }

            if (shouldProbeUserService) {
                retryUserServiceAtMs = if (
                    userStatus.state == TransportAvailability.DEGRADED ||
                    userStatus.state == TransportAvailability.UNAVAILABLE ||
                    userStatus.state == TransportAvailability.UNSUPPORTED
                ) {
                    clockMs() + userServiceRetryDelayMs
                } else {
                    0L
                }
                if (retryUserServiceAtMs == 0L) {
                    userServiceRecoveryJob?.cancel()
                    userServiceRecoveryJob = null
                }
            }

            mutableState.value = mutableState.value.copy(
                userService = visibleUserServiceStatus(userStatus),
                directProcess = directStatus,
                userServiceRetryAtMs = retryUserServiceAtMs.takeIf { it > clockMs() },
                lastFallbackReason = if (shouldProbeUserService) {
                    userStatus.detail.takeIf {
                        userStatus.state == TransportAvailability.UNSUPPORTED ||
                            userStatus.state == TransportAvailability.DEGRADED ||
                            userStatus.state == TransportAvailability.UNAVAILABLE
                    }
                } else {
                    mutableState.value.lastFallbackReason
                }
            )
        } finally {
            mutableState.update {
                it.copy(
                    isRefreshing = false,
                    lastCheckedAtMillis = wallClockMillis().coerceAtLeast(0L)
                )
            }
        }

        if (retryUserServiceAtMs > clockMs()) {
            scheduleUserServiceRecovery()
        }
        mutableState.value
    }

    /** Runs a command by registry ID; caller-supplied shell text is never accepted. */
    suspend fun execute(actionId: String): CommandResult = executionMutex.withLock {
        if (isClosed) {
            return@withLock rejectedResult(
                actionId = actionId,
                failureCode = CommandFailureCode.TRANSPORT_UNAVAILABLE,
                detail = "Shizuku transport manager is closed."
            )
        }

        mutableState.value = mutableState.value.copy(isExecuting = true)
        try {
            executeLocked(actionId)
        } finally {
            mutableState.value = mutableState.value.copy(
                isExecuting = false,
                userService = effectiveUserServiceStatus(),
                directProcess = directProcessTransport.status.value
            )
        }
    }

    private suspend fun executeLocked(actionId: String): CommandResult {
        val definition = CommandRegistry.find(actionId)
            ?: return finish(rejectedResult(
                actionId = actionId,
                failureCode = CommandFailureCode.COMMAND_REJECTED,
                detail = "Command is not in the application allowlist."
            ))

        val authorization = try {
            CommandPolicy.authorize(actionId, authorizationContextProvider())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return finish(rejectedResult(
                actionId = actionId,
                failureCode = CommandFailureCode.INTERNAL_ERROR,
                detail = "Authorization check failed: ${error.javaClass.simpleName}."
            ))
        }

        if (authorization !is CommandAuthorization.Allowed) {
            val rejection = (authorization as CommandAuthorization.Denied).reason
            return finish(rejectedResult(
                actionId = actionId,
                failureCode = rejection.toFailureCode(),
                detail = rejection.toDetail()
            ))
        }

        var userServiceResult: CommandResult? = null
        val canUseUserService =
            CommandTransportType.USER_SERVICE in definition.supportedTransports &&
                clockMs() >= retryUserServiceAtMs

        if (canUseUserService) {
            userServiceResult = executeSafely(userServiceTransport, definition.id)

            if (userServiceResult.isSuccess) {
                retryUserServiceAtMs = 0L
                userServiceRecoveryJob?.cancel()
                userServiceRecoveryJob = null
                mutableState.value = mutableState.value.copy(
                    userServiceRetryAtMs = null,
                    lastFallbackReason = null
                )
                return finish(userServiceResult)
            }

            if (!userServiceResult.canSafelyFallback ||
                definition.retryPolicy != CommandRetryPolicy.ONLY_WHEN_NOT_DISPATCHED
            ) {
                if (
                    userServiceTransport.status.value.state == TransportAvailability.DEGRADED ||
                    userServiceTransport.status.value.state == TransportAvailability.UNAVAILABLE ||
                    userServiceTransport.status.value.state == TransportAvailability.UNSUPPORTED
                ) {
                    retryUserServiceAtMs = clockMs() + userServiceRetryDelayMs
                    mutableState.value = mutableState.value.copy(
                        userServiceRetryAtMs = retryUserServiceAtMs,
                        lastFallbackReason = userServiceResult.failureDetail
                            ?: "UserService health needs to be checked again."
                    )
                    scheduleUserServiceRecovery()
                }
                return finish(userServiceResult)
            }

            retryUserServiceAtMs = clockMs() + userServiceRetryDelayMs
            val reason = userServiceResult.failureDetail
                ?: userServiceResult.stderr.takeIf { it.isNotBlank() }
                ?: userServiceResult.failureCode?.name
                ?: "UserService did not start the command."

            mutableState.value = mutableState.value.copy(
                userService = userServiceTransport.status.value.copy(
                    state = TransportAvailability.DEGRADED,
                    detail = reason
                ),
                userServiceRetryAtMs = retryUserServiceAtMs,
                lastFallbackReason = reason
            )
            scheduleUserServiceRecovery()
        }

        if (CommandTransportType.DIRECT_PROCESS !in definition.supportedTransports) {
            return finish(
                userServiceResult ?: rejectedResult(
                    actionId,
                    CommandFailureCode.TRANSPORT_UNSUPPORTED,
                    "No supported transport is available for this command."
                )
            )
        }

        val directResult = executeSafely(directProcessTransport, definition.id)
        val finalResult = if (userServiceResult != null && !directResult.isSuccess) {
            directResult.copy(
                durationMs = userServiceResult.durationMs + directResult.durationMs,
                failureDetail = listOfNotNull(
                    "UserService: ${userServiceResult.failureDetail ?: userServiceResult.failureCode?.name ?: "not started"}",
                    "Direct Process: ${directResult.failureDetail ?: directResult.failureCode?.name ?: "not completed"}"
                ).joinToString("; ")
            )
        } else if (userServiceResult != null) {
            directResult.copy(
                durationMs = userServiceResult.durationMs + directResult.durationMs
            )
        } else {
            directResult
        }

        return finish(finalResult)
    }

    private suspend fun executeSafely(
        transport: ShizukuTransport,
        actionId: String
    ): CommandResult {
        val definition = CommandRegistry.find(actionId)
            ?: return rejectedResult(
                actionId,
                CommandFailureCode.COMMAND_REJECTED,
                "Command is not in the application allowlist."
            )

        return try {
            val result = transport.execute(definition)
            val normalizedResult = if (
                result.commandId != actionId || result.transport != transport.type
            ) {
                CommandResult(
                    commandId = actionId,
                    transport = transport.type,
                    status = CommandExecutionStatus.UNKNOWN,
                    dispatchState = CommandDispatchState.UNKNOWN,
                    failureCode = CommandFailureCode.INTERNAL_ERROR,
                    failureDetail = "Transport returned a result that does not match the requested command."
                )
            } else {
                result
            }
            logTransportResult(transport, normalizedResult)
            normalizedResult
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // An exception escaping a transport does not prove that its remote
            // side did not receive the command. Treat it as UNKNOWN, never retry.
            AppLog.warn(
                LogModule.TRANSPORT,
                "transport_execution_unknown",
                "transport=" + transport.type.name +
                    "; exception=" + error.javaClass.simpleName +
                    "; dispatch=UNKNOWN"
            )
            CommandResult(
                commandId = actionId,
                transport = transport.type,
                status = CommandExecutionStatus.UNKNOWN,
                dispatchState = CommandDispatchState.UNKNOWN,
                failureCode = CommandFailureCode.INTERNAL_ERROR,
                failureDetail = "Transport threw " + error.javaClass.simpleName + "; dispatch outcome is unknown."
            )
        }
    }

    private suspend fun refreshSafely(transport: ShizukuTransport): TransportStatus =
        try {
            transport.refresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.warn(
                LogModule.TRANSPORT,
                "transport_refresh_failed",
                "transport=" + transport.type.name +
                    "; exception=" + error.javaClass.simpleName
            )
            TransportStatus(
                transport = transport.type,
                state = TransportAvailability.DEGRADED,
                detail = "Refresh failed: " + error.javaClass.simpleName + "."
            )
        }

    private fun logTransportResult(
        transport: ShizukuTransport,
        result: CommandResult
    ) {
        val summary = "transport=" + transport.type.name +
            "; commandId=" + result.commandId +
            "; status=" + result.status.name +
            "; dispatch=" + result.dispatchState.name +
            "; durationMs=" + result.durationMs +
            "; failure=" + (result.failureCode?.name ?: "none")

        if (result.isSuccess) {
            AppLog.info(LogModule.TRANSPORT, "command_completed", summary)
        } else {
            AppLog.warn(LogModule.TRANSPORT, "command_not_completed", summary)
        }
    }

    private fun scheduleUserServiceRecovery() {
        if (
            isClosed ||
            !isForeground ||
            userServiceRetryDelayMs <= 0L ||
            retryUserServiceAtMs <= 0L
        ) {
            return
        }

        val scheduledDeadline = retryUserServiceAtMs
        userServiceRecoveryJob?.cancel()
        userServiceRecoveryJob = statusScope.launch {
            delay((scheduledDeadline - clockMs()).coerceAtLeast(0L))
            executionMutex.withLock {
                if (
                    isClosed ||
                    !isForeground ||
                    retryUserServiceAtMs != scheduledDeadline
                ) {
                    return@withLock
                }

                val status = refreshSafely(userServiceTransport)
                mutableState.update {
                    it.copy(lastCheckedAtMillis = wallClockMillis().coerceAtLeast(0L))
                }
                when (status.state) {
                    TransportAvailability.READY -> {
                        retryUserServiceAtMs = 0L
                        mutableState.value = mutableState.value.copy(
                            userService = status,
                            userServiceRetryAtMs = null,
                            lastFallbackReason = null
                        )
                    }

                    TransportAvailability.DEGRADED,
                    TransportAvailability.UNAVAILABLE,
                    TransportAvailability.UNSUPPORTED -> {
                        retryUserServiceAtMs = clockMs() + userServiceRetryDelayMs
                        mutableState.value = mutableState.value.copy(
                            userService = status,
                            userServiceRetryAtMs = retryUserServiceAtMs,
                            lastFallbackReason = status.detail
                                ?: "UserService is still unavailable."
                        )
                    }

                    else -> {
                        retryUserServiceAtMs = 0L
                        mutableState.value = mutableState.value.copy(
                            userService = status,
                            userServiceRetryAtMs = null
                        )
                    }
                }
            }

            if (retryUserServiceAtMs > clockMs()) {
                scheduleUserServiceRecovery()
            } else {
                userServiceRecoveryJob = null
            }
        }
    }

    private fun effectiveUserServiceStatus(): TransportStatus =
        visibleUserServiceStatus(userServiceTransport.status.value)

    private fun visibleUserServiceStatus(current: TransportStatus): TransportStatus {
        return if (
            retryUserServiceAtMs > clockMs() &&
            current.state != TransportAvailability.UNSUPPORTED
        ) {
            current.copy(
                state = TransportAvailability.DEGRADED,
                detail = mutableState.value.userService.detail
                    ?: current.detail
                    ?: "UserService is in a temporary retry cooldown."
            )
        } else {
            current
        }
    }

    private fun finish(result: CommandResult): CommandResult {
        mutableState.value = mutableState.value.copy(
            isExecuting = false,
            activeTransport = result.transport,
            lastResult = result,
            lastCommandAtMillis = wallClockMillis().coerceAtLeast(0L),
            userService = effectiveUserServiceStatus(),
            directProcess = directProcessTransport.status.value
        )
        return result
    }

    suspend fun close() = executionMutex.withLock {
        if (isClosed) return@withLock
        isClosed = true
        isForeground = false
        userServiceRecoveryJob?.cancel()
        userServiceRecoveryJob = null
        statusScope.cancel()
        try {
            userServiceTransport.close()
        } catch (_: Exception) {
            // Continue releasing the fallback transport even if unbind fails.
        }
        try {
            directProcessTransport.close()
        } catch (_: Exception) {
            // Both transports are independent; attempt to release each one.
        }
        mutableState.value = mutableState.value.copy(
            isExecuting = false,
            isRefreshing = false,
            userService = userServiceTransport.status.value,
            directProcess = directProcessTransport.status.value,
            activeTransport = null
        )
    }

    private fun rejectedResult(
        actionId: String,
        failureCode: CommandFailureCode,
        detail: String
    ) = CommandResult(
        commandId = actionId.ifBlank { "unknown" },
        transport = null,
        status = CommandExecutionStatus.REJECTED,
        dispatchState = CommandDispatchState.NOT_DISPATCHED,
        failureCode = failureCode,
        failureDetail = detail
    )

    private fun CommandPolicyRejection.toFailureCode(): CommandFailureCode = when (this) {
        CommandPolicyRejection.UNKNOWN_COMMAND -> CommandFailureCode.COMMAND_REJECTED
        CommandPolicyRejection.SHIZUKU_UNAVAILABLE -> CommandFailureCode.SHIZUKU_UNAVAILABLE
        CommandPolicyRejection.PERMISSION_REQUIRED -> CommandFailureCode.PERMISSION_REQUIRED
        CommandPolicyRejection.USER_CONFIRMATION_REQUIRED -> CommandFailureCode.USER_CONFIRMATION_REQUIRED
    }

    private fun CommandPolicyRejection.toDetail(): String = when (this) {
        CommandPolicyRejection.UNKNOWN_COMMAND -> "Command is not in the application allowlist."
        CommandPolicyRejection.SHIZUKU_UNAVAILABLE -> "Shizuku Binder is unavailable."
        CommandPolicyRejection.PERMISSION_REQUIRED -> "BBooster does not have Shizuku permission."
        CommandPolicyRejection.USER_CONFIRMATION_REQUIRED -> "The command requires explicit user confirmation."
    }

    private companion object {
        const val DEFAULT_USER_SERVICE_RETRY_DELAY_MS = 30_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

data class TransportManagerState(
    val userService: TransportStatus = TransportStatus(
        transport = CommandTransportType.USER_SERVICE,
        state = TransportAvailability.CHECKING
    ),
    val directProcess: TransportStatus = TransportStatus(
        transport = CommandTransportType.DIRECT_PROCESS,
        state = TransportAvailability.CHECKING
    ),
    val isExecuting: Boolean = false,
    val isRefreshing: Boolean = false,
    val activeTransport: CommandTransportType? = null,
    val lastResult: CommandResult? = null,
    val userServiceRetryAtMs: Long? = null,
    val lastFallbackReason: String? = null,
    val lastCheckedAtMillis: Long? = null,
    val lastCommandAtMillis: Long? = null
)
