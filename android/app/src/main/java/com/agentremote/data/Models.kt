package com.agentremote.data

enum class AgentState {
    IDLE,
    RUNNING,
    WAITING_FOR_INPUT,
    COMPLETED,
    ERROR,
    CONNECTING,
    DISCONNECTED
}

data class PromptOption(
    val key: String,
    val label: String
)

data class PromptData(
    val mode: String, // NUMBERED, CONFIRM, RAW_VT
    val headline: String,
    val target: String? = null,
    val diffPreview: String? = null,
    val options: List<PromptOption> = emptyList(),
    val hint: String? = null
)

data class BridgeSnapshot(
    val sessionId: String,
    val state: AgentState,
    val history: String,
    val activePrompt: PromptData?,
    val timestamp: Long
)

sealed class ClientAction {
    data class RawKey(val key: String) : ClientAction()
    data class TextInput(val text: String) : ClientAction()
    data class Resize(val rows: Int, val cols: Int) : ClientAction()
    object DismissPrompt : ClientAction()
}
