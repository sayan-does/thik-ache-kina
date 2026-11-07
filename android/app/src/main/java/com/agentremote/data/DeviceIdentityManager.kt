package com.agentremote.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.*
import java.security.spec.ECGenParameterSpec

class DeviceIdentityManager(private val context: Context) {
    companion object {
        private const val TAG = "DeviceIdentityManager"
        private const val KEY_ALIAS = "agent_remote_device_identity_v2"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFS_NAME = "agent_remote_keystore_fallback"
        private const val PREF_FALLBACK_PRIV = "fallback_priv_key"
        private const val PREF_FALLBACK_PUB = "fallback_pub_key"

        fun bytesToHex(bytes: ByteArray): String {
            val hexChars = CharArray(bytes.size * 2)
            val hexArray = "0123456789abcdef".toCharArray()
            for (j in bytes.indices) {
                val v = bytes[j].toInt() and 0xFF
                hexChars[j * 2] = hexArray[v ushr 4]
                hexChars[j * 2 + 1] = hexArray[v and 0x0F]
            }
            return String(hexChars)
        }

        fun hexToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                        Character.digit(hex[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }
    }

    private var inMemoryKeyPair: KeyPair? = null

    init {
        ensureKeyGenerated()
    }

    private fun ensureKeyGenerated() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateKeystoreKey()
            }
        } catch (e: Exception) {
            Log.w(TAG, "AndroidKeyStore unavailable, using fallback provider: ${e.message}")
            ensureFallbackKey()
        }
    }

    private fun generateKeystoreKey() {
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()

        kpg.initialize(spec)
        kpg.generateKeyPair()
        Log.i(TAG, "ECDSA P-256 keypair generated in AndroidKeyStore")
    }

    private fun ensureFallbackKey() {
        if (inMemoryKeyPair != null) return

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedPubHex = prefs.getString(PREF_FALLBACK_PUB, null)
            val savedPrivHex = prefs.getString(PREF_FALLBACK_PRIV, null)

            if (savedPubHex != null && savedPrivHex != null) {
                val pubBytes = hexToBytes(savedPubHex)
                val privBytes = hexToBytes(savedPrivHex)
                val kf = KeyFactory.getInstance("EC")
                val pubKey = kf.generatePublic(java.security.spec.X509EncodedKeySpec(pubBytes))
                val privKey = kf.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(privBytes))
                inMemoryKeyPair = KeyPair(pubKey, privKey)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load fallback key from prefs: ${e.message}")
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()
        inMemoryKeyPair = kp

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(PREF_FALLBACK_PUB, bytesToHex(kp.public.encoded))
                .putString(PREF_FALLBACK_PRIV, bytesToHex(kp.private.encoded))
                .apply()
        } catch (_: Exception) {}
    }

    fun getPublicKeyDer(): ByteArray {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val cert = keyStore.getCertificate(KEY_ALIAS)
            cert?.publicKey?.encoded ?: inMemoryKeyPair!!.public.encoded
        } catch (e: Exception) {
            inMemoryKeyPair!!.public.encoded
        }
    }

    fun getPublicKeyHex(): String {
        return bytesToHex(getPublicKeyDer())
    }

    fun getDeviceId(): String {
        val pubDer = getPublicKeyDer()
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(pubDer)
        return bytesToHex(hash).substring(0, 16)
    }

    fun signChallenge(nonce: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
            if (privateKey != null) {
                signature.initSign(privateKey)
                signature.update(nonce)
                return signature.sign()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Signing with AndroidKeyStore failed, trying fallback: ${e.message}")
        }

        val fallbackPriv = inMemoryKeyPair?.private
            ?: throw IllegalStateException("No signing key available")
        signature.initSign(fallbackPriv)
        signature.update(nonce)
        return signature.sign()
    }

    fun signChallengeHex(nonce: ByteArray): String {
        return bytesToHex(signChallenge(nonce))
    }
}
