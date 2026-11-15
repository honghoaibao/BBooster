package com.honghoaibao.bbooster.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperSettingsTest {

    @Test
    fun defaultsKeepVerboseLoggingDisabledAndRetainFourReports() {
        val settings = DeveloperSettings()

        assertFalse(settings.verboseLoggingEnabled)
        assertEquals(4, settings.retainedDiagnosticReports)
    }

    @Test
    fun normalizedSettingsAcceptOnlySupportedRetentionValues() {
        DeveloperSettings.RETENTION_OPTIONS.forEach { count ->
            assertEquals(count, DeveloperSettings(retainedDiagnosticReports = count).normalized().retainedDiagnosticReports)
        }

        assertEquals(
            4,
            DeveloperSettings(retainedDiagnosticReports = 99).normalized().retainedDiagnosticReports
        )
    }

    @Test
    fun verboseSettingIsPreservedByNormalization() {
        assertTrue(
            DeveloperSettings(
                verboseLoggingEnabled = true,
                retainedDiagnosticReports = 8
            ).normalized().verboseLoggingEnabled
        )
    }
}
