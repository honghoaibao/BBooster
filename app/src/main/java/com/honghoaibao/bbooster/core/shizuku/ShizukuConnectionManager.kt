package com.honghoaibao.bbooster.core.shizuku

import android.content.pm.PackageManager
import com.honghoaibao.bbooster.core.logging.AppLog
import com.honghoaibao.bbooster.core.logging.LogModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

class ShizukuConnectionManager {

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
                status = ShizukuConnectionStatus.SERVICE_NOT_RUNNING
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
        if (!Shizuku.pingBinder()) {
            publishState(
                ShizukuConnectionState(
                    status = ShizukuConnectionStatus.SERVICE_NOT_RUNNING
                ),
                "binder_unavailable"
            )
            return
        }

        try {
            if (Shizuku.isPreV11()) {
                publishState(
                    ShizukuConnectionState(
                        status = ShizukuConnectionStatus.UNSUPPORTED_API
                    ),
                    "server_api_unsupported"
                )
                return
            }

            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                publishState(
                    ShizukuConnectionState(
                        status = ShizukuConnectionStatus.PERMISSION_REQUIRED
                    ),
                    "permission_required"
                )
                return
            }

            val uid = Shizuku.getUid()
            val version = Shizuku.getVersion()
            val privilege = when (uid) {
                0 -> ShizukuPrivilege.ROOT
                2000 -> ShizukuPrivilege.ADB
                else -> ShizukuPrivilege.UNKNOWN
            }

            publishState(
                ShizukuConnectionState(
                    status = ShizukuConnectionStatus.READY,
                    privilege = privilege,
                    serverUid = uid,
                    serverVersion = version
                ),
                "connection_ready"
            )
        } catch (_: SecurityException) {
            publishState(
                ShizukuConnectionState(
                    status = ShizukuConnectionStatus.PERMISSION_REQUIRED
                ),
                "permission_check_denied"
            )
        } catch (_: IllegalStateException) {
            publishState(
                ShizukuConnectionState(
                    status = ShizukuConnectionStatus.SERVICE_NOT_RUNNING
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
                    status = ShizukuConnectionStatus.ERROR
                ),
                "connection_check_error"
            )
        }
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) {
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
                    status = ShizukuConnectionStatus.REQUESTING_PERMISSION
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
                    status = ShizukuConnectionStatus.ERROR
                ),
                "permission_request_error"
            )
        }
    }

    private fun publishState(next: ShizukuConnectionState, event: String) {
        val previous = mutableState.value
        mutableState.value = next
        if (previous == next) return

        val message = "status=" + next.status.name +
            "; uid=" + (next.serverUid?.toString() ?: "unavailable") +
            "; serverVersion=" + (next.serverVersion?.toString() ?: "unavailable")
        if (next.status == ShizukuConnectionStatus.ERROR) {
            AppLog.warn(LogModule.SHIZUKU, event, message)
        } else {
            AppLog.info(LogModule.SHIZUKU, event, message)
        }
    }
}
