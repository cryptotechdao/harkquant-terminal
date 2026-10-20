package com.harkquant.terminal.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harkquant.terminal.HqApp
import com.harkquant.terminal.data.Analysis
import com.harkquant.terminal.data.AtrResult
import com.harkquant.terminal.data.BarsResponse
import com.harkquant.terminal.data.Calendar
import com.harkquant.terminal.data.Flow
import com.harkquant.terminal.data.Health
import com.harkquant.terminal.data.HqJson
import com.harkquant.terminal.data.IndicatorCatalog
import com.harkquant.terminal.data.IndicatorMeta
import com.harkquant.terminal.data.IndicatorResult
import com.harkquant.terminal.data.Load
import com.harkquant.terminal.data.NewsItem
import com.harkquant.terminal.data.OrderBook
import com.harkquant.terminal.data.Pivots
import com.harkquant.terminal.data.PositionRequest
import com.harkquant.terminal.data.PositionResult
import com.harkquant.terminal.data.Positioning
import com.harkquant.terminal.data.Sentiment
import com.harkquant.terminal.data.ServerStatus
import com.harkquant.terminal.data.Sessions
import com.harkquant.terminal.data.Technical
import com.harkquant.terminal.data.failedWith
import com.harkquant.terminal.data.friendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Run [block] and publish into [state], keeping old data visible (stale) on failure. */
fun <T> CoroutineScope.loadInto(state: MutableStateFlow<Load<T>>, reset: Boolean = false, block: suspend () -> T): Job = launch {
    if (reset || state.value !is Load.Ok) state.value = Load.Loading
    try {
        state.value = Load.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        state.update { it.failedWith(e.friendly()) }
    }
}

abstract class HqViewModel(app: Application) : AndroidViewModel(app) {
    protected val hq = app as HqApp
    val repo get() = hq.repo
    val api get() = hq.api
    val prefs get() = hq.prefs
    val settings get() = hq.settings
}

// ================================================================== dashboard
class DashboardViewModel(app: Application) : HqViewModel(app) {
    val summary = repo.summary
    val ticker = repo.ticker
    val prices = repo.prices
    val news = repo.news
    val conn = repo.conn

    private val _symbol = MutableStateFlow("XAUUSD")
    val symbol: StateFlow<String> = _symbol.asStateFlow()
    private val _tf = MutableStateFlow(hq.settings.value.chartTf)
    val tf: StateFlow<String> = _tf.asStateFlow()
    val bars = MutableStateFlow<Load<BarsResponse>>(Load.Loading)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        viewModelScope.launch { _tf.value = prefs.settings.first().chartTf; loadChart(true) }
        viewModelScope.launch {
            while (isActive) { delay(hq.settings.value.refreshSec.coerceAtLeast(10) * 1000L); loadChart(false) }
        }
    }

    fun selectSymbol(s: String) { if (s != _symbol.value) { _symbol.value = s; loadChart(true) } }
    fun selectTf(t: String) { if (t != _tf.value) { _tf.value = t; viewModelScope.launch { prefs.setChartTf(t) }; loadChart(true) } }

    private var chartJob: Job? = null
    fun loadChart(reset: Boolean) {
        chartJob?.cancel()
        chartJob = viewModelScope.loadInto(bars, reset) { api.bars(_symbol.value, _tf.value, 300) }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            val a = async { repo.refreshAll() }
            loadChart(false)
            a.await(); chartJob?.join()
            _refreshing.value = false
        }
    }
}

// ================================================================== news & calendar
class NewsViewModel(app: Application) : HqViewModel(app) {
    val impact = MutableStateFlow("ALL")
    val asset = MutableStateFlow("ALL")
    val query = MutableStateFlow("")
    val calRange = MutableStateFlow("week")
    val calHighOnly = MutableStateFlow(false)
    val calendar = MutableStateFlow<Load<Calendar>>(Load.Loading)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val items: StateFlow<Load<List<NewsItem>>> = combine(repo.news, impact, asset, query) { n, imp, a, q ->
        when (n) {
            is Load.Ok -> n.copy(data = filterNews(n.data, imp, a, q))
            else -> n
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Load.Loading)

    init { loadCalendar() }

    fun loadCalendar(): Job = viewModelScope.loadInto(calendar) { api.calendar(calRange.value, if (calHighOnly.value) "High" else null) }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            repo.refreshAll(); loadCalendar().join()
            _refreshing.value = false
        }
    }

    companion object {
        fun filterNews(list: List<NewsItem>, impact: String, asset: String, q: String) = com.harkquant.terminal.ui.filterNews(list, impact, asset, q)
    }
}

