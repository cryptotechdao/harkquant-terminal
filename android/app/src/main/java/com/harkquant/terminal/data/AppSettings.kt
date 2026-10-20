package com.harkquant.terminal.data

import com.harkquant.terminal.BuildConfig

data class AppSettings(
    val themeId: Int = DEFAULT_THEME,
    val baseUrl: String = BuildConfig.DEFAULT_BASE_URL,
    val refreshSec: Int = 20,
    val headlineAlerts: Boolean = false,
    val chartTf: String = "15m",
    val tvSymbol: String = "XAUUSD",
    val analysisSymbol: String = "XAUUSD",
    val indicatorsJson: String = "",
) {
    companion object { const val DEFAULT_THEME = 17 }
}

