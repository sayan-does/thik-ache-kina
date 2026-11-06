package com.agentremote.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BridgeClient(
    private val tokenStorage: TokenStorage,
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
    private var retryAttempt = 0
    private val handler = Handler(Looper.getMainLooper())

    fun connect() {
        isManuallyClosed = false
        val host = tokenStorage.host
        val port = tokenStorage.port
        val token = tokenStorage.authToken

        if (!tokenStorage.isConfigured) {
            listener.onConnectionStateChange(AgentState.DISCONNECTED)
            return
        }

        listener.onConnectionStateChange(AgentState.CONNECTING)
        val url = "ws://$host:$port/ws"
        Log.d(tag, "Connecting to $url...")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(tag, "WebSocket connected successfully")
                retryAttempt = 0
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
                Log.d(tag, "WebSocket closed")
                handler.post {
                    listener.onConnectionStateChange(AgentState.DISCONNECTED)
                }
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(tag, "WebSocket failure: ${t.message}")
                handler.post {
                    listener.onConnectionStateChange(AgentState.DISCONNECTED)
                }
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        isManuallyClosed = true
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "App paused or closed")
        webSocket = null
        listener.onConnectionStateChange(AgentState.DISCONNECTED)
    }

    private fun scheduleReconnect() {
        if (isManuallyClosed || !tokenStorage.isConfigured) return
        retryAttempt++
        // Exponential backoff with jitter: 1s, 2s, 4s, up to max 16s
        val delayMs = (Math.min(16, 1 shl (retryAttempt - 1)) * 1000L)
        Log.d(tag, "Scheduling reconnect attempt $retryAttempt in ${delayMs}ms")
        handler.postDelayed({
            if (!isManuallyClosed) {
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

    fun sendDismissPrompt() {
        val msg = JSONObject().apply {
            put("type", "DISMISS_PROMPT")
        }
        webSocket?.send(msg.toString())
    }
}
