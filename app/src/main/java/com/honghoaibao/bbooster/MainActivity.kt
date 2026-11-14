package com.honghoaibao.bbooster

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionManager
import com.honghoaibao.bbooster.core.shizuku.transport.ShizukuTransportManager
import com.honghoaibao.bbooster.feature.dashboard.DashboardScreen
import com.honghoaibao.bbooster.ui.theme.BBoosterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val shizukuConnectionManager by lazy { ShizukuConnectionManager() }
    private val transportManager by lazy { ShizukuTransportManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(11, 14, 20)
        window.navigationBarColor = android.graphics.Color.rgb(11, 14, 20)

        setContent {
            val shizukuState by shizukuConnectionManager.state.collectAsState()
            val transportState by transportManager.state.collectAsState()

            BBoosterTheme {
                DashboardScreen(
                    shizukuState = shizukuState,
                    commandState = transportState,
                    selectedLanguage = currentLanguageTag(),
                    onRequestShizukuPermission = shizukuConnectionManager::requestPermission,
                    onOpenShizuku = ::openShizuku,
                    onRefreshShizuku = ::refreshConnections,
                    onRunDiagnostics = ::runDiagnostics,
                    onLanguageSelected = ::setLanguage
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        shizukuConnectionManager.registerListeners()
        transportManager.setForeground(true)
    }

    override fun onResume() {
        super.onResume()
        refreshConnections(forceUserServiceProbe = false)
    }

    override fun onStop() {
        // Keep the UserService binding alive, but pause automatic recovery probes
        // while the app is backgrounded.
        transportManager.setForeground(false)
        shizukuConnectionManager.unregisterListeners()
        super.onStop()
    }

    override fun onDestroy() {
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
