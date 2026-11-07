package com.agentremote.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentremote.data.AgentState
import com.agentremote.data.TokenStorage
import com.agentremote.ui.components.LivePulsePill
import com.agentremote.ui.theme.*
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    agentState: AgentState,
    tokenStorage: TokenStorage,
    onOpenTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isSettingsOpen by remember { mutableStateOf(!tokenStorage.isConfigured) }

    var hostInput by remember { mutableStateOf(tokenStorage.host) }
    var portInput by remember { mutableStateOf(tokenStorage.port.toString()) }
    var tokenInput by remember { mutableStateOf(tokenStorage.authToken) }
    var ntfyInput by remember { mutableStateOf(tokenStorage.ntfyTopic) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🤖 Agent Remote",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = {
                        hostInput = tokenStorage.host
                        portInput = tokenStorage.port.toString()
                        tokenInput = tokenStorage.authToken
                        ntfyInput = tokenStorage.ntfyTopic
                        isSettingsOpen = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CanvasDark)
            )
        },
        containerColor = CanvasDark,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "HOST STATUS",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (tokenStorage.isConfigured) "${tokenStorage.host}:${tokenStorage.port}" else "Not Configured",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = TerminalFontFamily
                        )
                    }
                    LivePulsePill(state = agentState)
                }
            }

            // Active Session Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHighlight, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ACTIVE CLI SESSION",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Real-Time Terminal & Triage Control",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Receive notifications, approve file modifications, and send prompts to AGY, OpenCode, or Claude Code.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onOpenTerminal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Open Terminal Control →",
                            color = CanvasDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Quick Start Launch Guide
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LAUNCH FROM LAPTOP POWERSHELL",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Run any CLI agent wrapped with the bridge daemon:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val cmdText = "python bridge.py -- agy \"build login page\""
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CanvasDark, RoundedCornerShape(6.dp))
                            .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "$ $cmdText\n$ python bridge.py -- opencode\n$ python bridge.py -- claude",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontFamily = TerminalFontFamily,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(cmdText))
                            Toast.makeText(context, "Copied template command!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📋 Copy Command Template", color = TextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Host & Security Settings Dialog
    if (isSettingsOpen) {
        AlertDialog(
            onDismissRequest = { isSettingsOpen = false },
            title = {
                Text(text = "Bridge Daemon Connection", color = TextPrimary)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1-Tap Clipboard Paste Button
                    FilledTonalButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text?.trim()
                            if (clipText.isNullOrEmpty()) {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            } else {
                                var parsedAny = false
                                // Attempt JSON parse
                                if (clipText.startsWith("{") && clipText.endsWith("}")) {
                                    try {
                                        val json = JSONObject(clipText)
                                        if (json.has("host")) {
                                            val (h, p) = TokenStorage.sanitizeHost(json.getString("host"))
                                            hostInput = h
                                            if (p != null) portInput = p.toString()
                                            parsedAny = true
                                        }
                                        if (json.has("port")) {
                                            portInput = json.getInt("port").toString()
                                            parsedAny = true
                                        }
                                        if (json.has("token")) {
                                            tokenInput = TokenStorage.sanitizeToken(json.getString("token"))
                                            parsedAny = true
                                        }
                                        if (json.has("ntfy")) {
                                            ntfyInput = TokenStorage.sanitizeNtfyTopic(json.getString("ntfy"))
                                            parsedAny = true
                                        }
                                    } catch (_: Exception) {}
                                }

                                if (!parsedAny) {
                                    // Parse key=value or ws:// or IP:port
                                    if (clipText.contains("://") || clipText.contains(":") || clipText.contains(";")) {
                                        val (h, p) = TokenStorage.sanitizeHost(clipText)
                                        if (h.isNotBlank() && h != TokenStorage.DEFAULT_HOST) {
                                            hostInput = h
                                            if (p != null) portInput = p.toString()
                                            parsedAny = true
                                        }
                                    } else if (clipText.length >= 32 && !clipText.contains(".")) {
                                        // Hex auth token
                                        tokenInput = TokenStorage.sanitizeToken(clipText)
                                        parsedAny = true
                                    }
                                }

                                if (parsedAny) {
                                    Toast.makeText(context, "Imported configuration from clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    // Fallback: put in hostInput and sanitize
                                    val (h, p) = TokenStorage.sanitizeHost(clipText)
                                    hostInput = h
                                    if (p != null) portInput = p.toString()
                                    Toast.makeText(context, "Applied host from clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste from Clipboard", color = AccentCyan, fontSize = 12.sp)
                    }

                    // Lenient Host input: automatically splits IP:port and IP;port
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { raw ->
                            if (raw.contains(":") || raw.contains(";")) {
                                val (cleanedHost, extractedPort) = TokenStorage.sanitizeHost(raw)
                                hostInput = cleanedHost
                                if (extractedPort != null) {
                                    portInput = extractedPort.toString()
                                }
                            } else {
                                hostInput = raw.filterNot { it.isWhitespace() }
                            }
                        },
                        label = { Text("Laptop Tailscale IP (e.g. 100.82.14.92)") },
                        placeholder = { Text("100.x.y.z or IP:8765") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port (Default 8765)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = TokenStorage.sanitizeToken(it) },
                        label = { Text("Bearer Auth Token") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = ntfyInput,
                        onValueChange = { ntfyInput = TokenStorage.sanitizeNtfyTopic(it) },
                        label = { Text("ntfy.sh Topic (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        tokenStorage.saveConfig(
                            rawHost = hostInput,
                            rawPort = portInput,
                            rawToken = tokenInput,
                            rawNtfy = ntfyInput
                        )
                        isSettingsOpen = false
                        Toast.makeText(context, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                ) {
                    Text("Save & Connect", color = CanvasDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isSettingsOpen = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }
}
