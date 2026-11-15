package com.honghoaibao.bbooster.core.diagnostics.shizuku

import com.honghoaibao.bbooster.core.command.CommandCapability
import com.honghoaibao.bbooster.core.command.CommandDispatchState
import com.honghoaibao.bbooster.core.command.CommandExecutionStatus
import com.honghoaibao.bbooster.core.command.CommandFailureCode
import com.honghoaibao.bbooster.core.command.CommandTransportType
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionErrorCode
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionStatus
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionState
import com.honghoaibao.bbooster.core.shizuku.transport.TransportManagerState
import com.honghoaibao.bbooster.core.shizuku.transport.TransportStatus
import com.honghoaibao.bbooster.core.shizuku.ShizukuPrivilege
import com.honghoaibao.bbooster.core.shizuku.transport.TransportAvailability

data class ShizukuDiagnostics(
    val collectedAtMillis: Long,
    val connectionStatus: ShizukuConnectionStatus,
    val binderAvailable: Boolean,
    val permissionGranted: Boolean?,
    /** Shizuku remote server API version from getVersion(); distinct from Android SDK level. */
    val serverApiVersion: Int?,
    val serverUid: Int?,
    val privilege: ShizukuPrivilege?,
    val connectedAtMillis: Long?,
    val connectionDurationMillis: Long?,
    val lastConnectionCheckAtMillis: Long?,
    val lastTransportCheckAtMillis: Long?,
    /** BBooster prefers UserService, but may use Direct Process when fallback is safe. */
    val preferredTransport: CommandTransportType,
    /** Transport used by the most recent command, not a pinned transport selection. */
    val lastCommandTransport: CommandTransportType?,
    val isTransportRefreshing: Boolean,
    val isCommandExecuting: Boolean,
    val userService: ShizukuTransportDiagnostics,
    val directProcess: ShizukuTransportDiagnostics,
    val lastCommand: ShizukuCommandDiagnostics?,
    val lastError: ShizukuDiagnosticError?
)

data class ShizukuTransportDiagnostics(
    val transport: CommandTransportType,
    val availability: TransportAvailability,
    val capabilities: Set<CommandCapability>,
    val detail: String?
)

data class ShizukuCommandDiagnostics(
    val commandId: String,
    val transport: CommandTransportType?,
    val status: CommandExecutionStatus,
    val dispatchState: CommandDispatchState,
    val exitCode: Int?,
    val durationMillis: Long,
    val failureCode: CommandFailureCode?,
    val recordedAtMillis: Long?
)

data class ShizukuDiagnosticError(
    val source: ShizukuDiagnosticErrorSource,
    val code: String,
    val message: String,
    val occurredAtMillis: Long?
)

enum class ShizukuDiagnosticErrorSource {
    CONNECTION,
    COMMAND,
    USER_SERVICE,
    DIRECT_PROCESS
}

/**
 * Pure mapper so status presentation and error prioritization can be unit tested
 * without requiring a live Shizuku Binder.
 */
internal object ShizukuDiagnosticsMapper {

