package com.agentremote.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

class XTermBridge(
    private val onGeometryChanged: (rows: Int, cols: Int) -> Unit
) {
    @JavascriptInterface
    fun onGeometry(rows: Int, cols: Int) {
        onGeometryChanged(rows, cols)
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
    var isPageLoaded by remember { mutableStateOf(false) }

    fun writeToTerminal(text: String) {
        val webView = webViewInstance ?: return
        if (!isPageLoaded || text.isEmpty()) return

        // Safely pass text into JS via JSON encoding to handle all escape codes and newlines
        val encoded = JSONObject.quote(text)
        webView.evaluateJavascript("window.writeTerminal($encoded);", null)
    }

    fun clearAndWriteTerminal(history: String) {
        val webView = webViewInstance ?: return
        if (!isPageLoaded) return

        webView.evaluateJavascript("window.clearTerminal();", null)
        if (history.isNotEmpty()) {
            val encoded = JSONObject.quote(history)
            webView.evaluateJavascript("window.writeTerminal($encoded);", null)
        }
    }

    // Effect for streaming incremental chunks
    LaunchedEffect(streamChunk) {
        streamChunk?.let { writeToTerminal(it) }
    }

    // Effect for full snapshot history hydration (reconnect)
    LaunchedEffect(snapshotHistory, isPageLoaded) {
        snapshotHistory?.let { clearAndWriteTerminal(it) }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                setBackgroundColor(0xFF090A0F.toInt())

                addJavascriptInterface(XTermBridge(onGeometryChanged), "AndroidInterface")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isPageLoaded = true
                        snapshotHistory?.let { clearAndWriteTerminal(it) }
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
