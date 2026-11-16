package com.bbooster.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bbooster.app.ui.home.HomeScreen
import com.bbooster.app.ui.onboarding.OnboardingScreen

object Routes {
    const val HOME = "home"
    const val ONBOARDING = "onboarding"
}

@Composable
fun BBoosterNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onOpenOnboarding = { navController.navigate(Routes.ONBOARDING) })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onBack = { navController.popBackStack() })
        }
    }
}
