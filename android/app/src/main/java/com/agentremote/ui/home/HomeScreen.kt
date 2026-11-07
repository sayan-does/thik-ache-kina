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
import androidx.compose.material.icons.filled.Key
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
import com.agentremote.BuildConfig
import com.agentremote.data.AgentState
import com.agentremote.data.TokenStorage
import com.agentremote.ui.components.LivePulsePill
import com.agentremote.ui.components.UpdateCard
import com.agentremote.ui.terminal.TerminalViewModel
import com.agentremote.ui.theme.*
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    agentState: AgentState,
    tokenStorage: TokenStorage,
    viewModel: TerminalViewModel,
    onOpenTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isSettingsOpen by remember { mutableStateOf(!tokenStorage.isConfigured) }
    var isPairingInProgress by remember { mutableStateOf(false) }

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
                            text = "CLI Terminal & Triage Controller v2.0",
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TARGET HOST",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = TerminalFontFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            if (tokenStorage.isPaired) {
                                Box(
                                    modifier = Modifier
                                        .background(AccentEmerald.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "PAIRED (ECDSA)",
                                        color = AccentEmerald,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = TerminalFontFamily
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        val displayHost = if (tokenStorage.isConfigured) {
                            "${tokenStorage.host}:${tokenStorage.port}"
                        } else {
                            "Not Paired / Configured"
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
                        text = "Terminal Agency & Triage",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hardware-authenticated ConPTY terminal session with instant numeric choice approvals and full terminal agency.",
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

            // Software Update Section
            UpdateCard(currentVersion = BuildConfig.VERSION_NAME)

            // Quick Host Launch Guide
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "HOST CONTROLLER STATUS",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Run on laptop to launch background daemon & system tray:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val cmdText = "cd agent-bridge ; .\\start-remote.bat"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CanvasDark, RoundedCornerShape(6.dp))
                            .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "$ $cmdText\n# Tray popover manages QR pairing & ConPTY sessions",
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

    // Host Settings & Pairing Dialog
    if (isSettingsOpen) {
        AlertDialog(
            onDismissRequest = { isSettingsOpen = false },
            title = {
                Text(
                    text = if (tokenStorage.isPaired) "Device Pairing & Host" else "Pair Mobile Device",
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
                    // Fast Pairing from Clipboard
                    Button(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text?.trim()
                            if (clipText.isNullOrEmpty()) {
                                Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (clipText.startsWith("{") && clipText.endsWith("}")) {
                                try {
                                    val json = JSONObject(clipText)
                                    if (json.has("nonce") && json.has("host")) {
                                        isPairingInProgress = true
                                        val host = json.getString("host")
                                        val port = json.optInt("port", 8765)
                                        val nonce = json.getString("nonce")
                                        val totp = json.optString("totp_secret", json.optString("totp", ""))

                                        viewModel.pairDevice(host, port, nonce, totp) { success, msg ->
                                            isPairingInProgress = false
                                            if (success) {
                                                Toast.makeText(context, "Hardware Device Paired Successfully!", Toast.LENGTH_LONG).show()
                                                isSettingsOpen = false
                                            } else {
                                                Toast.makeText(context, "Pairing Failed: $msg", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        return@Button
                                    }
                                } catch (_: Exception) {}
                            }

                            // Fallback manual import
                            val (h, p) = TokenStorage.sanitizeHost(clipText)
                            hostInput = h
                            if (p != null) portInput = p.toString()
                            Toast.makeText(context, "Host imported from clipboard", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !isPairingInProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Pair",
                            tint = CanvasDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPairingInProgress) "Pairing..." else "Pair via Clipboard Payload",
                            color = CanvasDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    if (tokenStorage.isPaired) {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearPairing()
                                Toast.makeText(context, "Device unshared / pairing cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCrimson),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCrimson.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Revoke Device Pairing", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "MANUAL HOST OVERRIDE",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold
                    )

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
                        label = { Text("Bearer Auth Token (Legacy)") },
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
                        viewModel.connect()
                        Toast.makeText(context, "Saved & Connecting", Toast.LENGTH_SHORT).show()
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
