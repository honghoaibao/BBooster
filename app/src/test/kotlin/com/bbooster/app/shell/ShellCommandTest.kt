package com.bbooster.app.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShellCommandTest {

    // --- argv generation -------------------------------------------------------------------

    @Test
    fun whoami_argv() {
        assertEquals(listOf("id", "-u"), ShellCommand.Whoami.toArgv())
    }

    @Test
    fun settingsGet_argv() {
        val cmd = ShellCommand.SettingsGet(SettingKey.PEAK_REFRESH_RATE)
        assertEquals(listOf("settings", "get", "system", "peak_refresh_rate"), cmd.toArgv())
    }

    @Test
    fun settingsPut_argv() {
        val cmd = ShellCommand.SettingsPut(SettingKey.WINDOW_ANIMATION_SCALE, SettingValue.of("0.5")!!)
        assertEquals(listOf("settings", "put", "global", "window_animation_scale", "0.5"), cmd.toArgv())
    }

    @Test
    fun forceStop_argv() {
        val cmd = ShellCommand.ForceStop(KillablePackage.of("com.example.game")!!)
        assertEquals(listOf("am", "force-stop", "com.example.game"), cmd.toArgv())
    }

    // --- PackageName -----------------------------------------------------------------------

    @Test
    fun packageName_acceptsRealisticNames() {
        assertNotNull(PackageName.of("com.tencent.ig"))
        assertNotNull(PackageName.of("com.supercell.clashofclans"))
        assertNotNull(PackageName.of("org.example.app_v2"))
    }

    @Test
    fun packageName_rejectsInjectionAndMalformed() {
        val bad = listOf(
            "",
            "com",
            "com.",
            ".com.foo",
            "com..foo",
            "com.foo;reboot",
            "com.foo bar",
            "com.foo\nreboot",
            "\$(id).foo",
            "com.foo/../bar",
            "1com.foo",
            "com.1foo",
            "a".repeat(PackageName.MAX_LENGTH) + ".x",
        )
        bad.forEach { assertNull("should reject: $it", PackageName.of(it)) }
    }

    @Test
    fun killablePackage_rejectsProtectedSystemPackages() {
        assertNull(KillablePackage.of("com.android.systemui"))
        assertNull(KillablePackage.of("com.android.phone"))
        assertNull(KillablePackage.of("com.android.settings"))
    }

    // --- SettingValue ----------------------------------------------------------------------

    @Test
    fun settingValue_acceptsNumbers() {
        listOf("0", "1", "0.5", "120", "120.0", "-1").forEach {
            assertNotNull("should accept: $it", SettingValue.of(it))
        }
    }

    @Test
    fun settingValue_rejectsEverythingElse() {
        listOf("", " 1", "1 ", "1e3", "NaN", "1;reboot", "1.", ".5", "1234567", "0.12345", "a").forEach {
            assertNull("should reject: $it", SettingValue.of(it))
        }
    }

    // --- fromArgv: round trip --------------------------------------------------------------

    @Test
    fun fromArgv_roundTripsEveryCommand() {
        val commands = listOf(
            ShellCommand.Whoami,
            ShellCommand.SettingsGet(SettingKey.LOW_POWER),
            ShellCommand.SettingsPut(SettingKey.MIN_REFRESH_RATE, SettingValue.of("120.0")!!),
            ShellCommand.ForceStop(KillablePackage.of("com.example.game")!!),
        )
        commands.forEach { assertEquals(it, ShellCommand.fromArgv(it.toArgv())) }
    }

    @Test
    fun settingKey_everyEntryRoundTrips() {
        SettingKey.entries.forEach {
            assertEquals(it, SettingKey.find(it.namespace, it.key))
            val put = ShellCommand.SettingsPut(it, SettingValue.of("1")!!)
            assertEquals(put, ShellCommand.fromArgv(put.toArgv()))
        }
    }

    // --- fromArgv: rejection ---------------------------------------------------------------

    @Test
    fun fromArgv_rejectsNonWhitelisted() {
        val bad = listOf(
            emptyList<String>(),
            listOf("sh", "-c", "id"),
            listOf("id"),
            listOf("id", "-u", "extra"),
            listOf("reboot"),
            listOf("settings", "get", "global", "adb_enabled"),
            listOf("settings", "put", "global", "adb_enabled", "0"),
            listOf("settings", "put", "system", "min_refresh_rate", "1;reboot"),
            listOf("settings", "put", "system", "min_refresh_rate"),
            listOf("settings", "delete", "system", "min_refresh_rate"),
            listOf("am", "force-stop", "com.android.systemui"),
            listOf("am", "force-stop", "com.example.game", "extra"),
            listOf("am", "force-stop", "com.example.game;reboot"),
            listOf("am", "kill-all"),
            listOf("pm", "uninstall", "com.example.game"),
        )
        bad.forEach { assertNull("should reject: $it", ShellCommand.fromArgv(it)) }
    }
}
