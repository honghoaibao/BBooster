package com.honghoaibao.bbooster.core.shizuku.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuNewProcessAdapterTest {

    @Test
    fun installedShizukuApiExposesExpectedPrivateCompatibilitySignature() {
        val adapter = ShizukuNewProcessAdapter()

        assertEquals(
            ProcessStarterAvailability.AVAILABLE,
            adapter.checkAvailability()
        )
    }
}
