package com.honghoaibao.bbooster.core.settings

import android.content.Context
import android.content.SharedPreferences

/** Small private preference store for local developer-only options. */
class DeveloperSettingsStore(context: Context) {

    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): DeveloperSettings = DeveloperSettings(
        verboseLoggingEnabled = preferences.getBoolean(KEY_VERBOSE_LOGGING, false),
        retainedDiagnosticReports = preferences.getInt(
            KEY_RETAINED_REPORTS,
            DeveloperSettings.DEFAULT_RETAINED_REPORTS
        )
    ).normalized()

    fun setVerboseLoggingEnabled(enabled: Boolean): DeveloperSettings {
        preferences.edit().putBoolean(KEY_VERBOSE_LOGGING, enabled).apply()
        return load()
    }

    fun setRetainedDiagnosticReports(count: Int): DeveloperSettings {
        val normalized = count.takeIf { it in DeveloperSettings.RETENTION_OPTIONS }
            ?: DeveloperSettings.DEFAULT_RETAINED_REPORTS
        preferences.edit().putInt(KEY_RETAINED_REPORTS, normalized).apply()
        return load()
    }

    companion object {
        private const val PREFERENCES_NAME = "bbooster_developer_settings"
        private const val KEY_VERBOSE_LOGGING = "verbose_logging_enabled"
        private const val KEY_RETAINED_REPORTS = "retained_diagnostic_reports"
    }
}
