package com.honghoaibao.bbooster.core.command

/**
 * Application-owned allowlist of executable actions.
 *
 * This registry deliberately has no API for adding commands at runtime and
 * does not accept user-provided shell strings or arguments.
 */
object CommandRegistry {

    private const val DEFAULT_COMMAND_TIMEOUT_MS = 8_000L
    private const val DEFAULT_MAX_OUTPUT_BYTES = 64 * 1024

    private val registeredCommands: List<CommandDefinition> = listOf(
        definition(CommandAction.CALLER_IDENTITY),
        definition(CommandAction.ANDROID_RELEASE),
        definition(CommandAction.ANDROID_SDK)
    )

    private val commandsById: Map<String, CommandDefinition> =
        registeredCommands.associateBy(CommandDefinition::id)

    val all: List<CommandDefinition>
        get() = registeredCommands

    fun find(actionId: String): CommandDefinition? = commandsById[actionId]

    fun contains(actionId: String): Boolean = commandsById.containsKey(actionId)

    private fun definition(action: CommandAction) = CommandDefinition(
        action = action,
        accessType = CommandAccessType.READ_ONLY,
        executionMode = CommandExecutionMode.ONE_SHOT,
        supportedTransports = setOf(
            CommandTransportType.USER_SERVICE,
            CommandTransportType.DIRECT_PROCESS
        ),
        requiredCapabilities = setOf(CommandCapability.PROCESS_EXECUTION),
        timeoutMs = DEFAULT_COMMAND_TIMEOUT_MS,
        maxOutputBytes = DEFAULT_MAX_OUTPUT_BYTES,
        retryPolicy = CommandRetryPolicy.ONLY_WHEN_NOT_DISPATCHED,
        requiresShizukuPermission = true,
        requiresConfirmation = false
    )
}
