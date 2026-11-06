package com.agentremote.ui.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agentremote.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TerminalViewModel(application: Application) : AndroidViewModel(application), BridgeClient.BridgeListener {
    private val tokenStorage = TokenStorage(application)
    private val bridgeClient = BridgeClient(tokenStorage, this)

    private val _agentState = MutableStateFlow(AgentState.DISCONNECTED)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _activePrompt = MutableStateFlow<PromptData?>(null)
    val activePrompt: StateFlow<PromptData?> = _activePrompt.asStateFlow()

    private val _snapshotHistory = MutableStateFlow<String?>(null)
    val snapshotHistory: StateFlow<String?> = _snapshotHistory.asStateFlow()

    private val _latestChunk = MutableStateFlow<String?>(null)
    val latestChunk: StateFlow<String?> = _latestChunk.asStateFlow()

    private val _sessionId = MutableStateFlow("")
    val sessionId: StateFlow<String> = _sessionId.asStateFlow()

    init {
        connect()
    }

    fun connect() {
        bridgeClient.connect()
    }

    fun disconnect() {
        bridgeClient.disconnect()
    }

    fun sendOption(key: String) {
        bridgeClient.sendTextInput(key)
        _activePrompt.value = null
    }

    fun sendRawKey(key: String) {
        bridgeClient.sendRawKey(key)
    }

    fun sendText(text: String) {
        bridgeClient.sendTextInput(text)
    }

    fun dismissPrompt() {
        _activePrompt.value = null
        bridgeClient.sendDismissPrompt()
    }

    fun onGeometryChanged(rows: Int, cols: Int) {
        bridgeClient.sendResize(rows, cols)
    }

    // BridgeListener Callbacks
    override fun onConnectionStateChange(state: AgentState) {
        _agentState.value = state
    }

    override fun onSnapshot(snapshot: BridgeSnapshot) {
        _sessionId.value = snapshot.sessionId
        _agentState.value = snapshot.state
        _activePrompt.value = snapshot.activePrompt
        _snapshotHistory.value = snapshot.history
    }

    override fun onOutputChunk(chunk: String) {
        _latestChunk.value = chunk
    }

    override fun onStateUpdate(state: AgentState, activePrompt: PromptData?) {
        _agentState.value = state
        _activePrompt.value = activePrompt
    }

    override fun onCleared() {
        super.onCleared()
        bridgeClient.disconnect()
    }
}
