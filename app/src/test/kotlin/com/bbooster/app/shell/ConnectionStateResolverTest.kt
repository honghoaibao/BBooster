package com.bbooster.app.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionStateResolverTest {

    private fun snapshot(
        installed: Boolean = true,
        binderAlive: Boolean = true,
        permissionGranted: Boolean = true,
        permanentlyDenied: Boolean = false,
        verifiedUid: Int? = null,
        failure: String? = null,
    ) = ShizukuSnapshot(installed, binderAlive, permissionGranted, permanentlyDenied, verifiedUid, failure)

    @Test
    fun notInstalled_whenBinderDeadAndAppMissing() {
        val s = snapshot(installed = false, binderAlive = false, permissionGranted = false)
        assertEquals(ConnectionState.ShizukuNotInstalled, ConnectionStateResolver.resolve(s))
    }

    @Test
    fun notRunning_whenBinderDeadButAppInstalled() {
        val s = snapshot(binderAlive = false, permissionGranted = false)
        assertEquals(ConnectionState.ShizukuNotRunning, ConnectionStateResolver.resolve(s))
    }

    @Test
    fun binderAlive_winsOverMissingPackageVisibility() {
        // e.g. a rooted Sui setup: binder is there even though the Shizuku package isn't visible.
        val s = snapshot(installed = false, verifiedUid = 0)
        assertEquals(ConnectionState.Ready(0), ConnectionStateResolver.resolve(s))
    }

    @Test
    fun permissionRequired_thenDenied() {
        assertEquals(
            ConnectionState.PermissionRequired,
            ConnectionStateResolver.resolve(snapshot(permissionGranted = false)),
        )
        assertEquals(
            ConnectionState.PermissionDenied,
            ConnectionStateResolver.resolve(snapshot(permissionGranted = false, permanentlyDenied = true)),
        )
    }

    @Test
    fun connecting_untilIdentityVerified() {
        assertEquals(ConnectionState.Connecting, ConnectionStateResolver.resolve(snapshot()))
    }

    @Test
    fun ready_carriesVerifiedUid() {
        assertEquals(ConnectionState.Ready(2000), ConnectionStateResolver.resolve(snapshot(verifiedUid = 2000)))
    }

    @Test
    fun failure_reportedOnlyOnceGranted() {
        assertEquals(
            ConnectionState.Failed("boom"),
            ConnectionStateResolver.resolve(snapshot(failure = "boom")),
        )
        // A stale failure must not mask "Shizuku stopped".
        assertEquals(
            ConnectionState.ShizukuNotRunning,
            ConnectionStateResolver.resolve(snapshot(binderAlive = false, permissionGranted = false, failure = "boom")),
        )
    }
}