// ================================================================== indicators & tools
class IndicatorsViewModel(app: Application) : HqViewModel(app) {
    val symbols = repo.symbols
    val symbol = MutableStateFlow("BTCUSD")
    val tf = MutableStateFlow("15m")
    val bars = MutableStateFlow<Load<BarsResponse>>(Load.Loading)
    val catalog = MutableStateFlow<Load<IndicatorCatalog>>(Load.Loading)
    val active = MutableStateFlow(DEFAULT_ACTIVE)
    val results = MutableStateFlow<Map<String, IndicatorResult>>(emptyMap())
    val atr = MutableStateFlow<Load<AtrResult>>(Load.Loading)
    val pivots = MutableStateFlow<Load<Pivots>>(Load.Loading)
    val sessions = MutableStateFlow<Load<Sessions>>(Load.Loading)
    val serverPosition = MutableStateFlow<Load<PositionResult>?>(null)

    init {
        viewModelScope.launch {
            val saved = prefs.settings.first().indicatorsJson
            if (saved.isNotBlank()) runCatching { HqJson.decodeFromString(ListSerializer(ActiveIndicator.serializer()), saved) }.onSuccess { active.value = it }
            reload()
        }
        viewModelScope.loadInto(catalog) { api.indicators() }
        viewModelScope.launch { while (isActive) { viewModelScope.loadInto(sessions) { api.sessions() }; delay(30_000) } }
    }

    fun key(a: ActiveIndicator) = "${symbol.value}|${tf.value}|${a.id}|${a.params}"

    fun setSymbol(s: String) { symbol.value = s; results.value = emptyMap(); serverPosition.value = null; reload(true) }
    fun setTf(t: String) { tf.value = t; results.value = emptyMap(); reload(true) }

    fun reload(reset: Boolean = false) {
        viewModelScope.loadInto(bars, reset) { api.bars(symbol.value, tf.value, 600) }
        computeAll()
        viewModelScope.loadInto(atr, reset) { api.atr(symbol.value, tf.value) }
        viewModelScope.loadInto(pivots, reset) { api.pivots(symbol.value) }
    }

    private fun computeAll() { active.value.forEach { compute(it) } }

    private fun compute(a: ActiveIndicator) {
        val k = key(a)
        if (results.value[k]?.error == null && results.value.containsKey(k)) return
        viewModelScope.launch {
            val r = try { api.indicator(a.id, symbol.value, tf.value, a.params) } catch (e: CancellationException) { throw e } catch (e: Throwable) { IndicatorResult(id = a.id, name = a.name, error = e.friendly()) }
            results.update { it + (k to r) }
        }
    }

    fun add(meta: IndicatorMeta) {
        val a = ActiveIndicator(meta.id, meta.name, JsonObject(meta.params))
        active.update { it + a }; persist(); compute(a)
    }

    fun remove(index: Int) { active.update { it.toMutableList().also { l -> if (index in l.indices) l.removeAt(index) } }; persist() }

    fun updateParam(index: Int, name: String, value: Double) {
        val cur = active.value.getOrNull(index) ?: return
        val np = JsonObject(cur.params + (name to JsonPrimitive(if (value % 1.0 == 0.0) value.toLong() else value)))
        val na = cur.copy(params = np)
        active.update { it.toMutableList().also { l -> l[index] = na } }
        persist(); compute(na)
    }

    private fun persist() { viewModelScope.launch { prefs.setIndicators(HqJson.encodeToString(ListSerializer(ActiveIndicator.serializer()), active.value)) } }

    fun verifyPosition(req: PositionRequest) {
        serverPosition.value = Load.Loading
        viewModelScope.launch {
            serverPosition.value = try { Load.Ok(api.position(req)) } catch (e: CancellationException) { throw e } catch (e: Throwable) { Load.Err(e.friendly()) }
        }
    }

    companion object {
        val DEFAULT_ACTIVE = listOf(
            ActiveIndicator("talib:EMA", "Exponential Moving Average", buildJsonObject { put("timeperiod", JsonPrimitive(50)) }),
            ActiveIndicator("talib:RSI", "Relative Strength Index", buildJsonObject { put("timeperiod", JsonPrimitive(14)) }),
        )
    }
}

// ================================================================== TradingView tab
class ChartViewModel(app: Application) : HqViewModel(app) {
    val symbols = repo.symbols
    val symbol = MutableStateFlow("XAUUSD")
    val tf = MutableStateFlow("15m")
    val mode = MutableStateFlow("native")
    val bars = MutableStateFlow<Load<BarsResponse>>(Load.Loading)
    val ema = MutableStateFlow<IndicatorResult?>(null)

    init {
        viewModelScope.launch { symbol.value = prefs.settings.first().tvSymbol; load(true) }
        viewModelScope.launch { while (isActive) { delay(hq.settings.value.refreshSec.coerceAtLeast(10) * 1000L); if (mode.value == "native") load(false) } }
    }

    fun setSymbol(s: String) { symbol.value = s; viewModelScope.launch { prefs.setTvSymbol(s) }; load(true) }
    fun setTf(t: String) { tf.value = t; load(true) }

