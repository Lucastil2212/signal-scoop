package com.signalsoop.app.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KnowledgeGraph3DView(
    graphJson: String,
    onNodeSelected: (nodeId: String, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(graphJson) {
        if (graphJson.isNotBlank()) {
            webView?.evaluateJavascript(
                "window.GraphViewer && window.GraphViewer.load(${graphJson.quoteJs()});",
                null,
            )
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(0xFF0A0A0F.toInt())
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (graphJson.isNotBlank()) {
                                evaluateJavascript(
                                    "window.GraphViewer && window.GraphViewer.load(${graphJson.quoteJs()});",
                                    null,
                                )
                            }
                        }
                    }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onNodeSelected(nodeId: String, label: String) {
                            onNodeSelected(nodeId, label)
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
