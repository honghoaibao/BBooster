package com.honghoaibao.bbooster.core.command

/**
 * Describes a command that is declared by the application.
 *
 * Definitions are created by [CommandRegistry]. A command never receives shell
 * text or arguments from the UI or another untrusted input source.
 */
data class CommandDefinition internal constructor(
    val action: CommandAction,
    val accessType: CommandAccessType,
    val executionMode: CommandExecutionMode,
    val supportedTransports: Set<CommandTransportType>,
    val requiredCapabilities: Set<CommandCapability>,
    val timeoutMs: Long,
    val maxOutputBytes: Int,
    val retryPolicy: CommandRetryPolicy,
    val requiresShizukuPermission: Boolean,
    val requiresConfirmation: Boolean
) {
    val id: String
        get() = action.id

    internal val arguments: List<String>
        get() = action.arguments

    init {
        require(timeoutMs > 0L) { "Command timeout must be positive." }
        require(maxOutputBytes > 0) { "Command output limit must be positive." }
        require(supportedTransports.isNotEmpty()) {
            "At least one transport must be declared."
        }
        require(action.arguments.isNotEmpty()) {
            "Registered commands must have an explicit argument list."
        }
        require(accessType != CommandAccessType.MUTATING || requiresConfirmation) {
            "Mutating commands must require explicit user confirmation."
        }
        require(
            executionMode != CommandExecutionMode.CONTINUOUS ||
                CommandTransportType.USER_SERVICE in supportedTransports
        ) {
            "Continuous commands require UserService support."
        }
    }
}

enum class CommandAccessType {
    READ_ONLY,
    MUTATING
}

enum class CommandExecutionMode {
    ONE_SHOT,
    CONTINUOUS
}

enum class CommandTransportType {
    USER_SERVICE,
    DIRECT_PROCESS
}

enum class CommandCapability {
    PROCESS_EXECUTION,
    PERSISTENT_SERVICE,
    AIDL_CALLBACK
}

enum class CommandRetryPolicy {
    NEVER,
    ONLY_WHEN_NOT_DISPATCHED
}
