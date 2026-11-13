package com.honghoaibao.bbooster

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionManager
import com.honghoaibao.bbooster.feature.dashboard.DashboardScreen
import com.honghoaibao.bbooster.ui.theme.BBoosterTheme

class MainActivity : ComponentActivity() {

    private val shizukuConnectionManager by lazy { ShizukuConnectionManager() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(11, 14, 20)
        window.navigationBarColor = android.graphics.Color.rgb(11, 14, 20)

        setContent {
            val shizukuState by shizukuConnectionManager.state.collectAsState()

            BBoosterTheme {
                DashboardScreen(
                    shizukuState = shizukuState,
                    onRequestShizukuPermission = shizukuConnectionManager::requestPermission,
                    onOpenShizuku = ::openShizuku,
                    onRefreshShizuku = shizukuConnectionManager::refresh
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        shizukuConnectionManager.registerListeners()
    }

    override fun onResume() {
        super.onResume()
        shizukuConnectionManager.refresh()
    }

    override fun onStop() {
        shizukuConnectionManager.unregisterListeners()
        super.onStop()
    }

    private fun openShizuku() {
        val launchIntent = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launchIntent != null) {
            startActivity(launchIntent)
            return
        }

        val downloadIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(SHIZUKU_DOWNLOAD_URL)
        )
        startActivity(downloadIntent)
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
    }
}
