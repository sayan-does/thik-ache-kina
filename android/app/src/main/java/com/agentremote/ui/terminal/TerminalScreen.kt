package com.agentremote.ui.terminal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agentremote.ui.components.LivePulsePill
import com.agentremote.ui.components.SplitThumbBar
import com.agentremote.ui.components.TriageActionCard
import com.agentremote.ui.components.XTermWebView
import com.agentremote.ui.theme.CanvasDark
import com.agentremote.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Intercept hardware and gesture back navigation to return cleanly to HomeScreen
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
                            text = "CLI Terminal",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium
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
            // Animated Triage Action Card (Visible when prompt is pending)
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

            // Full xterm.js Terminal Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                XTermWebView(
                    streamChunk = latestChunk,
                    snapshotHistory = snapshotHistory,
                    onGeometryChanged = { r, c -> viewModel.onGeometryChanged(r, c) }
                )
            }
        }
    }
}
