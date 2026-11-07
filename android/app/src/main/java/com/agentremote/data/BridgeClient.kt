package com.agentremote.data

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class BridgeClient(
    private val tokenStorage: TokenStorage,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val listener: BridgeListener
) {
    interface BridgeListener {
        fun onConnectionStateChange(state: AgentState)
        fun onSnapshot(snapshot: BridgeSnapshot)
        fun onOutputChunk(chunk: String)
        fun onStateUpdate(state: AgentState, activePrompt: PromptData?)
    }

    private val tag = "BridgeClient"
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isManuallyClosed = false
    private var isConnecting = false
    private var retryAttempt = 0
    private val handler = Handler(Looper.getMainLooper())

    @Synchronized
    fun connect() {
        isManuallyClosed = false

        if (!tokenStorage.isConfigured) {
            Log.d(tag, "connect() called but TokenStorage is not configured")
            handler.post {
                listener.onConnectionStateChange(AgentState.DISCONNECTED)
            }
            return
        }

        // Deduplication: Avoid duplicate concurrent connections
        if (isConnecting || webSocket != null) {
            Log.d(tag, "connect() ignored: already connecting or active socket exists")
            return
        }

        val host = tokenStorage.host
        val port = tokenStorage.port

        // Zero-crash validation using HttpUrl
        val validatedHttpUrl: HttpUrl? = try {
            "http://$host:$port/ws".toHttpUrlOrNull() ?: HttpUrl.Builder()
                .scheme("http")
                .host(host)
                .port(port)
                .addPathSegment("ws")
                .build()
        } catch (e: Exception) {
            Log.e(tag, "Malformed host/port configuration ($host:$port): ${e.message}")
            null
        }

        if (validatedHttpUrl == null) {
            Log.e(tag, "Failed to build valid HttpUrl for WebSocket connection")
            handler.post {
                listener.onConnectionStateChange(AgentState.ERROR)
            }
            return
        }

        val wsUrl = "ws://${validatedHttpUrl.host}:${validatedHttpUrl.port}${validatedHttpUrl.encodedPath}"
        val requestBuilder = Request.Builder().url(wsUrl)

        if (tokenStorage.isPaired) {
            val nonce = "${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().replace("-", "")}"
            val sigHex = deviceIdentityManager.signChallengeHex(nonce.toByteArray(Charsets.UTF_8))
            val pubkeyHex = deviceIdentityManager.getPublicKeyHex()
            requestBuilder
                .addHeader("X-Device-Id", tokenStorage.deviceId)
                .addHeader("X-Device-Pubkey", pubkeyHex)
                .addHeader("X-Auth-Nonce", nonce)
                .addHeader("X-Auth-Signature", sigHex)
        } else {
            val token = tokenStorage.authToken
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = try {
            requestBuilder.build()
        } catch (e: Exception) {
            Log.e(tag, "Failed to create OkHttp Request for $wsUrl: ${e.message}")
            handler.post {
                listener.onConnectionStateChange(AgentState.ERROR)
            }
            return
        }

        isConnecting = true
        handler.post {
            listener.onConnectionStateChange(AgentState.CONNECTING)
        }
        Log.d(tag, "Connecting to $wsUrl...")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(tag, "WebSocket connected successfully to $wsUrl")
                synchronized(this@BridgeClient) {
                    isConnecting = false
                    retryAttempt = 0
                }
                handler.post {
                    listener.onConnectionStateChange(AgentState.RUNNING)
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closing: $code / $reason")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closed: $code / $reason")
                synchronized(this@BridgeClient) {
                    isConnecting = false
                    webSocket = null
                }
                handler.post {
                    listener.onConnectionStateChange(AgentState.DISCONNECTED)
                }
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(tag, "WebSocket failure: ${t.message}")
                synchronized(this@BridgeClient) {
                    isConnecting = false
                    webSocket = null
                }
                handler.post {
                    listener.onConnectionStateChange(AgentState.DISCONNECTED)
                }
                scheduleReconnect()
            }
        })
    }

    @Synchronized
    fun disconnect() {
        isManuallyClosed = true
        isConnecting = false
        handler.removeCallbacksAndMessages(null)
        try {
            webSocket?.close(1000, "App paused or closed")
        } catch (e: Exception) {
            webSocket?.cancel()
        }
        webSocket = null
        handler.post {
            listener.onConnectionStateChange(AgentState.DISCONNECTED)
        }
    }

    private fun scheduleReconnect() {
        if (isManuallyClosed || !tokenStorage.isConfigured) return

        retryAttempt++
        val shift = (retryAttempt - 1).coerceIn(0, 4)
        val delayMs = (1 shl shift) * 1000L

        Log.d(tag, "Scheduling reconnect attempt $retryAttempt in ${delayMs}ms")
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!isManuallyClosed && !isConnecting && webSocket == null) {
                connect()
            }
        }, delayMs)
    }

    private fun handleIncomingMessage(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            when (json.optString("type")) {
                "SNAPSHOT" -> {
                    val payload = json.getJSONObject("payload")
                    val snapshot = parseSnapshot(payload)
                    handler.post { listener.onSnapshot(snapshot) }
                }
                "OUTPUT" -> {
                    val chunk = json.optString("data", "")
                    if (chunk.isNotEmpty()) {
                        handler.post { listener.onOutputChunk(chunk) }
                    }
                }
                "STATE_UPDATE" -> {
                    val payload = json.getJSONObject("payload")
                    val stateStr = payload.optString("state", "RUNNING")
                    val state = parseAgentState(stateStr)
                    val promptObj = payload.optJSONObject("active_prompt")
                    val prompt = if (promptObj != null) parsePrompt(promptObj) else null
                    handler.post { listener.onStateUpdate(state, prompt) }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse incoming WebSocket frame: ${e.message}")
        }
    }

    private fun parseSnapshot(obj: JSONObject): BridgeSnapshot {
        val stateStr = obj.optString("state", "IDLE")
        val state = parseAgentState(stateStr)
        val promptObj = obj.optJSONObject("active_prompt")
        val prompt = if (promptObj != null) parsePrompt(promptObj) else null
        return BridgeSnapshot(
            sessionId = obj.optString("session_id", ""),
            state = state,
            history = obj.optString("history", ""),
            activePrompt = prompt,
            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
        )
    }

    private fun parsePrompt(obj: JSONObject): PromptData {
        val options = mutableListOf<PromptOption>()
        val optsArray = obj.optJSONArray("options")
        if (optsArray != null) {
            for (i in 0 until optsArray.length()) {
                val opt = optsArray.getJSONObject(i)
                options.add(
                    PromptOption(
                        key = opt.optString("key", ""),
                        label = opt.optString("label", "")
                    )
                )
            }
        }

        return PromptData(
            mode = obj.optString("mode", "NUMBERED"),
            headline = obj.optString("headline", "Approval Required"),
            target = if (obj.has("target") && !obj.isNull("target")) obj.optString("target") else null,
            diffPreview = if (obj.has("diff_preview") && !obj.isNull("diff_preview")) obj.optString("diff_preview") else null,
            options = options,
            hint = if (obj.has("hint") && !obj.isNull("hint")) obj.optString("hint") else null
        )
    }

    private fun parseAgentState(str: String): AgentState {
        return try {
            AgentState.valueOf(str)
        } catch (e: Exception) {
            AgentState.RUNNING
        }
    }

    fun pairDevice(
        rawHost: String,
        port: Int,
        nonceHex: String,
        totpSecret: String,
        callback: (success: Boolean, message: String?) -> Unit
    ) {
        val (cleanedHost, extractedPort) = TokenStorage.sanitizeHost(rawHost)
        val finalPort = if (port in 1..65535) port else (extractedPort ?: 8765)
        val nonceBytes = DeviceIdentityManager.hexToBytes(nonceHex)
        val signatureHex = deviceIdentityManager.signChallengeHex(nonceBytes)
        val pubkeyHex = deviceIdentityManager.getPublicKeyHex()
        val deviceId = deviceIdentityManager.getDeviceId()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        val jsonBody = JSONObject().apply {
            put("device_id", deviceId)
            put("public_key", pubkeyHex)
            put("signature", signatureHex)
            put("device_name", deviceName)
        }

        val request = Request.Builder()
            .url("http://$cleanedHost:$finalPort/pair")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post { callback(false, e.message ?: "Connection failed") }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    tokenStorage.savePairingConfig(cleanedHost, finalPort, totpSecret, deviceId)
                    handler.post { callback(true, null) }
                } else {
                    handler.post { callback(false, "Pairing rejected (${response.code}): $bodyStr") }
                }
            }
        })
    }

    fun sendRawKey(key: String) {
        val msg = JSONObject().apply {
            put("type", "RAW_KEY")
            put("key", key)
        }
        webSocket?.send(msg.toString())
    }

    fun sendTextInput(text: String) {
        val msg = JSONObject().apply {
            put("type", "TEXT_INPUT")
            put("text", text)
        }
        webSocket?.send(msg.toString())
    }

    fun sendResize(rows: Int, cols: Int) {
        val msg = JSONObject().apply {
            put("type", "RESIZE")
            put("rows", rows)
            put("cols", cols)
        }
        webSocket?.send(msg.toString())
    }

    fun requestBufferRefresh() {
        val msg = JSONObject().apply {
            put("type", "REQUEST_REFRESH")
        }
        webSocket?.send(msg.toString())
    }

    fun sendDismissPrompt() {
        val msg = JSONObject().apply {
            put("type", "DISMISS_PROMPT")
        }
        webSocket?.send(msg.toString())
    }
}
