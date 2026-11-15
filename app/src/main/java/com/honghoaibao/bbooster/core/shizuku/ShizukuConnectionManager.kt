package com.honghoaibao.bbooster.core.shizuku

import android.content.pm.PackageManager
import com.honghoaibao.bbooster.core.logging.AppLog
import com.honghoaibao.bbooster.core.logging.LogModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

class ShizukuConnectionManager(
    private val clockMillis: () -> Long = System::currentTimeMillis
) {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 0xBB02
    }

    private val mutableState = MutableStateFlow(ShizukuConnectionState())
    val state: StateFlow<ShizukuConnectionState> = mutableState.asStateFlow()

    private var listenersRegistered = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        AppLog.debug(LogModule.SHIZUKU, "binder_received", "Shizuku Binder callback received")
        refresh()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        publishState(
            ShizukuConnectionState(
                status = ShizukuConnectionStatus.SERVICE_NOT_RUNNING,
                binderAvailable = false,
                lastErrorCode = ShizukuConnectionErrorCode.BINDER_UNAVAILABLE
            ),
            "binder_died"
        )
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == PERMISSION_REQUEST_CODE) {
                AppLog.info(
                    LogModule.SHIZUKU,
                    "permission_result",
                    "granted=" + (grantResult == PackageManager.PERMISSION_GRANTED)
                )
                refresh()
            }
        }

    fun registerListeners() {
        if (listenersRegistered) {
            refresh()
            return
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        listenersRegistered = true
        refresh()
    }

    fun unregisterListeners() {
        if (!listenersRegistered) return

        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        listenersRegistered = false
    }

    fun refresh() {
        val binderCheck = runCatching { Shizuku.pingBinder() }
        val binderAvailable = binderCheck.getOrDefault(false)
        if (!binderAvailable) {
            val errorCode = if (binderCheck.isFailure) {
                ShizukuConnectionErrorCode.BINDER_CHECK_FAILED
            } else {
                ShizukuConnectionErrorCode.BINDER_UNAVAILABLE
            }
            publishState(
                ShizukuConnectionState(
                    status = ShizukuConnectionStatus.SERVICE_NOT_RUNNING,
                    binderAvailable = false,
                    lastErrorCode = errorCode
                ),
                "binder_unavailable"
            )
            return
        }

        try {
            val serverVersion = Shizuku.getVersion().takeIf { it >= 0 }
            if (Shizuku.isPreV11()) {
                publishState(
                    ShizukuConnectionState(
                        status = ShizukuConnectionStatus.UNSUPPORTED_API,
                        binderAvailable = true,
                        serverVersion = serverVersion,
                        lastErrorCode = ShizukuConnectionErrorCode.SERVER_UNSUPPORTED
                    ),
                    "server_unsupported"
                )
                return
            }

            val uid = runCatching { Shizuku.getUid() }
                .getOrNull()
                ?.takeIf { it >= 0 }
            val privilege = when (uid) {
                0 -> ShizukuPrivilege.ROOT
                2000 -> ShizukuPrivilege.ADB
                else -> ShizukuPrivilege.UNKNOWN
            }

            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                publishState(
                    ShizukuConnectionState(
                        status = ShizukuConnectionStatus.PERMISSION_REQUIRED,
                        privilege = privilege,
                        serverUid = uid,
                        binderAvailable = true,
                        permissionGranted = false,
                        serverVersion = serverVersion,
                        lastErrorCode = ShizukuConnectionErrorCode.PERMISSION_REQUIRED
                    ),
                    "permission_required"
                )
                return
            }

            publishState(
                ShizukuConnectionState(
                    status = ShizukuConnectionStatus.READY,
                    privilege = privilege,
                    serverUid = uid,
                    serverVersion = serverVersion,
                    binderAvailable = true,
                    permissionGranted = true
                ),
                "connection_ready"
            )
        } catch (_: SecurityException) {
            publishState(
                ShizukuConnectionState(
                    status = ShizukuConnectionStatus.PERMISSION_REQUIRED,
                    binderAvailable = true,
                    permissionGranted = false,
                    lastErrorCode = ShizukuConnectionErrorCode.PERMISSION_CHECK_DENIED
                ),
                "permission_check_denied"
            )
        } catch (_: IllegalStateException) {
            publishState(
                ShizukuConnectionState(
                    status = ShizukuConnectionStatus.SERVICE_NOT_RUNNING,
                    binderAvailable = false,
                    lastErrorCode = ShizukuConnectionErrorCode.BINDER_STATE_INVALID
                ),
                "binder_state_invalid"
            )
        } catch (error: Exception) {
            AppLog.warn(
                LogModule.SHIZUKU,
                "connection_check_failed",
                "exception=" + error.javaClass.simpleName
            )
            publishState(
                ShizukuConnectionState(
                    status = ShizukuConnectionStatus.ERROR,
                    binderAvailable = true,
                    lastErrorCode = ShizukuConnectionErrorCode.CONNECTION_CHECK_FAILED
                ),
                "connection_check_error"
            )
        }
    }

    fun requestPermission() {
        if (!safePingBinder()) {
            AppLog.warn(LogModule.SHIZUKU, "permission_request_skipped", "reason=binder_unavailable")
            refresh()
            return
        }

        try {
            if (Shizuku.isPreV11()) {
                refresh()
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refresh()
                return
            }

            publishState(
                ShizukuConnectionState(
                    status = ShizukuConnectionStatus.REQUESTING_PERMISSION,
                    binderAvailable = true,
                    permissionGranted = false
                ),
                "permission_request_started"
            )
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (_: SecurityException) {
            refresh()
        } catch (_: IllegalStateException) {
            refresh()
        } catch (error: Exception) {
            AppLog.warn(
                LogModule.SHIZUKU,
                "permission_request_failed",
                "exception=" + error.javaClass.simpleName
            )
            publishState(
                ShizukuConnectionState(
                    status = ShizukuConnectionStatus.ERROR,
                    binderAvailable = true,
                    permissionGranted = false,
                    lastErrorCode = ShizukuConnectionErrorCode.PERMISSION_REQUEST_FAILED
                ),
                "permission_request_error"
            )
        }
    }

    private fun safePingBinder(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    private fun publishState(next: ShizukuConnectionState, event: String) {
        val previous = mutableState.value
        val now = clockMillis().coerceAtLeast(0L)
        val connectedAt = when {
            !next.binderAvailable -> null
            previous.binderAvailable -> previous.connectedAtMillis ?: now
            else -> now
        }
        val normalized = next.copy(
            lastCheckedAtMillis = now,
            connectedAtMillis = connectedAt,
            lastErrorCode = next.lastErrorCode ?: previous.lastErrorCode,
            lastErrorAtMillis = if (next.lastErrorCode != null) {
                now
            } else {
                previous.lastErrorAtMillis
            }
        )

        val meaningfulChange =
            previous.status != normalized.status ||
                previous.binderAvailable != normalized.binderAvailable ||
                previous.permissionGranted != normalized.permissionGranted ||
                previous.privilege != normalized.privilege ||
                previous.serverUid != normalized.serverUid ||
                previous.serverVersion != normalized.serverVersion ||
                previous.lastErrorCode != normalized.lastErrorCode

        mutableState.value = normalized
        if (!meaningfulChange) return

        val message = "status=" + normalized.status.name +
            "; binder=" + normalized.binderAvailable +
            "; permission=" + (normalized.permissionGranted?.toString() ?: "unknown") +
            "; uid=" + (normalized.serverUid?.toString() ?: "unavailable") +
            "; serverVersion=" + (normalized.serverVersion?.toString() ?: "unavailable")
        if (normalized.status == ShizukuConnectionStatus.ERROR) {
            AppLog.warn(LogModule.SHIZUKU, event, message)
        } else {
            AppLog.info(LogModule.SHIZUKU, event, message)
        }
    }
}
