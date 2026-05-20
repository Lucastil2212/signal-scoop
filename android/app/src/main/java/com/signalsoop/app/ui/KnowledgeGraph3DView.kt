package com.signalsoop.app.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.signalsoop.app.security.InputSanitizer

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KnowledgeGraph3DView(
    graphJson: String,
    onNodeSelected: (nodeId: String, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeJson = remember(graphJson) { InputSanitizer.graphJsonForWebView(graphJson) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(safeJson) {
        val json = safeJson ?: return@LaunchedEffect
        webView?.evaluateJavascript(
            "window.GraphViewer && window.GraphViewer.load(${json.quoteJs()});",
            null,
        )
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(0xFF0A0A0F.toInt())
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            return !url.startsWith("file:///android_asset/")
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            val json = safeJson ?: return
                            evaluateJavascript(
                                "window.GraphViewer && window.GraphViewer.load(${json.quoteJs()});",
                                null,
                            )
                        }
                    }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onNodeSelected(nodeId: String, label: String) {
                            onNodeSelected(
                                InputSanitizer.javascriptLabel(nodeId),
                                InputSanitizer.javascriptLabel(label),
                            )
                        }
                    },
                    "AndroidGraph",
                )
                loadUrl("file:///android_asset/knowledge_graph_3d/index.html")
                webView = this
            }
        },
        update = { view ->
            webView = view
        },
    )
}

private fun String.quoteJs(): String {
    val escaped = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    return "\"$escaped\""
}
