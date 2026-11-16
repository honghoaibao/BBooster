package com.honghoaibao.bbooster.core.game

import android.content.Context
import android.content.SharedPreferences

interface GameLibraryStore {
    fun readPackageNames(): List<String>
    fun writePackageNames(packageNames: List<String>)
}

class SharedPreferencesGameLibraryStore(context: Context) : GameLibraryStore {
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun readPackageNames(): List<String> =
        preferences.getString(KEY_PACKAGES, null).orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()

    override fun writePackageNames(packageNames: List<String>) {
        val serialized = packageNames.map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString("\n")
        preferences.edit().putString(KEY_PACKAGES, serialized).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "bbooster_game_library"
        const val KEY_PACKAGES = "selected_package_names"
    }
}
