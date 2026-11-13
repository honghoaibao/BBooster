package com.honghoaibao.bbooster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.honghoaibao.bbooster.feature.dashboard.DashboardScreen
import com.honghoaibao.bbooster.ui.theme.BBoosterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(11, 14, 20)
        window.navigationBarColor = android.graphics.Color.rgb(11, 14, 20)

        setContent {
            BBoosterTheme {
                DashboardScreen()
            }
        }
    }
}
