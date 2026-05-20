package com.signalsoop.app.ui

import android.annotation.SuppressLint
import android.util.Base64
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
import androidx.compose.runtime.mutableIntStateOf
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
    var loadGeneration by remember { mutableIntStateOf(0) }

    fun pushGraphToWebView(view: WebView?, generation: Int) {
        val viewRef = view ?: return
        val json = jsonState.value
        viewRef.post {
            if (generation != loadGeneration) return@post
            if (json.isNullOrBlank()) {
                viewRef.evaluateJavascript(
                    "window.GraphViewer && window.GraphViewer.load('{\"nodes\":[],\"links\":[]}');",
                    null,
                )
                return@post
            }
            val b64 = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            viewRef.evaluateJavascript(
                """
                (function(){
                  if(!window.GraphViewer)return;
                  var ok=window.GraphViewer.loadFromBase64('$b64');
                  if(!ok) window.GraphViewer.loadFromAndroid();
                  window.GraphViewer.onResize && window.GraphViewer.onResize();
                })();
                """.trimIndent(),
                null,
            )
        }
    }

    fun schedulePushes(view: WebView?) {
        val gen = loadGeneration
        val delays = longArrayOf(0, 80, 200, 500, 1000)
        delays.forEach { delay ->
            view?.postDelayed({ pushGraphToWebView(view, gen) }, delay)
        }
    }

    DisposableEffect(safeJson) {
        loadGeneration++
        schedulePushes(webView)
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
                // Required so three.min.js loads from the same asset folder as index.html
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = true
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
                            loadGeneration++
                            schedulePushes(view)
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
                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    (v as? WebView)?.post {
                        v.evaluateJavascript(
                            "window.GraphViewer && window.GraphViewer.onResize && window.GraphViewer.onResize();",
                            null,
                        )
                    }
                }
                loadUrl("file:///android_asset/knowledge_graph_3d/index.html")
                webView = this
            }
        },
        update = { view ->
            webView = view
            loadGeneration++
            schedulePushes(view)
        },
    )
}

private class GraphPayloadBridge(private val payload: () -> String) {
    @JavascriptInterface
    fun getGraphPayload(): String = payload()
}
