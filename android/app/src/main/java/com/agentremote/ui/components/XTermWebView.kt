package com.agentremote.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

class XTermBridge(
    private val onGeometryChanged: (rows: Int, cols: Int) -> Unit,
    private val onTerminalReady: () -> Unit
) {
    @JavascriptInterface
    fun onGeometry(rows: Int, cols: Int) {
        onGeometryChanged(rows, cols)
    }

    @JavascriptInterface
    fun onReady() {
        onTerminalReady()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun XTermWebView(
    streamChunk: String?,
    snapshotHistory: String?,
    onGeometryChanged: (rows: Int, cols: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isTerminalReady by remember { mutableStateOf(false) }

    fun writeToTerminal(text: String) {
        val webView = webViewInstance ?: return
        if (text.isEmpty()) return

        val encoded = JSONObject.quote(text)
        webView.post {
            webView.evaluateJavascript("window.writeTerminal($encoded);", null)
        }
    }

    fun clearAndWriteTerminal(history: String) {
        val webView = webViewInstance ?: return

        webView.post {
            webView.evaluateJavascript("window.clearTerminal();", null)
            if (history.isNotEmpty()) {
                val encoded = JSONObject.quote(history)
                webView.evaluateJavascript("window.writeTerminal($encoded);", null)
            }
        }
    }

    // Effect for streaming incremental chunks
    LaunchedEffect(streamChunk) {
        streamChunk?.let { writeToTerminal(it) }
    }

    // Effect for full snapshot history hydration (reconnect or ready)
    LaunchedEffect(snapshotHistory, isTerminalReady) {
        if (isTerminalReady && snapshotHistory != null) {
            clearAndWriteTerminal(snapshotHistory)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                setBackgroundColor(0xFF0A0B0E.toInt())

                addJavascriptInterface(
                    XTermBridge(
                        onGeometryChanged = onGeometryChanged,
                        onTerminalReady = {
                            post {
                                isTerminalReady = true
                                snapshotHistory?.let { clearAndWriteTerminal(it) }
                            }
                        }
                    ),
                    "AndroidInterface"
                )

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            Log.d("XTermJS", "${it.message()} [${it.sourceId()}:${it.lineNumber()}]")
                        }
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // In case JS onReady() fired before or after
                        if (snapshotHistory != null && isTerminalReady) {
                            clearAndWriteTerminal(snapshotHistory)
                        }
                    }
                }

                loadUrl("file:///android_asset/xterm/index.html")
                webViewInstance = this
            }
        },
        update = { webView ->
            webViewInstance = webView
        }
    )
}
