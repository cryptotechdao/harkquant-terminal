package com.harkquant.terminal.ui.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier

/** System-bar insets (edge-to-edge). Split out so the desktop screenshot harness can stub them. */
fun Modifier.hqNavBars(): Modifier = this.navigationBarsPadding()
fun Modifier.hqStatusBars(): Modifier = this.statusBarsPadding()