    fun load(reset: Boolean) {
        if (reset) ema.value = null
        viewModelScope.loadInto(bars, reset) { api.bars(symbol.value, tf.value, 500) }
        viewModelScope.launch { runCatching { api.indicator("talib:EMA", symbol.value, tf.value, buildJsonObject { put("timeperiod", JsonPrimitive(21)) }, 500) }.onSuccess { ema.value = it } }
    }
}

// ================================================================== market analysis
class AnalysisViewModel(app: Application) : HqViewModel(app) {
    val symbols = repo.symbols
    val symbol = MutableStateFlow("XAUUSD")
    val tab = MutableStateFlow("bias")
    val analysis = MutableStateFlow<Load<Analysis>>(Load.Loading)
    val flow = MutableStateFlow<Load<Flow>>(Load.Loading)
    val orderbook = MutableStateFlow<Load<OrderBook>>(Load.Loading)
    val ratings = MutableStateFlow<Load<Technical>>(Load.Loading)
    val positioning = MutableStateFlow<Load<Positioning>>(Load.Loading)
    val sentiment = MutableStateFlow<Load<Sentiment>>(Load.Loading)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    val updatedAt = MutableStateFlow(0L)

    init {
        viewModelScope.launch { symbol.value = prefs.settings.first().analysisSymbol; load(true) }
        viewModelScope.launch { while (isActive) { delay(hq.settings.value.refreshSec.coerceAtLeast(10) * 1000L); load(false) } }
    }

    fun setSymbol(s: String) { symbol.value = s; viewModelScope.launch { prefs.setAnalysisSymbol(s) }; load(true) }
    fun setTab(t: String) { tab.value = t; load(false) }

    fun load(reset: Boolean): List<Job> {
        val s = symbol.value
        val jobs = mutableListOf(viewModelScope.loadInto(analysis, reset) { api.analysis(s) })
        when (tab.value) {
            "flow" -> jobs += viewModelScope.loadInto(flow, reset) { api.flow(s) }
            "pressure" -> { jobs += viewModelScope.loadInto(orderbook, reset) { api.orderbook(s) }; jobs += viewModelScope.loadInto(flow, reset) { api.flow(s) } }
            "bulls" -> { jobs += viewModelScope.loadInto(ratings, reset) { api.ratings(s) }; jobs += viewModelScope.loadInto(positioning, reset) { api.positioning(s) } }
            "sentiment" -> jobs += viewModelScope.loadInto(sentiment, reset) { api.sentiment(6.0) }
        }
        if (reset) { flow.value = Load.Loading; orderbook.value = Load.Loading; ratings.value = Load.Loading; positioning.value = Load.Loading }
        updatedAt.value = System.currentTimeMillis()
        return jobs
    }

    fun refresh() { viewModelScope.launch { _refreshing.value = true; load(false).forEach { it.join() }; _refreshing.value = false } }
}

// ================================================================== settings
class SettingsViewModel(app: Application) : HqViewModel(app) {
    val token = prefs.token
    val status = MutableStateFlow<Load<ServerStatus>>(Load.Loading)
    val health = MutableStateFlow<Load<Pair<Health, Long>>?>(null)
    val serverSettings = MutableStateFlow<Load<JsonObject>>(Load.Loading)
    val saveResult = MutableStateFlow<String?>(null)
    val tokenSaved = MutableStateFlow<Boolean?>(null)

    init { reloadServer() }

    fun reloadServer() {
        viewModelScope.loadInto(status) { api.status() }
        viewModelScope.loadInto(serverSettings) { api.serverSettings() }
    }

    fun setTheme(id: Int) = viewModelScope.launch { prefs.setTheme(id) }
    fun setRefresh(sec: Int) = viewModelScope.launch { prefs.setRefresh(sec) }
    fun setAlerts(on: Boolean) = viewModelScope.launch { prefs.setAlerts(on) }

    fun setBaseUrl(url: String) = viewModelScope.launch {
        prefs.setBaseUrl(url)
        delay(300)
        testConnection(); reloadServer()
    }

    fun saveToken(t: String?) { tokenSaved.value = prefs.saveToken(t) }

    fun testConnection() {
        health.value = Load.Loading
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            health.value = try { Load.Ok(api.health() to (System.currentTimeMillis() - t0)) } catch (e: CancellationException) { throw e } catch (e: Throwable) { Load.Err(e.friendly()) }
        }
    }

    fun saveServerPatch(patch: JsonObject) {
        saveResult.value = "Saving…"
        viewModelScope.launch {
            saveResult.value = try {
                val r = api.saveServerSettings(patch)
                (r["settings"] as? JsonObject)?.let { serverSettings.value = Load.Ok(buildJsonObject { (serverSettings.value.value ?: JsonObject(emptyMap())).forEach { (k, v) -> put(k, v) }; put("settings", it) }) }
                "Saved on the server"
            } catch (e: CancellationException) { throw e } catch (e: Throwable) { "Save failed: ${e.friendly()}" }
        }
    }
}
