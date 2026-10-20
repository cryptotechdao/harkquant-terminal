package com.harkquant.terminal.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.harkquant.terminal.data.AppSettings
import com.harkquant.terminal.ui.components.HqBackground
import com.harkquant.terminal.ui.components.HqIcons
import com.harkquant.terminal.ui.screens.AnalysisScreen
import com.harkquant.terminal.ui.screens.DashboardScreen
import com.harkquant.terminal.ui.screens.IndicatorsScreen
import com.harkquant.terminal.ui.screens.NewsScreen
import com.harkquant.terminal.ui.screens.SettingsScreen
import com.harkquant.terminal.ui.screens.TradingViewScreen
import com.harkquant.terminal.ui.theme.Form
import com.harkquant.terminal.ui.theme.Hq

@Composable
fun AppRoot(settings: AppSettings) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Tab.DASH.route
    val current = Tab.entries.firstOrNull { it.route == route } ?: if (route == "news") Tab.DASH else Tab.DASH
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val go: (Tab) -> Unit = { t -> nav.navigateTab(t.route) }
    HqBackground {
        Row(Modifier.fillMaxSize()) {
            if (wide) HqNavRail(current, go)
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = { if (!wide) HqBottomBar(current, go) },
            ) { pad ->
                NavHost(
                    nav, startDestination = Tab.DASH.route,
                    modifier = Modifier.padding(pad).statusBarsPadding().let { if (wide) it.navigationBarsPadding() else it },
                    enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
                ) {
                    composable(Tab.DASH.route) {
                        DashboardScreen(onOpenNews = { nav.navigate("news") }, onOpenAnalysis = { go(Tab.ANALYSIS) }, onOpenChart = { go(Tab.TV) })
                    }
                    composable(Tab.TOOLS.route) { IndicatorsScreen() }
                    composable(Tab.TV.route) { TradingViewScreen() }
                    composable(Tab.ANALYSIS.route) { AnalysisScreen() }
                    composable(Tab.SETTINGS.route) { SettingsScreen(settings) }
                    composable("news") { NewsScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

