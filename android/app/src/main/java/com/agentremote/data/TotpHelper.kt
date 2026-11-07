package com.agentremote.data

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TotpHelper {
    private const val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decodeBase32(input: String): ByteArray {
        val cleanInput = input.trim().uppercase().filter { it in BASE32_CHARS }
        var buffer = 0
        var bitsLeft = 0
        val output = mutableListOf<Byte>()

        for (c in cleanInput) {
            val value = BASE32_CHARS.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return output.toByteArray()
    }

    fun generateTotp(secretBase32: String, timeStepSeconds: Long = 30L, digits: Int = 6): String {
        val key = decodeBase32(secretBase32)
        if (key.isEmpty()) return "000000"

        val counter = System.currentTimeMillis() / 1000L / timeStepSeconds
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(counterBytes)

        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val truncatedHash = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        var modulo = 1
        for (i in 0 until digits) modulo *= 10
        val code = truncatedHash % modulo

        return String.format("%0${digits}d", code)
    }

    fun verifyTotp(secretBase32: String, code: String, window: Int = 1): Boolean {
        val cleanCode = code.trim()
        if (cleanCode.length != 6) return false

        val key = decodeBase32(secretBase32)
        if (key.isEmpty()) return false

        val currentCounter = System.currentTimeMillis() / 1000L / 30L
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))

        for (i in -window..window) {
            val counter = currentCounter + i
            val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
            val hash = mac.doFinal(counterBytes)

            val offset = (hash[hash.size - 1].toInt() and 0x0F)
            val truncatedHash = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)

            val validCode = String.format("%06d", truncatedHash % 1000000)
            if (validCode == cleanCode) return true
        }
        return false
    }
}
