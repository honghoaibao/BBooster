package com.honghoaibao.bbooster.core.shizuku

import android.content.pm.PackageManager
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
        refresh()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        mutableState.value = ShizukuConnectionState(
            status = ShizukuConnectionStatus.SERVICE_NOT_RUNNING
        )
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST_CODE) refresh()
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
            mutableState.value = ShizukuConnectionState(
                status = ShizukuConnectionStatus.SERVICE_NOT_RUNNING
            )
            return
        }

        try {
            if (Shizuku.isPreV11()) {
                mutableState.value = ShizukuConnectionState(
                    status = ShizukuConnectionStatus.UNSUPPORTED_API
                )
                return
            }

            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                mutableState.value = ShizukuConnectionState(
                    status = ShizukuConnectionStatus.PERMISSION_REQUIRED
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

            mutableState.value = ShizukuConnectionState(
                status = ShizukuConnectionStatus.READY,
                privilege = privilege,
                serverUid = uid,
                serverVersion = version
            )
        } catch (_: SecurityException) {
            mutableState.value = ShizukuConnectionState(
                status = ShizukuConnectionStatus.PERMISSION_REQUIRED
            )
        } catch (_: IllegalStateException) {
            mutableState.value = ShizukuConnectionState(
                status = ShizukuConnectionStatus.SERVICE_NOT_RUNNING
            )
        } catch (_: Exception) {
            mutableState.value = ShizukuConnectionState(
                status = ShizukuConnectionStatus.ERROR
            )
        }
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) {
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

            mutableState.value = ShizukuConnectionState(
                status = ShizukuConnectionStatus.REQUESTING_PERMISSION
            )
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (_: SecurityException) {
            refresh()
        } catch (_: IllegalStateException) {
            refresh()
        } catch (_: Exception) {
            mutableState.value = ShizukuConnectionState(
                status = ShizukuConnectionStatus.ERROR
            )
        }
    }
}
