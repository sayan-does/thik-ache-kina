package com.agentremote.data

import android.content.Context
import android.content.SharedPreferences

class TokenStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("agent_remote_prefs", Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString("server_host", "100.x.y.z") ?: "100.x.y.z"
        set(value) = prefs.edit().putString("server_host", value.trim()).apply()

    var port: Int
        get() = prefs.getInt("server_port", 8765)
        set(value) = prefs.edit().putInt("server_port", value).apply()

    var authToken: String
        get() = prefs.getString("auth_token", "") ?: ""
        set(value) = prefs.edit().putString("auth_token", value.trim()).apply()

    var ntfyTopic: String
        get() = prefs.getString("ntfy_topic", "") ?: ""
        set(value) = prefs.edit().putString("ntfy_topic", value.trim()).apply()

    val isConfigured: Boolean
        get() = authToken.isNotEmpty() && host.isNotEmpty() && host != "100.x.y.z"
}
