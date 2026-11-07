package com.agentremote.data

import android.content.Context
import android.content.SharedPreferences

class TokenStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("agent_remote_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_HOST = "100.x.y.z"
        const val DEFAULT_PORT = 8765

        fun sanitizeHost(raw: String): Pair<String, Int?> {
            var cleaned = raw.trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")

            // Strip scheme if present
            cleaned = cleaned
                .removePrefix("ws://")
                .removePrefix("wss://")
                .removePrefix("http://")
                .removePrefix("https://")

            // Strip trailing path if present (e.g., /ws or /)
            val pathIndex = cleaned.indexOf('/')
            if (pathIndex != -1) {
                cleaned = cleaned.substring(0, pathIndex)
            }

            // Check for port delimiter (: or ;)
            var extractedPort: Int? = null
            val delimiterIndex = cleaned.indexOfFirst { it == ':' || it == ';' }
            if (delimiterIndex != -1) {
                val portStr = cleaned.substring(delimiterIndex + 1).trim()
                extractedPort = portStr.toIntOrNull()?.takeIf { it in 1..65535 }
                cleaned = cleaned.substring(0, delimiterIndex).trim()
            }

            // In case of multiple colons or leftover port attachments
            if (cleaned.contains(':')) {
                cleaned = cleaned.substringBefore(':').trim()
            }

            // Remove any leftover whitespace or control characters
            cleaned = cleaned.filterNot { it.isWhitespace() }

            return Pair(if (cleaned.isEmpty()) DEFAULT_HOST else cleaned, extractedPort)
        }

        fun sanitizeToken(raw: String): String {
            return raw.trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .removePrefix("Bearer ")
                .removePrefix("bearer ")
                .trim()
        }

        fun sanitizeNtfyTopic(raw: String): String {
            var cleaned = raw.trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
            if (cleaned.contains("ntfy.sh/")) {
                cleaned = cleaned.substringAfter("ntfy.sh/").substringBefore('/')
            }
            return cleaned.filterNot { it.isWhitespace() }
        }
    }

    var host: String
        get() {
            val raw = prefs.getString("server_host", DEFAULT_HOST) ?: DEFAULT_HOST
            val (sanitized, _) = sanitizeHost(raw)
            return sanitized
        }
        set(value) {
            val (sanitizedHost, extractedPort) = sanitizeHost(value)
            prefs.edit().putString("server_host", sanitizedHost).apply()
            if (extractedPort != null) {
                port = extractedPort
            }
        }

    var port: Int
        get() {
            val p = prefs.getInt("server_port", DEFAULT_PORT)
            return if (p in 1..65535) p else DEFAULT_PORT
        }
        set(value) {
            val safePort = if (value in 1..65535) value else DEFAULT_PORT
            prefs.edit().putInt("server_port", safePort).apply()
        }

    var authToken: String
        get() = sanitizeToken(prefs.getString("auth_token", "") ?: "")
        set(value) = prefs.edit().putString("auth_token", sanitizeToken(value)).apply()

    var isPaired: Boolean
        get() = prefs.getBoolean("is_paired", false)
        set(value) = prefs.edit().putBoolean("is_paired", value).apply()

    var totpSecret: String
        get() = prefs.getString("totp_secret", "") ?: ""
        set(value) = prefs.edit().putString("totp_secret", value).apply()

    var deviceId: String
        get() = prefs.getString("device_id", "") ?: ""
        set(value) = prefs.edit().putString("device_id", value).apply()

    var ntfyTopic: String
        get() = sanitizeNtfyTopic(prefs.getString("ntfy_topic", "") ?: "")
        set(value) = prefs.edit().putString("ntfy_topic", sanitizeNtfyTopic(value)).apply()

    val isConfigured: Boolean
        get() {
            val currentHost = host
            val hasAuth = isPaired || authToken.isNotBlank()
            return hasAuth &&
                    currentHost.isNotBlank() &&
                    currentHost != DEFAULT_HOST &&
                    !currentHost.contains(" ") &&
                    port in 1..65535
        }

    fun savePairingConfig(
        hostAddress: String,
        portNumber: Int,
        secret: String,
        devId: String
    ) {
        val (cleanedHost, extractedPort) = sanitizeHost(hostAddress)
        host = cleanedHost
        port = if (portNumber in 1..65535) portNumber else (extractedPort ?: DEFAULT_PORT)
        totpSecret = secret
        deviceId = devId
        isPaired = true
    }

    fun clearPairing() {
        prefs.edit()
            .putBoolean("is_paired", false)
            .remove("totp_secret")
            .remove("device_id")
            .apply()
    }

    fun saveConfig(
        rawHost: String,
        rawPort: String? = null,
        rawToken: String? = null,
        rawNtfy: String? = null
    ) {
        val (cleanedHost, extractedPort) = sanitizeHost(rawHost)
        host = cleanedHost
        val finalPort = rawPort?.toIntOrNull() ?: extractedPort ?: port
        port = finalPort
        if (rawToken != null) authToken = rawToken
        if (rawNtfy != null) ntfyTopic = rawNtfy
    }
}
