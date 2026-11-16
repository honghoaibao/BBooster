package com.bbooster.app.shell

/** What the UI shows about the privileged backend. */
sealed interface ConnectionState {
    data object ShizukuNotInstalled : ConnectionState
    data object ShizukuNotRunning : ConnectionState
    data object PermissionRequired : ConnectionState

    /** The user chose "deny and don't ask again"; it can only be changed inside the Shizuku app. */
    data object PermissionDenied : ConnectionState
    data object Connecting : ConnectionState
    data class Ready(val uid: Int) : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}

/** Raw facts gathered from Shizuku and our own service, kept separate so the logic is testable. */
data class ShizukuSnapshot(
    val installed: Boolean,
    val binderAlive: Boolean,
    val permissionGranted: Boolean,
    val permanentlyDenied: Boolean,
    val verifiedUid: Int?,
    val failure: String?,
)

object ConnectionStateResolver {
    fun resolve(s: ShizukuSnapshot): ConnectionState = when {
        !s.binderAlive ->
            if (s.installed) ConnectionState.ShizukuNotRunning else ConnectionState.ShizukuNotInstalled
        !s.permissionGranted ->
            if (s.permanentlyDenied) ConnectionState.PermissionDenied else ConnectionState.PermissionRequired
        s.failure != null -> ConnectionState.Failed(s.failure)
        s.verifiedUid != null -> ConnectionState.Ready(s.verifiedUid)
        else -> ConnectionState.Connecting
    }
}
