package com.agentremote.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
                    Column {
                        Text(
                            text = "AGENT REMOTE",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "CLI Terminal & Triage Controller",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = TerminalFontFamily
                        )
                    }
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
                            tint = TextSecondary
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Target Host Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(8.dp)
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
                            text = "TARGET HOST",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val displayHost = if (tokenStorage.isConfigured) {
                            if (tokenStorage.host.contains(":")) tokenStorage.host else "${tokenStorage.host}:${tokenStorage.port}"
                        } else {
                            "Unconfigured"
                        }
                        Text(
                            text = displayHost,
                            color = TextPrimary,
                            fontSize = 13.sp,
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
                    .border(1.dp, BorderHighlight, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .background(SurfaceElevated, RoundedCornerShape(4.dp))
                            .border(1.dp, BorderDark, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACTIVE SESSION",
                            color = AccentCyan,
                            fontSize = 10.sp,
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Terminal Stream & Triage",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Live ANSI output, interactive approval cards, and remote terminal controls for AGY, OpenCode, and Claude Code.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onOpenTerminal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Open Terminal Control",
                            color = CanvasDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }

            // Quick Launch Host Guide
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "HOST LAUNCH COMMAND",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Run on your laptop PowerShell:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val cmdText = "python bridge.py -- agy \"build login page\""
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CanvasDark, RoundedCornerShape(6.dp))
                            .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "$ $cmdText\n$ python bridge.py -- opencode\n$ python bridge.py -- claude",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontFamily = TerminalFontFamily,
                            lineHeight = 17.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(cmdText))
                            Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHighlight),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copy Command Template",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = TerminalFontFamily
                        )
                    }
                }
            }
        }
    }

    // Host Settings Dialog
    if (isSettingsOpen) {
        AlertDialog(
            onDismissRequest = { isSettingsOpen = false },
            title = {
                Text(
                    text = "Host Configuration",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Clipboard Import Button
                    FilledTonalButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text?.trim()
                            if (clipText.isNullOrEmpty()) {
                                Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                            } else {
                                var parsedAny = false
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
                                    if (clipText.contains("://") || clipText.contains(":") || clipText.contains(";")) {
                                        val (h, p) = TokenStorage.sanitizeHost(clipText)
                                        if (h.isNotBlank() && h != TokenStorage.DEFAULT_HOST) {
                                            hostInput = h
                                            if (p != null) portInput = p.toString()
                                            parsedAny = true
                                        }
                                    } else if (clipText.length >= 8 && !clipText.contains(" ")) {
                                        tokenInput = TokenStorage.sanitizeToken(clipText)
                                        parsedAny = true
                                    }
                                }

                                if (parsedAny) {
                                    Toast.makeText(context, "Configuration imported", Toast.LENGTH_SHORT).show()
                                } else {
                                    val (h, p) = TokenStorage.sanitizeHost(clipText)
                                    hostInput = h
                                    if (p != null) portInput = p.toString()
                                    Toast.makeText(context, "Host imported", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceElevated),
                        shape = RoundedCornerShape(6.dp)
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
                        label = { Text("Laptop IP (e.g. 192.168.0.101)") },
                        placeholder = { Text("IP only, without port") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderDark
                        )
                    )

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port (Default 8765)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderDark
                        )
                    )

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = TokenStorage.sanitizeToken(it) },
                        label = { Text("Bearer Auth Token") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderDark
                        )
                    )

                    OutlinedTextField(
                        value = ntfyInput,
                        onValueChange = { ntfyInput = TokenStorage.sanitizeNtfyTopic(it) },
                        label = { Text("Notification Topic (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderDark
                        )
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
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                    shape = RoundedCornerShape(6.dp)
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
