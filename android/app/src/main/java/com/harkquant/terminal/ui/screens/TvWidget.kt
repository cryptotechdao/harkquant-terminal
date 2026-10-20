package com.harkquant.terminal.ui.screens

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.harkquant.terminal.ui.theme.Hq

/** Official TradingView Advanced Chart widget (free embed). Only this tab uses a WebView. */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun TradingViewWidget(symbol: String, interval: String, light: Boolean, bgArgb: Int, modifier: Modifier = Modifier) {
    var loading by remember { mutableStateOf(true) }
    val html = remember(symbol, interval, light) { tvHtml(symbol, interval, light, bgArgb) }
    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(bgArgb)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.builtInZoomControls = false
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) { loading = false }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            // keep TradingView frames inside; open other links in the browser
                            val u = request?.url ?: return false
                            if (u.host?.contains("tradingview.com") == true) return false
                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, u)) }
                            return true
                        }
                    }
                    // let the chart own horizontal + pinch gestures
                    setOnTouchListener { v, e ->
                        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) v.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                    loadDataWithBaseURL("https://www.tradingview.com/", html, "text/html", "utf-8", null)
                    tag = html
                }
            },
            update = { w ->
                if (w.tag != html) { loading = true; w.tag = html; w.loadDataWithBaseURL("https://www.tradingview.com/", html, "text/html", "utf-8", null) }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Hq.c.acc)
    }
}

