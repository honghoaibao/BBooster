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
    fun removesLineBreaksAndLimitsEventNames() {
        val output = LogRedactor.redact("first line\nsecond line\tthird line")
        val event = LogRedactor.sanitizeEvent("User Service Connected!")

        assertFalse(output.contains("\n"))
        assertTrue(output.contains("first line second line third line"))
        assertTrue(event.matches(Regex("[a-z0-9_.-]+")))
        assertTrue(LogRedactor.sanitizeEvent("x".repeat(200)).length <= 80)
    }
}
