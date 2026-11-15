package com.honghoaibao.bbooster.core.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactorTest {

    @Test
    fun redactsSecretsEmailsAndBearerTokens() {
        val input = "token=secret-value email=person@example.com Authorization: Bearer abc.def.ghi"
        val output = LogRedactor.redact(input)

        assertTrue(output.contains("token=[REDACTED]"))
        assertTrue(output.contains("[REDACTED_EMAIL]"))
        assertTrue(output.contains("Authorization=[REDACTED]"))
        assertFalse(output.contains("secret-value"))
        assertFalse(output.contains("person@example.com"))
        assertFalse(output.contains("abc.def.ghi"))
        assertFalse(LogRedactor.redact("Bearer standalone-token").contains("standalone-token"))
    }

    @Test
    fun redactsCamelCaseSensitiveAssignments() {
        val input = "accessToken=access-secret refreshToken:refresh-secret apiKey=api-secret " +
            "serialNumber=serial-secret androidId=android-secret accountId=account-secret " +
            "userId=user-secret deviceId=device-secret sessionId=session-secret"
        val output = LogRedactor.redact(input)

        listOf(
            "access-secret",
            "refresh-secret",
            "api-secret",
            "serial-secret",
            "android-secret",
            "account-secret",
            "user-secret",
            "device-secret",
            "session-secret"
        ).forEach { secret ->
            assertFalse("Secret was not redacted: $secret", output.contains(secret))
        }
        assertTrue(output.contains("accessToken=[REDACTED]"))
        assertTrue(output.contains("refreshToken=[REDACTED]"))
        assertTrue(output.contains("apiKey=[REDACTED]"))
        assertTrue(output.contains("serialNumber=[REDACTED]"))
    }

    @Test
    fun redactsQuotedJsonKeysAndValues() {
        val input = """{"clientSecret":"client-secret","authorization":"Bearer json.token.value","userId":"user-123"}"""
        val output = LogRedactor.redact(input)

        assertFalse(output.contains("client-secret"))
        assertFalse(output.contains("json.token.value"))
        assertFalse(output.contains("user-123"))
        assertTrue(output.contains("clientSecret=[REDACTED]"))
        assertTrue(output.contains("authorization=[REDACTED]"))
        assertTrue(output.contains("userId=[REDACTED]"))
    }

    @Test
    fun removesLineBreaksAndLimitsEventNames() {
        val output = LogRedactor.redact("first line\nsecond line\tthird line")
        val event = LogRedactor.sanitizeEvent("User Service Connected!")

        assertFalse(output.contains("\n"))
        assertTrue(output.contains("first line second line third line"))
        assertTrue(event.matches(Regex("[a-z0-9_.-]+")))
        assertTrue(LogRedactor.sanitizeEvent("x".repeat(200)).length <= 80)
    }
}
