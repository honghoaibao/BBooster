package com.bbooster.app.shell

/**
 * A package name that passed strict validation. Segments must start with a letter and contain only
 * letters, digits or underscore, with at least one dot. This is what keeps shell metacharacters out of
 * argv: there is no way to build a [PackageName] from arbitrary text.
 */
@JvmInline
value class PackageName private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 255
        private val PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

        fun of(raw: String): PackageName? =
            if (raw.length <= MAX_LENGTH && PATTERN.matches(raw)) PackageName(raw) else null
    }
}

/**
 * A package that may be force-stopped. Hard backstop only: it rejects a few core system packages.
 * The real "never stop" policy (dialer, SMS, launcher, keyboard, user-pinned apps) is a boost-layer
 * concern and arrives in phase 2.
 */
@JvmInline
value class KillablePackage private constructor(val pkg: PackageName) {
    companion object {
        private val PROTECTED = setOf(
            "com.android.systemui",
            "com.android.phone",
            "com.android.settings",
            "com.android.server.telecom",
            "com.android.providers.telephony",
            "com.android.shell",
        )

        fun of(raw: String): KillablePackage? {
            val pkg = PackageName.of(raw) ?: return null
            return if (pkg.value in PROTECTED) null else KillablePackage(pkg)
        }
    }
}

/** Settings keys BBooster is allowed to touch. Anything else is not expressible as a command. */
enum class SettingKey(val namespace: String, val key: String) {
    MIN_REFRESH_RATE("system", "min_refresh_rate"),
    PEAK_REFRESH_RATE("system", "peak_refresh_rate"),
    WINDOW_ANIMATION_SCALE("global", "window_animation_scale"),
    TRANSITION_ANIMATION_SCALE("global", "transition_animation_scale"),
    ANIMATOR_DURATION_SCALE("global", "animator_duration_scale"),
    LOW_POWER("global", "low_power"),
    ;

    companion object {
        fun find(namespace: String, key: String): SettingKey? =
            entries.firstOrNull { it.namespace == namespace && it.key == key }
    }
}

/** Numeric setting value only (all allowed keys are numeric). Rejects everything else. */
@JvmInline
value class SettingValue private constructor(val raw: String) {
    companion object {
        private val PATTERN = Regex("-?[0-9]{1,6}(\\.[0-9]{1,4})?")

        fun of(raw: String): SettingValue? = if (PATTERN.matches(raw)) SettingValue(raw) else null
    }
}

/**
 * The complete, closed set of shell commands BBooster can run. Each command maps to a fixed argv
 * (never a shell string), and [fromArgv] is the exact inverse, so the privileged service can
 * re-validate whatever arrives over Binder with the same rules.
 */
sealed interface ShellCommand {
    fun toArgv(): List<String>

    /** Prints the UID the privileged process runs as: 2000 (shell) or 0 (root). */
    data object Whoami : ShellCommand {
        override fun toArgv() = listOf("id", "-u")
    }

    data class SettingsGet(val key: SettingKey) : ShellCommand {
        override fun toArgv() = listOf("settings", "get", key.namespace, key.key)
    }

    data class SettingsPut(val key: SettingKey, val value: SettingValue) : ShellCommand {
        override fun toArgv() = listOf("settings", "put", key.namespace, key.key, value.raw)
    }

    data class ForceStop(val target: KillablePackage) : ShellCommand {
        override fun toArgv() = listOf("am", "force-stop", target.pkg.value)
    }

    companion object {
        /** Returns the command only if [argv] is exactly the argv that command would produce. */
        fun fromArgv(argv: List<String>): ShellCommand? {
            val parsed: ShellCommand? = when {
                argv == Whoami.toArgv() -> Whoami
                argv.size == 4 && argv[0] == "settings" && argv[1] == "get" ->
                    SettingKey.find(argv[2], argv[3])?.let { SettingsGet(it) }
                argv.size == 5 && argv[0] == "settings" && argv[1] == "put" -> {
                    val key = SettingKey.find(argv[2], argv[3])
                    val value = SettingValue.of(argv[4])
                    if (key != null && value != null) SettingsPut(key, value) else null
                }
                argv.size == 3 && argv[0] == "am" && argv[1] == "force-stop" ->
                    KillablePackage.of(argv[2])?.let { ForceStop(it) }
                else -> null
            }
            return parsed?.takeIf { it.toArgv() == argv }
        }
    }
}
