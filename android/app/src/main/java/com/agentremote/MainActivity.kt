package com.agentremote

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.agentremote.data.BiometricAuthManager
import com.agentremote.data.TokenStorage
import com.agentremote.ui.home.HomeScreen
import com.agentremote.ui.terminal.TerminalScreen
import com.agentremote.ui.terminal.TerminalViewModel
import com.agentremote.ui.theme.AgentRemoteTheme
import com.agentremote.ui.theme.CanvasDark

class MainActivity : FragmentActivity() {
    private val terminalViewModel: TerminalViewModel by viewModels()
    private lateinit var tokenStorage: TokenStorage
    private lateinit var biometricAuthManager: BiometricAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStorage = TokenStorage(this)
        biometricAuthManager = BiometricAuthManager(this)

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

                    fun authenticateAndOpenTerminal() {
                        if (!tokenStorage.isConfigured) {
                            Toast.makeText(this@MainActivity, "Host not paired or configured", Toast.LENGTH_SHORT).show()
                            return
                        }

                        biometricAuthManager.promptBiometric(
                            onSuccess = {
                                terminalViewModel.connect()
                                currentScreen = Screen.Terminal
                            },
                            onError = { err ->
                                Toast.makeText(this@MainActivity, "Auth failed: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    when (currentScreen) {
                        Screen.Home -> {
                            HomeScreen(
                                agentState = agentState,
                                tokenStorage = tokenStorage,
                                viewModel = terminalViewModel,
                                onOpenTerminal = {
                                    authenticateAndOpenTerminal()
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
        if (::tokenStorage.isInitialized && tokenStorage.isConfigured) {
            terminalViewModel.connect()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        terminalViewModel.disconnect()
    }
}

enum class Screen {
    Home,
    Terminal
}
