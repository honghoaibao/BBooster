package com.bbooster.app.ui.onboarding

import com.bbooster.app.shell.ConnectionState

/**
 * Which of the three onboarding steps the user is on, derived purely from [ConnectionState]
 * (so it survives process death and re-entering the screen). [ordinal] is the step index.
 */
enum class OnboardingStep {
    INSTALL_SHIZUKU,
    START_SHIZUKU,
    GRANT_PERMISSION,
    DONE,
    ;

    companion object {
        fun from(state: ConnectionState): OnboardingStep = when (state) {
            ConnectionState.ShizukuNotInstalled -> INSTALL_SHIZUKU
            ConnectionState.ShizukuNotRunning -> START_SHIZUKU
            is ConnectionState.Failed -> START_SHIZUKU // restarting Shizuku is the usual fix
            ConnectionState.PermissionRequired, ConnectionState.PermissionDenied -> GRANT_PERMISSION
            ConnectionState.Connecting, is ConnectionState.Ready -> DONE
        }
    }
}
