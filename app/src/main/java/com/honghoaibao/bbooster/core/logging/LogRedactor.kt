package com.honghoaibao.bbooster.core.logging

import java.util.Locale

internal object LogRedactor {

    private const val MAX_EVENT_LENGTH = 80
    private const val MAX_MESSAGE_LENGTH = 4_000

    private val sensitiveAssignment = Regex(
        """(?i)\b(password|passwd|token|access_token|refresh_token|authorization|cookie|secret|api[_-]?key|serial(?:_number)?|android[_-]?id|account(?:_id)?)\b\s*[:=]\s*(?:Bearer\s+)?("[^"]*"|'[^']*'|[^\s,;]+)"""
    )
    private val bearerToken = Regex("""(?i)\bBearer\s+[^\s,;]+""")
    private val emailAddress = Regex(
        """(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b"""
    )

    fun sanitizeEvent(event: String): String {
        val normalized = event.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_.-]"), "_")
            .trim('_')
            .take(MAX_EVENT_LENGTH)
        return normalized.ifBlank { "unspecified_event" }
    }

    fun redact(message: String): String {
        var sanitized = message
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .trim()

        sanitized = sensitiveAssignment.replace(sanitized) { match ->
            match.groupValues[1] + "=[REDACTED]"
        }
        sanitized = bearerToken.replace(sanitized, "Bearer [REDACTED]")
        sanitized = emailAddress.replace(sanitized, "[REDACTED_EMAIL]")

        return sanitized.take(MAX_MESSAGE_LENGTH)
    }
}
