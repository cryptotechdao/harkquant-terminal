package com.harkquant.terminal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.harkquant.terminal.ui.theme.Hq

/** Desktop placeholder: the real app embeds TradingView's widget in a WebView. */
@Composable
fun TradingViewWidget(symbol: String, interval: String, light: Boolean, bgArgb: Int, modifier: Modifier = Modifier) {
    Box(modifier.background(Hq.c.track), contentAlignment = Alignment.Center) { Text("TradingView widget · $symbol · $interval", color = Hq.c.tx2) }
}
