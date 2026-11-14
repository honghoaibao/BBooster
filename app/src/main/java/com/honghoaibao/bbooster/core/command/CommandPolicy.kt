package com.honghoaibao.bbooster.core.command

data class CommandPolicyContext(
    val binderAvailable: Boolean,
    val shizukuPermissionGranted: Boolean,
    val userConfirmed: Boolean = false
)

sealed interface CommandAuthorization {
    data class Allowed(
        val definition: CommandDefinition
    ) : CommandAuthorization

    data class Denied(
        val reason: CommandPolicyRejection
    ) : CommandAuthorization
}

enum class CommandPolicyRejection {
    UNKNOWN_COMMAND,
    SHIZUKU_UNAVAILABLE,
    PERMISSION_REQUIRED,
    USER_CONFIRMATION_REQUIRED
}

/**
 * Validates a registry entry and the current authorization context before
 * either transport is allowed to receive a command.
 */
object CommandPolicy {

    fun authorize(
        actionId: String,
        context: CommandPolicyContext
    ): CommandAuthorization {
        val definition = CommandRegistry.find(actionId)
            ?: return CommandAuthorization.Denied(
                CommandPolicyRejection.UNKNOWN_COMMAND
            )

        return evaluate(definition, context)
    }

    internal fun evaluate(
        definition: CommandDefinition,
        context: CommandPolicyContext
    ): CommandAuthorization {
        if (context.binderAvailable.not()) {
            return CommandAuthorization.Denied(
                CommandPolicyRejection.SHIZUKU_UNAVAILABLE
            )
        }

        if (definition.requiresShizukuPermission &&
            context.shizukuPermissionGranted.not()
        ) {
            return CommandAuthorization.Denied(
                CommandPolicyRejection.PERMISSION_REQUIRED
            )
        }

        if (definition.requiresConfirmation && context.userConfirmed.not()) {
            return CommandAuthorization.Denied(
                CommandPolicyRejection.USER_CONFIRMATION_REQUIRED
            )
        }

        return CommandAuthorization.Allowed(definition)
    }
}
