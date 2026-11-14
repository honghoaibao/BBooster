package com.honghoaibao.bbooster.core.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPolicyTest {

    @Test
    fun unknownCommandIsRejectedBeforeTransportSelection() {
        val result = CommandPolicy.authorize(
            "settings put global test 1",
            grantedContext()
        )

        assertEquals(
            CommandPolicyRejection.UNKNOWN_COMMAND,
            (result as CommandAuthorization.Denied).reason
        )
    }

    @Test
    fun unavailableBinderPreventsExecution() {
        val result = CommandPolicy.authorize(
            "caller_identity",
            grantedContext().copy(binderAvailable = false)
        )

        assertEquals(
            CommandPolicyRejection.SHIZUKU_UNAVAILABLE,
            (result as CommandAuthorization.Denied).reason
        )
    }

    @Test
    fun missingShizukuPermissionPreventsExecution() {
        val result = CommandPolicy.authorize(
            "caller_identity",
            grantedContext().copy(shizukuPermissionGranted = false)
        )

        assertEquals(
            CommandPolicyRejection.PERMISSION_REQUIRED,
            (result as CommandAuthorization.Denied).reason
        )
    }

    @Test
    fun readOnlyCommandDoesNotRequireAdditionalConfirmation() {
        val result = CommandPolicy.authorize(
            "caller_identity",
            grantedContext()
        )

        assertTrue(result is CommandAuthorization.Allowed)
    }

    @Test
    fun mutatingCommandRequiresExplicitConfirmation() {
        val definition = CommandRegistry.find("caller_identity")!!.copy(
            accessType = CommandAccessType.MUTATING,
            requiresConfirmation = true
        )

        val denied = CommandPolicy.evaluate(
            definition,
            grantedContext().copy(userConfirmed = false)
        )
        assertEquals(
            CommandPolicyRejection.USER_CONFIRMATION_REQUIRED,
            (denied as CommandAuthorization.Denied).reason
        )

        val allowed = CommandPolicy.evaluate(
            definition,
            grantedContext().copy(userConfirmed = true)
        )
        assertTrue(allowed is CommandAuthorization.Allowed)
    }

    private fun grantedContext() = CommandPolicyContext(
        binderAvailable = true,
        shizukuPermissionGranted = true
    )
}
