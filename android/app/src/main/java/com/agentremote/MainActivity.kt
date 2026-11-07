package com.agentremote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.agentremote.data.TokenStorage
import com.agentremote.ui.home.HomeScreen
import com.agentremote.ui.terminal.TerminalScreen
import com.agentremote.ui.terminal.TerminalViewModel
import com.agentremote.ui.theme.AgentRemoteTheme
import com.agentremote.ui.theme.CanvasDark

class MainActivity : ComponentActivity() {
    private val terminalViewModel: TerminalViewModel by viewModels()
    private lateinit var tokenStorage: TokenStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStorage = TokenStorage(this)

        val startScreen = if (isDeepLinkIntent(intent) && tokenStorage.isConfigured) {
            Screen.Terminal
        } else {
            Screen.Home
        }

        setContent {
            AgentRemoteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CanvasDark
                ) {
                    var currentScreen by remember { mutableStateOf(startScreen) }
                    val agentState by terminalViewModel.agentState.collectAsState()

                    // Ensure Android system back gesture navigates from Terminal back to Home
                    BackHandler(enabled = currentScreen == Screen.Terminal) {
                        currentScreen = Screen.Home
                    }

                    when (currentScreen) {
                        Screen.Home -> {
                            HomeScreen(
                                agentState = agentState,
                                tokenStorage = tokenStorage,
                                onOpenTerminal = {
                                    if (tokenStorage.isConfigured) {
                                        terminalViewModel.connect()
                                    }
                                    currentScreen = Screen.Terminal
                                }
                            )
                        }
                        Screen.Terminal -> {
                            TerminalScreen(
                                viewModel = terminalViewModel,
                                onBack = { currentScreen = Screen.Home }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isDeepLinkIntent(intent) && tokenStorage.isConfigured) {
            terminalViewModel.connect()
        }
    }

    private fun isDeepLinkIntent(intent: Intent?): Boolean {
        return intent?.data?.scheme == "agentremote"
    }

    override fun onResume() {
        super.onResume()
        // Re-establish connection when app is opened in foreground, if configured
        if (::tokenStorage.isInitialized && tokenStorage.isConfigured) {
            terminalViewModel.connect()
        }
    }

    override fun onPause() {
        super.onPause()
        // Clean disconnect after idle to conserve battery
        terminalViewModel.disconnect()
    }
}

enum class Screen {
    Home,
    Terminal
}
