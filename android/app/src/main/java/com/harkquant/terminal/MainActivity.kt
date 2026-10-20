package com.harkquant.terminal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.harkquant.terminal.ui.AppRoot
import com.harkquant.terminal.ui.theme.HqTheme
import com.harkquant.terminal.ui.theme.Themes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val app = application as HqApp
        var ready = false
        splash.setKeepOnScreenCondition { !ready }
        enableEdgeToEdge()
        setContent {
            val settings by app.settings.collectAsState()
            val light = Themes.byId(settings.themeId).palette.isLight
            LaunchedEffect(light) {
                val style = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                ready = true
            }
            HqTheme(settings.themeId) { AppRoot(settings) }
        }
    }
}
