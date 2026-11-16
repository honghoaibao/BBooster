package com.honghoaibao.bbooster.core.game

import java.util.Locale

data class LaunchableApp(val packageName: String, val label: String)
data class GameLibraryItem(val packageName: String, val label: String, val isInstalled: Boolean)

data class GameLibraryState(
    val games: List<GameLibraryItem> = emptyList(),
    val availableApps: List<LaunchableApp> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: GameLibraryError? = null
)

enum class GameLibraryError { LOAD_FAILED, SAVE_FAILED, REMOVE_FAILED }
enum class GameLaunchResult { STARTED, APP_UNAVAILABLE, FAILED }

object GameLibraryRules {
    private val packageNamePattern = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")

    fun normalizePackageNames(packages: Iterable<String>, ownPackageName: String): List<String> {
        val own = ownPackageName.trim()
        return packages.map(String::trim)
            .filter { it != own && packageNamePattern.matches(it) }
            .distinct()
    }

    fun normalizeApps(apps: Iterable<LaunchableApp>, ownPackageName: String): List<LaunchableApp> =
        apps.mapNotNull { app ->
            val packageName = normalizePackageNames(listOf(app.packageName), ownPackageName).firstOrNull()
                ?: return@mapNotNull null
            LaunchableApp(packageName, app.label.trim().ifBlank { packageName })
        }.distinctBy { it.packageName }
            .sortedWith(compareBy<LaunchableApp> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName })

    fun buildItems(
        savedPackages: Iterable<String>,
        apps: Iterable<LaunchableApp>,
        ownPackageName: String
    ): List<GameLibraryItem> {
        val byPackage = normalizeApps(apps, ownPackageName).associateBy { it.packageName }
        return normalizePackageNames(savedPackages, ownPackageName).map { packageName ->
            val installed = byPackage[packageName]
            GameLibraryItem(packageName, installed?.label ?: packageName, installed != null)
        }
    }

    fun availableApps(
        apps: Iterable<LaunchableApp>,
        savedPackages: Iterable<String>,
        ownPackageName: String
    ): List<LaunchableApp> {
        val saved = normalizePackageNames(savedPackages, ownPackageName).toSet()
        return normalizeApps(apps, ownPackageName).filterNot { it.packageName in saved }
    }
}
