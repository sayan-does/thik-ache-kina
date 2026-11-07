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

    var ntfyTopic: String
        get() = sanitizeNtfyTopic(prefs.getString("ntfy_topic", "") ?: "")
        set(value) = prefs.edit().putString("ntfy_topic", sanitizeNtfyTopic(value)).apply()

    val isConfigured: Boolean
        get() {
            val currentHost = host
            return authToken.isNotBlank() &&
                    currentHost.isNotBlank() &&
                    currentHost != DEFAULT_HOST &&
                    !currentHost.contains(" ") &&
                    port in 1..65535
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