    fun map(
        connection: ShizukuConnectionState,
        transports: TransportManagerState,
        nowMillis: Long
    ): ShizukuDiagnostics {
        val now = nowMillis.coerceAtLeast(0L)
        val connectedAt = connection.connectedAtMillis
        val connectionDuration = connectedAt?.let {
            (now - it).coerceAtLeast(0L)
        }

        val commandResult = transports.lastResult
        val lastCommand = commandResult?.let {
            ShizukuCommandDiagnostics(
                commandId = it.commandId,
                transport = it.transport,
                status = it.status,
                dispatchState = it.dispatchState,
                exitCode = it.exitCode,
                durationMillis = it.durationMs,
                failureCode = it.failureCode,
                recordedAtMillis = transports.lastCommandAtMillis
            )
        }

        val errors = buildList {
            connection.lastErrorCode?.let { code ->
                add(
                    ShizukuDiagnosticError(
                        source = ShizukuDiagnosticErrorSource.CONNECTION,
                        code = code.name,
                        message = code.toDiagnosticMessage(),
                        occurredAtMillis = connection.lastErrorAtMillis
                    )
                )
            }

            if (commandResult != null && !commandResult.isSuccess) {
                add(
                    ShizukuDiagnosticError(
                        source = ShizukuDiagnosticErrorSource.COMMAND,
                        code = commandResult.failureCode?.name
                            ?: commandResult.status.name,
                        message = commandResult.failureDetail
                            ?: "The most recent registered command did not complete successfully.",
                        occurredAtMillis = transports.lastCommandAtMillis
                    )
                )
            }

            transports.userService.toDiagnosticError(
                ShizukuDiagnosticErrorSource.USER_SERVICE,
                transports.lastCheckedAtMillis
            )?.let(::add)

            transports.directProcess.toDiagnosticError(
                ShizukuDiagnosticErrorSource.DIRECT_PROCESS,
                transports.lastCheckedAtMillis
            )?.let(::add)
        }

        val lastError = errors.maxByOrNull {
            it.occurredAtMillis ?: Long.MIN_VALUE
        }

        return ShizukuDiagnostics(
            collectedAtMillis = now,
            connectionStatus = connection.status,
            binderAvailable = connection.binderAvailable,
            permissionGranted = connection.permissionGranted,
            serverApiVersion = connection.serverVersion,
            serverUid = connection.serverUid,
            privilege = connection.privilege,
            connectedAtMillis = connectedAt,
            connectionDurationMillis = connectionDuration,
            lastConnectionCheckAtMillis = connection.lastCheckedAtMillis,
            lastTransportCheckAtMillis = transports.lastCheckedAtMillis,
            preferredTransport = CommandTransportType.USER_SERVICE,
            lastCommandTransport = transports.activeTransport,
            isTransportRefreshing = transports.isRefreshing,
            isCommandExecuting = transports.isExecuting,
            userService = transports.userService.toDiagnostics(),
            directProcess = transports.directProcess.toDiagnostics(),
            lastCommand = lastCommand,
            lastError = lastError
        )
    }

    private fun TransportStatus.toDiagnostics() = ShizukuTransportDiagnostics(
            transport = transport,
            availability = state,
            capabilities = capabilities,
            detail = detail
        )

    private fun TransportStatus.toDiagnosticError(
            source: ShizukuDiagnosticErrorSource,
            checkedAtMillis: Long?
        ): ShizukuDiagnosticError? {
        if (state !in setOf(
                TransportAvailability.DEGRADED,
                TransportAvailability.UNAVAILABLE,
                TransportAvailability.UNSUPPORTED
            )
        ) return null

        return ShizukuDiagnosticError(
            source = source,
            code = state.name,
            message = detail ?: "Transport is " + state.name.lowercase() + ".",
            occurredAtMillis = checkedAtMillis
        )
    }

    private fun ShizukuConnectionErrorCode.toDiagnosticMessage(): String = when (this) {
        ShizukuConnectionErrorCode.BINDER_CHECK_FAILED ->
            "The Shizuku Binder availability check failed."
        ShizukuConnectionErrorCode.BINDER_UNAVAILABLE ->
            "The Shizuku Binder is unavailable."
        ShizukuConnectionErrorCode.SERVER_UNSUPPORTED ->
            "The installed Shizuku server version is not supported."
        ShizukuConnectionErrorCode.PERMISSION_REQUIRED ->
            "BBooster has not been granted Shizuku permission."
        ShizukuConnectionErrorCode.PERMISSION_CHECK_DENIED ->
            "The Shizuku permission check was denied."
        ShizukuConnectionErrorCode.BINDER_STATE_INVALID ->
            "The Shizuku Binder entered an invalid state."
        ShizukuConnectionErrorCode.CONNECTION_CHECK_FAILED ->
            "The Shizuku connection check failed."
        ShizukuConnectionErrorCode.PERMISSION_REQUEST_FAILED ->
            "The Shizuku permission request failed."
    }
}
