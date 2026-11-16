package com.honghoaibao.bbooster.core.game

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GameLibraryRepository(
    private val discoveryProvider: GameDiscoveryProvider,
    private val store: GameLibraryStore,
    private val ownPackageName: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(GameLibraryState())
    private var hasLoadedSuccessfully = false

    val state: StateFlow<GameLibraryState> = mutableState.asStateFlow()

    suspend fun load(forceRefresh: Boolean = false) {
        mutex.withLock {
            if (hasLoadedSuccessfully && !forceRefresh) return@withLock

            val wasLoaded = hasLoadedSuccessfully
            mutableState.value = mutableState.value.copy(
                isLoading = !wasLoaded,
                isRefreshing = wasLoaded,
                error = null
            )

            try {
                val snapshot = withContext(ioDispatcher) {
                    discoveryProvider.queryLaunchableApps() to store.readPackageNames()
                }
                publishSnapshot(snapshot.first, snapshot.second)
                hasLoadedSuccessfully = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = GameLibraryError.LOAD_FAILED
                )
            }
        }
    }

    suspend fun addGame(packageName: String): Boolean = mutex.withLock {
        val current = mutableState.value
        val app = current.availableApps.firstOrNull { it.packageName == packageName }
            ?: return@withLock false

        try {
            val saved = withContext(ioDispatcher) {
                val existing = GameLibraryRules.normalizePackageNames(
                    store.readPackageNames(),
                    ownPackageName
                )
                if (packageName in existing) false
                else {
                    store.writePackageNames(existing + packageName)
                    true
                }
            }
            if (!saved) return@withLock false

            mutableState.value = current.copy(
                games = current.games + GameLibraryItem(app.packageName, app.label, true),
                availableApps = current.availableApps.filterNot { it.packageName == packageName },
                error = null
            )
            hasLoadedSuccessfully = true
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = current.copy(error = GameLibraryError.SAVE_FAILED)
            false
        }
    }

    suspend fun removeGame(packageName: String): Boolean = mutex.withLock {
        val current = mutableState.value
        val item = current.games.firstOrNull { it.packageName == packageName }
            ?: return@withLock false

        try {
            withContext(ioDispatcher) {
                val saved = GameLibraryRules.normalizePackageNames(
                    store.readPackageNames(),
                    ownPackageName
                )
                store.writePackageNames(saved.filterNot { it == packageName })
            }

            val restoredApp = if (item.isInstalled) {
                listOf(LaunchableApp(item.packageName, item.label))
            } else {
                emptyList()
            }
            mutableState.value = current.copy(
                games = current.games.filterNot { it.packageName == packageName },
                availableApps = GameLibraryRules.normalizeApps(
                    current.availableApps + restoredApp,
                    ownPackageName
                ),
                error = null
            )
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = current.copy(error = GameLibraryError.REMOVE_FAILED)
            false
        }
    }

    private fun publishSnapshot(
        discoveredApps: List<LaunchableApp>,
        savedPackageNames: List<String>
    ) {
        val apps = GameLibraryRules.normalizeApps(discoveredApps, ownPackageName)
        val saved = GameLibraryRules.normalizePackageNames(savedPackageNames, ownPackageName)
        mutableState.value = GameLibraryState(
            games = GameLibraryRules.buildItems(saved, apps, ownPackageName),
            availableApps = GameLibraryRules.availableApps(apps, saved, ownPackageName)
        )
    }
}
