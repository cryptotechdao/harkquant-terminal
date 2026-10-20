package com.harkquant.terminal.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harkquant.terminal.data.AppSettings
import com.harkquant.terminal.ui.AnalysisViewModel
import com.harkquant.terminal.ui.ChartViewModel
import com.harkquant.terminal.ui.DashboardViewModel
import com.harkquant.terminal.ui.IndicatorsViewModel
import com.harkquant.terminal.ui.NewsViewModel
import com.harkquant.terminal.ui.SettingsViewModel
import com.harkquant.terminal.ui.components.rememberNow

// Android-only route wrappers: they bind each ViewModel to its stateless *Content composable.

@Composable
fun DashboardScreen(onOpenNews: () -> Unit, onOpenAnalysis: () -> Unit, onOpenChart: () -> Unit, vm: DashboardViewModel = viewModel()) {
    val st = DashboardState(
        summary = vm.summary.collectAsState().value,
        ticker = vm.ticker.collectAsState().value,
        prices = vm.prices.collectAsState().value,
        news = vm.news.collectAsState().value,
        conn = vm.conn.collectAsState().value,
        symbol = vm.symbol.collectAsState().value,
        tf = vm.tf.collectAsState().value,
        bars = vm.bars.collectAsState().value,
        refreshing = vm.refreshing.collectAsState().value,
    )
    DashboardContent(st, rememberNow(), vm::refresh, vm::selectSymbol, vm::selectTf, onOpenNews, onOpenAnalysis, onOpenChart)
}


@Composable
fun NewsScreen(onBack: () -> Unit, vm: NewsViewModel = viewModel()) {
    val items by vm.items.collectAsState()
    val cal by vm.calendar.collectAsState()
    val impact by vm.impact.collectAsState()
    val asset by vm.asset.collectAsState()
    val q by vm.query.collectAsState()
    val range by vm.calRange.collectAsState()
    val highOnly by vm.calHighOnly.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    var tab by rememberSaveable { mutableStateOf("feed") }
    NewsContent(
        tab, { tab = it }, items, cal, impact, asset, q, range, highOnly, refreshing, rememberNow(),
        onBack, vm::refresh, { vm.impact.value = it }, { vm.asset.value = it }, { vm.query.value = it },
        { vm.calRange.value = it; vm.loadCalendar() }, { vm.calHighOnly.value = it; vm.loadCalendar() },
    )
}


@Composable
fun IndicatorsScreen(vm: IndicatorsViewModel = viewModel()) {
    val symbols by vm.symbols.collectAsState()
    val symbol by vm.symbol.collectAsState()
    val tf by vm.tf.collectAsState()
    val bars by vm.bars.collectAsState()
    val catalog by vm.catalog.collectAsState()
    val active by vm.active.collectAsState()
    val results by vm.results.collectAsState()
    val atr by vm.atr.collectAsState()
    val pivots by vm.pivots.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val serverPos by vm.serverPosition.collectAsState()
    IndicatorsContent(
        symbols.map { it.symbol }, symbol, tf, bars, catalog, active, active.map { results[vm.key(it)] }, atr, pivots, sessions, serverPos, rememberNow(),
        vm::setSymbol, vm::setTf, { vm.reload() }, vm::add, vm::remove, vm::updateParam, vm::verifyPosition,
    )
}


@Composable
fun TradingViewScreen(vm: ChartViewModel = viewModel()) {
    val symbols by vm.symbols.collectAsState()
    val symbol by vm.symbol.collectAsState()
    val tf by vm.tf.collectAsState()
    val mode by vm.mode.collectAsState()
    val bars by vm.bars.collectAsState()
    val ema by vm.ema.collectAsState()
    TradingViewContent(symbols, symbol, tf, mode, bars, ema, vm::setSymbol, vm::setTf, { vm.mode.value = it }, { vm.load(false) })
}


@Composable
fun AnalysisScreen(vm: AnalysisViewModel = viewModel()) {
    val st = AnalysisState(
        vm.symbols.collectAsState().value, vm.symbol.collectAsState().value, vm.tab.collectAsState().value,
        vm.analysis.collectAsState().value, vm.flow.collectAsState().value, vm.orderbook.collectAsState().value,
        vm.ratings.collectAsState().value, vm.positioning.collectAsState().value, vm.sentiment.collectAsState().value,
        vm.refreshing.collectAsState().value, vm.updatedAt.collectAsState().value,
    )
    AnalysisContent(st, vm::setSymbol, vm::setTab, vm::refresh)
}


@Composable
fun SettingsScreen(settings: AppSettings, vm: SettingsViewModel = viewModel()) {
    val token by vm.token.collectAsState()
    val status by vm.status.collectAsState()
    val health by vm.health.collectAsState()
    val server by vm.serverSettings.collectAsState()
    val saveResult by vm.saveResult.collectAsState()
    val tokenSaved by vm.tokenSaved.collectAsState()
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setAlerts(granted) }
    SettingsContent(
        settings, token, status, health, server, saveResult, tokenSaved,
        onTheme = { vm.setTheme(it) }, onRefresh = { vm.setRefresh(it) },
        onAlerts = { on ->
            if (on && Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setAlerts(on)
        },
        onBaseUrl = { vm.setBaseUrl(it) }, onTest = vm::testConnection, onToken = vm::saveToken, onSaveServer = vm::saveServerPatch, onReload = vm::reloadServer,
    )
}

