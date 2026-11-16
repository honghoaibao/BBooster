package com.honghoaibao.bbooster.core.game

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLibraryRepositoryTest {

    @Test
    fun loadMarksInstalledAndMissingEntriesAndHidesSelectedApps() = runBlocking {
        val repository = repository(
            saved = listOf("com.example.installed", "com.example.removed"),
            apps = listOf(
                LaunchableApp("com.example.installed", "Installed Game"),
                LaunchableApp("com.example.newgame", "New Game"),
                LaunchableApp(OWN_PACKAGE, "BBooster")
            )
        )

        repository.load()

        assertEquals(
            listOf(
                GameLibraryItem("com.example.installed", "Installed Game", true),
                GameLibraryItem("com.example.removed", "com.example.removed", false)
            ),
            repository.state.value.games
        )
        assertEquals(
            listOf(LaunchableApp("com.example.newgame", "New Game")),
            repository.state.value.availableApps
        )
    }

    @Test
    fun addingAnAvailableAppPersistsItAndRemovesItFromPicker() = runBlocking {
        val store = FakeStore()
        val repository = repository(
            store = store,
            apps = listOf(LaunchableApp("com.example.game", "Example Game"))
        )
        repository.load()

        assertTrue(repository.addGame("com.example.game"))
        assertEquals(listOf("com.example.game"), store.packages)
        assertEquals(
            listOf(GameLibraryItem("com.example.game", "Example Game", true)),
            repository.state.value.games
        )
        assertTrue(repository.state.value.availableApps.isEmpty())
        assertFalse(repository.addGame("com.example.game"))
    }

    @Test
    fun removingAnInstalledGameReturnsItToAvailableApps() = runBlocking {
        val repository = repository(
            saved = listOf("com.example.game"),
            apps = listOf(LaunchableApp("com.example.game", "Example Game"))
        )
        repository.load()

        assertTrue(repository.removeGame("com.example.game"))
        assertTrue(repository.state.value.games.isEmpty())
        assertEquals(
            listOf(LaunchableApp("com.example.game", "Example Game")),
            repository.state.value.availableApps
        )
    }

    @Test
    fun removingMissingGameDoesNotInventAnAvailableApp() = runBlocking {
        val repository = repository(saved = listOf("com.example.removed"))
        repository.load()

        assertTrue(repository.removeGame("com.example.removed"))
        assertTrue(repository.state.value.games.isEmpty())
        assertTrue(repository.state.value.availableApps.isEmpty())
    }

    @Test
    fun refreshUpdatesInstalledStateAndPreservesSavedPackages() = runBlocking {
        val store = FakeStore(listOf("com.example.game"))
        val provider = FakeDiscoveryProvider(
            listOf(LaunchableApp("com.example.game", "Example Game"))
        )
        val repository = GameLibraryRepository(provider, store, OWN_PACKAGE)

        repository.load()
        assertTrue(repository.state.value.games.single().isInstalled)

        provider.apps = emptyList()
        repository.load(forceRefresh = true)

        assertFalse(repository.state.value.games.single().isInstalled)
        assertEquals(listOf("com.example.game"), store.packages)
    }

    @Test
    fun packageNormalizationRemovesDuplicatesInvalidValuesAndOwnPackage() {
        assertEquals(
            listOf("com.example.game"),
            GameLibraryRules.normalizePackageNames(
                listOf(
                    "com.example.game",
                    "com.example.game",
                    " ",
                    "not-a-package",
                    OWN_PACKAGE
                ),
                OWN_PACKAGE
            )
        )
    }

    private fun repository(
        saved: List<String> = emptyList(),
        apps: List<LaunchableApp> = emptyList(),
        store: FakeStore = FakeStore(saved)
    ) = GameLibraryRepository(
        discoveryProvider = FakeDiscoveryProvider(apps),
        store = store,
        ownPackageName = OWN_PACKAGE
    )

    private class FakeStore(initial: List<String> = emptyList()) : GameLibraryStore {
        var packages: List<String> = initial.toList()
        override fun readPackageNames(): List<String> = packages.toList()
        override fun writePackageNames(packageNames: List<String>) {
            packages = packageNames.toList()
        }
    }

    private class FakeDiscoveryProvider(var apps: List<LaunchableApp>) : GameDiscoveryProvider {
        override fun queryLaunchableApps(): List<LaunchableApp> = apps.toList()
    }

    private companion object {
        const val OWN_PACKAGE = "com.honghoaibao.bbooster"
    }
}
