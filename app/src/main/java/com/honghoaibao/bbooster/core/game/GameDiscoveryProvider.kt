package com.honghoaibao.bbooster.core.game

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

interface GameDiscoveryProvider {
    fun queryLaunchableApps(): List<LaunchableApp>
}

class AndroidGameDiscoveryProvider(context: Context) : GameDiscoveryProvider {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    override fun queryLaunchableApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            queryActivitiesLegacy(intent)
        }

        return GameLibraryRules.normalizeApps(
            resolved.mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                val packageName = activity.packageName
                val label = runCatching { info.loadLabel(packageManager).toString() }
                    .getOrDefault(packageName)
                LaunchableApp(packageName, label)
            },
            appContext.packageName
        )
    }

    @Suppress("DEPRECATION")
    private fun queryActivitiesLegacy(intent: Intent) =
        packageManager.queryIntentActivities(intent, 0)
}
