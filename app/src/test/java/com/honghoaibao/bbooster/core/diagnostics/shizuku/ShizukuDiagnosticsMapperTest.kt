package com.honghoaibao.bbooster.core.diagnostics.shizuku

import com.honghoaibao.bbooster.core.command.CommandDispatchState
import com.honghoaibao.bbooster.core.command.CommandExecutionStatus
import com.honghoaibao.bbooster.core.command.CommandFailureCode
import com.honghoaibao.bbooster.core.command.CommandResult
import com.honghoaibao.bbooster.core.command.CommandTransportType
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionErrorCode
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionState
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionStatus
import com.honghoaibao.bbooster.core.shizuku.ShizukuPrivilege
import com.honghoaibao.bbooster.core.shizuku.transport.TransportAvailability
import com.honghoaibao.bbooster.core.shizuku.transport.TransportManagerState
import com.honghoaibao.bbooster.core.shizuku.transport.TransportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuDiagnosticsMapperTest {

    @Test
    fun mapsServerVersionAndUidWithoutConflatingSdkLevel() {
        val diagnostics = ShizukuDiagnosticsMapper.map(
            connection = ShizukuConnectionState(
                status = ShizukuConnectionStatus.READY,
                privilege = ShizukuPrivilege.ADB,
                serverUid = 2000,
                serverVersion = 13,
                binderAvailable = true,
                permissionGranted = true,
                connectedAtMillis = 1_000L,
                lastCheckedAtMillis = 2_000L
            ),
            transports = TransportManagerState(),
            nowMillis = 5_000L
        )

        assertEquals(13, diagnostics.serverApiVersion)
        assertEquals(2000, diagnostics.serverUid)
        assertEquals(4_000L, diagnostics.connectionDurationMillis ?: -1L)
        assertTrue(diagnostics.binderAvailable)
        assertTrue(diagnostics.permissionGranted == true)
    }

    @Test
    fun binderCanBeAvailableWhilePermissionIsMissing() {
        val diagnostics = ShizukuDiagnosticsMapper.map(
            connection = ShizukuConnectionState(
                status = ShizukuConnectionStatus.PERMISSION_REQUIRED,
                binderAvailable = true,
                permissionGranted = false,
                serverVersion = 13,
                serverUid = 2000,
                privilege = ShizukuPrivilege.ADB,
                lastErrorCode = ShizukuConnectionErrorCode.PERMISSION_REQUIRED
            ),
            transports = TransportManagerState(),
            nowMillis = 10L
        )

        assertTrue(diagnostics.binderAvailable)
        assertFalse(diagnostics.permissionGranted == true)
        assertEquals(
            ShizukuConnectionStatus.PERMISSION_REQUIRED,
            diagnostics.connectionStatus
        )
        assertEquals("PERMISSION_REQUIRED", diagnostics.lastError?.code)
        assertEquals(13, diagnostics.serverApiVersion)
        assertEquals(2000, diagnostics.serverUid)
    }

    @Test
    fun reportsPreferredTransportSeparatelyFromLastCommandTransport() {
        val transports = TransportManagerState(
            userService = TransportStatus(
                CommandTransportType.USER_SERVICE,
                TransportAvailability.DEGRADED,
                detail = "UserService callback timed out."
            ),
            directProcess = TransportStatus(
                CommandTransportType.DIRECT_PROCESS,
                TransportAvailability.AVAILABLE
            ),
            activeTransport = CommandTransportType.DIRECT_PROCESS
        )

        val diagnostics = ShizukuDiagnosticsMapper.map(
            ShizukuConnectionState(),
            transports,
            nowMillis = 100L
        )

        assertEquals(CommandTransportType.USER_SERVICE, diagnostics.preferredTransport)
        assertEquals(CommandTransportType.DIRECT_PROCESS, diagnostics.lastCommandTransport)
        assertEquals(TransportAvailability.DEGRADED, diagnostics.userService.availability)
        assertEquals(TransportAvailability.AVAILABLE, diagnostics.directProcess.availability)
    }

    @Test
    fun reportsOnlyMetadataForLastCommand() {
        val transports = TransportManagerState(
            activeTransport = CommandTransportType.DIRECT_PROCESS,
            lastResult = CommandResult(
                commandId = "caller_identity",
                transport = CommandTransportType.DIRECT_PROCESS,
                status = CommandExecutionStatus.UNKNOWN,
                dispatchState = CommandDispatchState.UNKNOWN,
                failureCode = CommandFailureCode.REMOTE_CALL_FAILED,
                failureDetail = "Dispatch outcome is unknown.",
                stdout = "must-not-appear",
                stderr = "private-output"
            ),
            lastCommandAtMillis = 20L
        )

        val diagnostics = ShizukuDiagnosticsMapper.map(
            ShizukuConnectionState(),
            transports,
            nowMillis = 30L
        )

        assertEquals("caller_identity", diagnostics.lastCommand?.commandId)
        assertEquals(CommandDispatchState.UNKNOWN, diagnostics.lastCommand?.dispatchState)
        assertEquals(CommandFailureCode.REMOTE_CALL_FAILED, diagnostics.lastCommand?.failureCode)
        assertEquals(ShizukuDiagnosticErrorSource.COMMAND, diagnostics.lastError?.source)
        assertFalse(diagnostics.lastError?.message.orEmpty().contains("must-not-appear"))
        assertFalse(diagnostics.lastError?.message.orEmpty().contains("private-output"))
    }
 
    @Test
    fun choosesTheMostRecentErrorAcrossConnectionAndCommandSources() {
        val connection = ShizukuConnectionState(
            status = ShizukuConnectionStatus.SERVICE_NOT_RUNNING,
            lastErrorCode = ShizukuConnectionErrorCode.BINDER_UNAVAILABLE,
            lastErrorAtMillis = 10L
        )
        val transports = TransportManagerState(
            lastResult = CommandResult(
                commandId = "caller_identity",
                transport = CommandTransportType.DIRECT_PROCESS,
                status = CommandExecutionStatus.FAILED,
                dispatchState = CommandDispatchState.DISPATCHED,
                failureCode = CommandFailureCode.NON_ZERO_EXIT,
                failureDetail = "Registered diagnostic command returned a non-zero exit."
            ),
            lastCommandAtMillis = 20L
        )

        val diagnostics = ShizukuDiagnosticsMapper.map(connection, transports, 30L)

        assertEquals(ShizukuDiagnosticErrorSource.COMMAND, diagnostics.lastError?.source)
        assertEquals("NON_ZERO_EXIT", diagnostics.lastError?.code)
    }

    @Test
    fun keepsUnknownTimesNullAndClampsConnectionDuration() {
        val noTimes = ShizukuDiagnosticsMapper.map(
            ShizukuConnectionState(),
            TransportManagerState(),
            nowMillis = 100L
        )
        assertNull(noTimes.connectedAtMillis)
        assertNull(noTimes.connectionDurationMillis)
        assertNull(noTimes.lastConnectionCheckAtMillis)

        val futureConnection = ShizukuDiagnosticsMapper.map(
            ShizukuConnectionState(
                binderAvailable = true,
                connectedAtMillis = 500L
            ),
            TransportManagerState(),
            nowMillis = 100L
        )
        assertEquals(0L, futureConnection.connectionDurationMillis)
    }
}
