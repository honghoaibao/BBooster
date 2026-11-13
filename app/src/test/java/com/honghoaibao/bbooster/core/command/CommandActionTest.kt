package com.honghoaibao.bbooster.core.command

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CommandActionTest {

    @Test
    fun everyDiagnosticHasAnExplicitArgumentList() {
        CommandAction.entries.forEach { action ->
            val mapped = CommandAction.fromId(action.id)
            assertNotNull(mapped)
            assertArrayEquals(action.arguments.toTypedArray(), mapped!!.arguments.toTypedArray())
        }
    }

    @Test
    fun unknownOrUserSuppliedCommandIdsAreRejected() {
        assertNull(CommandAction.fromId("sh"))
        assertNull(CommandAction.fromId("settings put global window_animation_scale 0"))
        assertNull(CommandAction.fromId("unknown"))
    }

    @Test
    fun diagnosticActionsDoNotInvokeAShell() {
        CommandAction.entries.forEach { action ->
            assertFalse(action.arguments.first() in setOf("sh", "su", "bash"))
        }
    }
}
