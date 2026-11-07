package com.agentremote

import com.agentremote.data.DeviceIdentityManager
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

class DeviceIdentityTest {

    @Test
    fun testHexConversion() {
        val original = byteArrayOf(0x00, 0x0F, 0x10, 0xFF.toByte(), 0xAA.toByte())
        val hex = DeviceIdentityManager.bytesToHex(original)
        assertEquals("000f10ffaa", hex)
        val roundtrip = DeviceIdentityManager.hexToBytes(hex)
        assertArrayEquals(original, roundtrip)
    }

    @Test
    fun testEcdsaP256SigningAndVerification() {
        // Generate NIST P-256 keypair
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()

        val nonce = "test_challenge_nonce_1234567890".toByteArray(Charsets.UTF_8)

        // Sign with SHA256withECDSA
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(kp.private)
        signer.update(nonce)
        val signatureBytes = signer.sign()

        assertTrue(signatureBytes.isNotEmpty())

        // Verify with public key
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(kp.public)
        verifier.update(nonce)
        assertTrue(verifier.verify(signatureBytes))

        // Ensure invalid nonce fails verification
        val badVerifier = Signature.getInstance("SHA256withECDSA")
        badVerifier.initVerify(kp.public)
        badVerifier.update("tampered_nonce".toByteArray(Charsets.UTF_8))
        assertFalse(badVerifier.verify(signatureBytes))
    }

    @Test
    fun testDeviceIdDerivation() {
        val dummyPub = "0123456789abcdef".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(dummyPub)
        val deviceId = DeviceIdentityManager.bytesToHex(hash).substring(0, 16)
        assertEquals(16, deviceId.length)
        assertTrue(deviceId.all { it in "0123456789abcdef" })
    }
}
