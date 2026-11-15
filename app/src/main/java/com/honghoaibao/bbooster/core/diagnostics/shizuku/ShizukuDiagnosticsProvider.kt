package com.honghoaibao.bbooster.core.diagnostics.shizuku

import com.honghoaibao.bbooster.core.logging.AppLog
import com.honghoaibao.bbooster.core.logging.LogModule
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionManager
import com.honghoaibao.bbooster.core.shizuku.transport.ShizukuTransportManager

/**
 * Produces a read-only diagnostics snapshot from the existing connection and
 * transport managers. It does not probe, bind, grant permission or execute commands.
 */
class ShizukuDiagnosticsProvider(
    private val connectionManager: ShizukuConnectionManager,
    private val transportManager: ShizukuTransportManager,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {

    fun collect(): ShizukuDiagnostics {
        val snapshot = ShizukuDiagnosticsMapper.map(
            connection = connectionManager.state.value,
            transports = transportManager.state.value,
            nowMillis = clockMillis()
        )

        AppLog.debug(
            LogModule.SHIZUKU,
            "shizuku_diagnostics_collected",
            "binder=" + snapshot.binderAvailable +
                "; permission=" + (snapshot.permissionGranted?.toString() ?: "unknown") +
                "; userService=" + snapshot.userService.availability.name +
                "; directProcess=" + snapshot.directProcess.availability.name
        )
        return snapshot
    }
}
