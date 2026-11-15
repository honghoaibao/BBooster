package com.honghoaibao.bbooster.core.logging

import java.nio.file.Files
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLoggerTest {

    private val directExecutor = Executor { command -> command.run() }

    @Test
    fun debugLogsAreDisabledUntilVerboseLoggingIsEnabled() {
        val store = FileLogStore(Files.createTempDirectory("bbooster-logger").toFile())
        val logger = AppLogger(store, directExecutor) { 123L }

        logger.debug(LogModule.APP, "hidden_debug", "hidden")
        logger.info(LogModule.APP, "visible_info", "visible")

        assertTrue(logger.flush())
        assertEquals(1, logger.query().size)

        logger.setVerboseLogging(true)
        logger.debug(LogModule.APP, "enabled_debug", "debug detail")

        assertTrue(logger.flush())
        assertEquals(2, logger.query().size)
        assertTrue(logger.isVerboseLoggingEnabled())
    }

    @Test
    fun queryFiltersByLevelModuleAndSearchText() {
        val store = FileLogStore(Files.createTempDirectory("bbooster-query").toFile())
        val logger = AppLogger(store, directExecutor) { 456L }

        logger.info(LogModule.DEVICE, "ram_read", "RAM diagnostics complete")
        logger.warn(LogModule.TRANSPORT, "bind_timeout", "UserService timed out")
        logger.error(LogModule.SHIZUKU, "binder_dead", "Shizuku Binder disconnected")

        val result = logger.query(
            LogQuery(
                levels = setOf(LogLevel.WARN),
                modules = setOf(LogModule.TRANSPORT),
                search = "timeout"
            )
        )

        assertEquals(1, result.size)
        assertEquals("bind_timeout", result.single().event)
    }
}
