package com.honghoaibao.bbooster.core.settings

data class DeveloperSettings(
    val verboseLoggingEnabled: Boolean = false,
    val retainedDiagnosticReports: Int = DEFAULT_RETAINED_REPORTS
) {
    fun normalized(): DeveloperSettings = copy(
        retainedDiagnosticReports = retainedDiagnosticReports
            .takeIf { it in RETENTION_OPTIONS }
            ?: DEFAULT_RETAINED_REPORTS
    )

    companion object {
        const val DEFAULT_RETAINED_REPORTS = 4
        val RETENTION_OPTIONS: Set<Int> = setOf(2, 4, 8)
    }
}
