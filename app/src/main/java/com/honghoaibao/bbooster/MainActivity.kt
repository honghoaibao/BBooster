package com.honghoaibao.bbooster

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.honghoaibao.bbooster.core.diagnostics.device.AndroidDeviceDiagnosticsProvider
import com.honghoaibao.bbooster.core.diagnostics.device.DeviceMonitoringController
import com.honghoaibao.bbooster.core.diagnostics.report.AndroidDiagnosticReportSharer
import com.honghoaibao.bbooster.core.diagnostics.report.DiagnosticReportCollector
import com.honghoaibao.bbooster.core.diagnostics.report.DiagnosticReportFormat
import com.honghoaibao.bbooster.core.diagnostics.report.DiagnosticReportWriter
import com.honghoaibao.bbooster.core.diagnostics.shizuku.ShizukuDiagnosticsProvider
import com.honghoaibao.bbooster.core.diagnostics.transport.TransportDiagnosticsProvider
import com.honghoaibao.bbooster.core.diagnostics.transport.TransportTestResult
import com.honghoaibao.bbooster.core.command.CommandTransportType
import com.honghoaibao.bbooster.core.settings.DeveloperSettings
import com.honghoaibao.bbooster.core.settings.DeveloperSettingsStore
import com.honghoaibao.bbooster.core.logging.AppLog
import com.honghoaibao.bbooster.core.logging.LogModule
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionManager
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionStatus
import com.honghoaibao.bbooster.core.shizuku.transport.ShizukuTransportManager
import com.honghoaibao.bbooster.feature.dashboard.DashboardScreen
import com.honghoaibao.bbooster.feature.devtools.DeveloperSettingsScreen
import com.honghoaibao.bbooster.feature.devtools.DiagnosticsHubScreen
import com.honghoaibao.bbooster.feature.devtools.LogViewerScreen
import com.honghoaibao.bbooster.ui.theme.BBoosterTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private var isPreparingReport by mutableStateOf(false)
    private var isRefreshingDeviceDiagnostics by mutableStateOf(false)
    private var isDeviceMonitoring by mutableStateOf(false)
    private var isTestingTransport by mutableStateOf(false)
    private var lastTransportTestResult by mutableStateOf<TransportTestResult?>(null)
    private var developerSettings by mutableStateOf(DeveloperSettings())

    private val developerSettingsStore by lazy { DeveloperSettingsStore(applicationContext) }
    private val deviceDiagnosticsProvider by lazy {
        AndroidDeviceDiagnosticsProvider(applicationContext)
    }
    private val deviceMonitoringController by lazy {
        DeviceMonitoringController(deviceDiagnosticsProvider)
    }
    private val shizukuConnectionManager by lazy { ShizukuConnectionManager() }
    private val transportManager by lazy { ShizukuTransportManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        developerSettings = developerSettingsStore.load()
        AppLog.initialize(File(filesDir, "diagnostics/logs"))
        AppLog.setVerboseLogging(developerSettings.verboseLoggingEnabled)
        AppLog.info(
            LogModule.APP,
            "application_started",
            "version=" + BuildConfig.VERSION_NAME
        )

        window.statusBarColor = android.graphics.Color.rgb(11, 14, 20)
        window.navigationBarColor = android.graphics.Color.rgb(11, 14, 20)

        setContent {
            val shizukuState by shizukuConnectionManager.state.collectAsState()
            val transportState by transportManager.state.collectAsState()
            val deviceDiagnostics by deviceMonitoringController.latestSnapshot.collectAsState()
            val deviceTelemetryHistory by deviceMonitoringController.samples.collectAsState()
            var showLogViewer by remember { mutableStateOf(false) }
            var showDeveloperSettings by remember { mutableStateOf(false) }
            var showDiagnosticsHub by remember { mutableStateOf(false) }

            BackHandler(enabled = showLogViewer || showDeveloperSettings || showDiagnosticsHub) {
                if (showLogViewer) {
                    showLogViewer = false
                } else if (showDeveloperSettings) {
                    showDeveloperSettings = false
                } else {
                    showDiagnosticsHub = false
                }
            }

            BBoosterTheme {
                when {
                    showLogViewer -> {
                        LogViewerScreen(onBack = { showLogViewer = false })
                    }
                    showDeveloperSettings -> {
                        DeveloperSettingsScreen(
                            settings = developerSettings,
                            reportCacheDirectory = File(cacheDir, "diagnostic_reports"),
                            onBack = { showDeveloperSettings = false },
                            onVerboseLoggingChanged = ::setVerboseLogging,
                            onReportRetentionChanged = ::setReportRetention
                        )
                    }
                    showDiagnosticsHub -> {
                        DiagnosticsHubScreen(
                            diagnostics = deviceDiagnostics,
                            telemetryHistory = deviceTelemetryHistory,
                            isMonitoring = isDeviceMonitoring,
                            isRefreshingDeviceDiagnostics = isRefreshingDeviceDiagnostics,
                            shizukuState = shizukuState,
                            transportState = transportState,
                            isPreparingReport = isPreparingReport,
                            onRefreshDeviceDiagnostics = ::refreshDeviceDiagnostics,
                            onOpenLogViewer = { showLogViewer = true },
                            onShareDiagnosticReport = ::shareDiagnosticReport,
                            onBack = { showDiagnosticsHub = false }
                        )
                    }
                    else -> {
                        DashboardScreen(
                            shizukuState = shizukuState,
                            commandState = transportState,
                            selectedLanguage = currentLanguageTag(),
                            onRequestShizukuPermission = shizukuConnectionManager::requestPermission,
                            onOpenShizuku = ::openShizuku,
                            onRefreshShizuku = ::refreshConnections,
                            onRunDiagnostics = ::runDiagnostics,
                            onLanguageSelected = ::setLanguage,
                            onOpenLogViewer = { showLogViewer = true },
                            onOpenDiagnosticsHub = { showDiagnosticsHub = true },
                            onShareDiagnosticReport = ::shareDiagnosticReport,
                            onOpenDeveloperSettings = { showDeveloperSettings = true },
                            deviceDiagnostics = deviceDiagnostics,
                            deviceTelemetryHistory = deviceTelemetryHistory,
                            isDeviceMonitoring = isDeviceMonitoring,
                            isRefreshingDeviceDiagnostics = isRefreshingDeviceDiagnostics,
                            onRefreshDeviceDiagnostics = ::refreshDeviceDiagnostics,
                            lastTransportTestResult = lastTransportTestResult,
                            isTestingTransport = isTestingTransport,
                            canTestTransport = shizukuState.status == ShizukuConnectionStatus.READY &&
                                !transportState.isExecuting && !transportState.isRefreshing,
                            onTestTransport = ::testTransport,
                            isPreparingReport = isPreparingReport
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        shizukuConnectionManager.registerListeners()
        transportManager.setForeground(true)
        deviceMonitoringController.start(lifecycleScope)
        isDeviceMonitoring = true
        AppLog.debug(LogModule.APP, "activity_foreground", "MainActivity entered foreground")
    }

    override fun onResume() {
        super.onResume()
        refreshConnections(forceUserServiceProbe = false)
    }

    override fun onStop() {
        // Keep the UserService binding alive, but pause automatic recovery probes
        // while the app is backgrounded.
        AppLog.debug(LogModule.APP, "activity_background", "MainActivity left foreground")
        deviceMonitoringController.stop()
        isDeviceMonitoring = false
        transportManager.setForeground(false)
        shizukuConnectionManager.unregisterListeners()
        super.onStop()
    }

    override fun onDestroy() {
        deviceMonitoringController.stop()
        val manager = transportManager
        lifecycleScope.launch(NonCancellable + Dispatchers.IO) {
            manager.close()
        }
        super.onDestroy()
    }

    private fun refreshConnections() {
        refreshConnections(forceUserServiceProbe = true)
    }

    private fun refreshConnections(forceUserServiceProbe: Boolean) {
        shizukuConnectionManager.refresh()
        lifecycleScope.launch {
            transportManager.refresh(forceUserServiceProbe = forceUserServiceProbe)
        }
    }

    private fun runDiagnostics() {
        lifecycleScope.launch {
            transportManager.execute(DIAGNOSTIC_COMMAND_ID)
        }
    }

    private fun refreshDeviceDiagnostics() {
        if (isRefreshingDeviceDiagnostics) return
        isRefreshingDeviceDiagnostics = true

        lifecycleScope.launch {
            try {
                deviceMonitoringController.collectOnce()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.warn(
                    LogModule.DEVICE,
                    "device_diagnostics_refresh_failed",
                    "exception=" + error.javaClass.simpleName
                )
            } finally {
                isRefreshingDeviceDiagnostics = false
            }
        }
    }

    private fun testTransport(transport: CommandTransportType) {
        if (isTestingTransport) return
        isTestingTransport = true
        lastTransportTestResult = null

        lifecycleScope.launch {
            try {
                lastTransportTestResult = TransportDiagnosticsProvider(transportManager)
                    .testTransport(transport)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.warn(
                    LogModule.TRANSPORT,
                    "transport_test_ui_failed",
                    "transport=" + transport.name +
                        "; exception=" + error.javaClass.simpleName
                )
            } finally {
                isTestingTransport = false
            }
        }
    }

    private fun shareDiagnosticReport(format: DiagnosticReportFormat) {
        if (isPreparingReport) return
        isPreparingReport = true

        lifecycleScope.launch {
            try {
                AppLog.info(
                    LogModule.REPORT,
                    "diagnostic_report_export_started",
                    "format=" + format.name
                )
                val collector = DiagnosticReportCollector(
                    deviceProvider = deviceDiagnosticsProvider,
                    shizukuProvider = ShizukuDiagnosticsProvider(
                        shizukuConnectionManager,
                        transportManager
                    ),
                    transportProvider = TransportDiagnosticsProvider(transportManager),
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceTelemetryHistory = deviceMonitoringController.samples.value
                        .takeLast(DeviceMonitoringController.MAX_SAMPLES)
                )
                val report = collector.collect()
                val file = DiagnosticReportWriter(
                    directory = File(cacheDir, "diagnostic_reports"),
                    maxRetainedReports = developerSettings.retainedDiagnosticReports
                ).write(report, format)

                AppLog.info(
                    LogModule.REPORT,
                    "diagnostic_report_file_ready",
                    "format=" + format.name
                )
                AndroidDiagnosticReportSharer.share(this@MainActivity, file, format)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.warn(
                    LogModule.REPORT,
                    "diagnostic_report_export_failed",
                    "exception=" + error.javaClass.simpleName
                )
                Toast.makeText(
                    this@MainActivity,
                    R.string.report_share_failed,
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isPreparingReport = false
            }
        }
    }

    private fun setVerboseLogging(enabled: Boolean) {
        developerSettings = developerSettingsStore.setVerboseLoggingEnabled(enabled)
        AppLog.setVerboseLogging(enabled)
        AppLog.info(
            LogModule.APP,
            "developer_verbose_logging_changed",
            "enabled=" + enabled
        )
    }

    private fun setReportRetention(count: Int) {
        developerSettings = developerSettingsStore.setRetainedDiagnosticReports(count)
        AppLog.info(
            LogModule.REPORT,
            "developer_report_retention_changed",
            "retained=" + developerSettings.retainedDiagnosticReports
        )
    }

    private fun setLanguage(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(languageTag)
        )
    }

    private fun currentLanguageTag(): String {
        val selectedLocale = AppCompatDelegate.getApplicationLocales().get(0)
        if (selectedLocale != null) return selectedLocale.language

        val systemLanguage = resources.configuration.locales[0]?.language
        return if (systemLanguage == "vi") "vi" else "en"
    }

    private fun openShizuku() {
        val launchIntent = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launchIntent != null) {
            startActivity(launchIntent)
            return
        }

        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL)))
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
        const val DIAGNOSTIC_COMMAND_ID = "caller_identity"
    }
}
