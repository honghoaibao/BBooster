package com.bbooster.app.ui.onboarding

import com.bbooster.app.shell.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingStepTest {
    @Test
    fun mapsEveryState() {
        assertEquals(OnboardingStep.INSTALL_SHIZUKU, OnboardingStep.from(ConnectionState.ShizukuNotInstalled))
        assertEquals(OnboardingStep.START_SHIZUKU, OnboardingStep.from(ConnectionState.ShizukuNotRunning))
        assertEquals(OnboardingStep.START_SHIZUKU, OnboardingStep.from(ConnectionState.Failed("x")))
        assertEquals(OnboardingStep.GRANT_PERMISSION, OnboardingStep.from(ConnectionState.PermissionRequired))
        assertEquals(OnboardingStep.GRANT_PERMISSION, OnboardingStep.from(ConnectionState.PermissionDenied))
        assertEquals(OnboardingStep.DONE, OnboardingStep.from(ConnectionState.Connecting))
        assertEquals(OnboardingStep.DONE, OnboardingStep.from(ConnectionState.Ready(2000)))
    }

    @Test
    fun stepsAreOrdered() {
        val order = OnboardingStep.entries
        assertEquals(
            listOf(
                OnboardingStep.INSTALL_SHIZUKU,
                OnboardingStep.START_SHIZUKU,
                OnboardingStep.GRANT_PERMISSION,
                OnboardingStep.DONE,
            ),
            order,
        )
    }
}
