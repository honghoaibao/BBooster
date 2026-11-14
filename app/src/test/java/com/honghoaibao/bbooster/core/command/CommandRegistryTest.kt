package com.honghoaibao.bbooster.core.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRegistryTest {

    @Test
    fun registryContainsOnlyApplicationDeclaredDiagnosticActions() {
        assertEquals(
            setOf("caller_identity", "android_release", "android_sdk"),
            CommandRegistry.all.map(CommandDefinition::id).toSet()
        )
        assertNull(CommandRegistry.find("sh"))
        assertNull(CommandRegistry.find("settings put global test 1"))
        assertNull(CommandRegistry.find("unknown"))
    }

    @Test
    fun everyRegisteredCommandIsReadOnlyOneShotAndAllowlistedForBothTransports() {
        CommandRegistry.all.forEach { definition ->
            assertEquals(CommandAccessType.READ_ONLY, definition.accessType)
            assertEquals(CommandExecutionMode.ONE_SHOT, definition.executionMode)
            assertEquals(
                setOf(
                    CommandTransportType.USER_SERVICE,
                    CommandTransportType.DIRECT_PROCESS
                ),
                definition.supportedTransports
            )
            assertTrue(definition.requiresShizukuPermission)
            assertFalse(definition.requiresConfirmation)
            assertTrue(definition.timeoutMs > 0L)
            assertTrue(definition.maxOutputBytes > 0)
            assertTrue(definition.arguments.isNotEmpty())
        }
    }

    @Test
    fun registryLookupReturnsTheRegisteredDefinition() {
        val definition = CommandRegistry.find("caller_identity")

        assertNotNull(definition)
        assertEquals(CommandAction.CALLER_IDENTITY, definition?.action)
        assertTrue(CommandRegistry.contains("caller_identity"))
        assertFalse(CommandRegistry.contains("arbitrary"))
    }

    @Test
    fun currentDefinitionsOnlyDeclareProcessExecutionCapability() {
        CommandRegistry.all.forEach { definition ->
            assertEquals(
                setOf(CommandCapability.PROCESS_EXECUTION),
                definition.requiredCapabilities
            )
        }
    }
}
