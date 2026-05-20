package com.signalsoop.app.ui

import android.annotation.SuppressLint
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.signalsoop.app.security.InputSanitizer

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KnowledgeGraph3DView(
    graphJson: String,
    onNodeSelected: (nodeId: String, label: String) -> Unit,
    modifier: Modifier = Modifier,
    previewHeight: Dp? = null,
) {
    val safeJson = remember(graphJson) { InputSanitizer.graphJsonForWebView(graphJson) }
    val jsonState = rememberUpdatedState(safeJson)
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun pushGraphToWebView(view: WebView?) {
        val viewRef = view ?: return
        viewRef.post {
            viewRef.evaluateJavascript(
                "window.GraphViewer && window.GraphViewer.loadFromAndroid();",
                null,
            )
        }
    }

    DisposableEffect(safeJson) {
        pushGraphToWebView(webView)
        onDispose { }
    }

    val sizeModifier =
        if (previewHeight != null) {
            Modifier.fillMaxWidth().height(previewHeight)
        } else {
            Modifier.fillMaxSize()
        }

    AndroidView(
        modifier = modifier.then(sizeModifier),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(0xFF0A0A0F.toInt())
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
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
                            pushGraphToWebView(view)
                        }
                    }
                addJavascriptInterface(
                    GraphPayloadBridge { jsonState.value.orEmpty() },
                    "GraphPayload",
                )
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
            pushGraphToWebView(view)
        },
    )
}

private class GraphPayloadBridge(private val payload: () -> String) {
    @JavascriptInterface
    fun getGraphPayload(): String = payload()
}
