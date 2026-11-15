package com.honghoaibao.bbooster.core.diagnostics.device

interface DeviceDiagnosticsProvider {
    /**
     * Collects a one-time snapshot of public device telemetry.
     *
     * Implementations should return null for individual data sources that are
     * unavailable instead of failing the entire snapshot.
     */
    suspend fun collect(): DeviceDiagnostics
}
