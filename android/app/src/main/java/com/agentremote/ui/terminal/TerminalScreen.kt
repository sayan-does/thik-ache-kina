package com.agentremote.ui.terminal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentremote.ui.components.LivePulsePill
import com.agentremote.ui.components.SplitThumbBar
import com.agentremote.ui.components.TriageActionCard
import com.agentremote.ui.components.XTermWebView
import com.agentremote.ui.theme.CanvasDark
import com.agentremote.ui.theme.TerminalFontFamily
import com.agentremote.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Intercept Android hardware/gesture back to safely return to HomeScreen
    BackHandler(onBack = onBack)

    val agentState by viewModel.agentState.collectAsState()
    val activePrompt by viewModel.activePrompt.collectAsState()
    val snapshotHistory by viewModel.snapshotHistory.collectAsState()
    val latestChunk by viewModel.latestChunk.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "TERMINAL",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TerminalFontFamily,
                            letterSpacing = 1.sp
                        )
                        LivePulsePill(state = agentState)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CanvasDark)
            )
        },
        bottomBar = {
            SplitThumbBar(
                onRawKey = { key -> viewModel.sendRawKey(key) },
                onSendText = { text -> viewModel.sendText(text) }
            )
        },
        containerColor = CanvasDark,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Triage Action Card (Prominently displayed when agent requires approval)
            AnimatedVisibility(
                visible = activePrompt != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                activePrompt?.let { prompt ->
                    TriageActionCard(
                        prompt = prompt,
                        onOptionSelected = { key -> viewModel.sendOption(key) },
                        onDismiss = { viewModel.dismissPrompt() },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // High-fidelity xterm.js viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                XTermWebView(
                    outputChunks = viewModel.outputChunks,
                    streamChunk = latestChunk,
                    snapshotHistory = snapshotHistory,
                    onGeometryChanged = { r, c -> viewModel.onGeometryChanged(r, c) }
                )
            }
        }
    }
}
