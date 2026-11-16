package com.honghoaibao.bbooster.core.game

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build

class GameLaunchManager(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun launch(packageName: String): GameLaunchResult {
        if (packageName.isBlank() || packageName == appContext.packageName) {
            return GameLaunchResult.APP_UNAVAILABLE
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getLaunchIntentSenderForPackage(packageName)
                    .sendIntent(appContext, 0, null, null, null)
            } else {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                    ?: return GameLaunchResult.APP_UNAVAILABLE
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
            }
            GameLaunchResult.STARTED
        } catch (_: PackageManager.NameNotFoundException) {
            GameLaunchResult.APP_UNAVAILABLE
        } catch (_: IntentSender.SendIntentException) {
            GameLaunchResult.APP_UNAVAILABLE
        } catch (_: ActivityNotFoundException) {
            GameLaunchResult.APP_UNAVAILABLE
        } catch (_: SecurityException) {
            GameLaunchResult.FAILED
        } catch (_: RuntimeException) {
            GameLaunchResult.FAILED
        }
    }
}
