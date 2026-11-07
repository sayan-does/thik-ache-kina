package com.agentremote

import com.agentremote.data.TotpHelper
import org.junit.Assert.*
import org.junit.Test

class TotpHelperTest {

    @Test
    fun testBase32Decoding() {
        val secret = "JBSWY3DPEHPK3PXP" // Standard test vector secret
        val bytes = TotpHelper.decodeBase32(secret)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun testTotpGenerationFormat() {
        val secret = "CDWJY2QIELJ2PKMBCKXH6XW2YFDOK4DZ"
        val code = TotpHelper.generateTotp(secret)
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun testTotpVerificationCurrentWindow() {
        val secret = "CDWJY2QIELJ2PKMBCKXH6XW2YFDOK4DZ"
        val currentCode = TotpHelper.generateTotp(secret)
        assertTrue(TotpHelper.verifyTotp(secret, currentCode, window = 1))
        assertFalse(TotpHelper.verifyTotp(secret, "000000", window = 1))
    }
}
