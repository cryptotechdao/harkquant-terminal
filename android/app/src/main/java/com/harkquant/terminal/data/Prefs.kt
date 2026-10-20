package com.harkquant.terminal.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.harkquant.terminal.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hq_settings")

/** User preferences in DataStore; the server token lives only in EncryptedSharedPreferences. */
class Prefs(private val context: Context) {
    private object K {
        val theme = intPreferencesKey("theme_id")
        val baseUrl = stringPreferencesKey("base_url")
        val refresh = intPreferencesKey("refresh_sec")
        val alerts = booleanPreferencesKey("headline_alerts")
        val chartTf = stringPreferencesKey("chart_tf")
        val tvSymbol = stringPreferencesKey("tv_symbol")
        val anSymbol = stringPreferencesKey("analysis_symbol")
        val indicators = stringPreferencesKey("indicators_json")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeId = (p[K.theme] ?: AppSettings.DEFAULT_THEME).coerceIn(1, 20),
            baseUrl = p[K.baseUrl]?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_BASE_URL,
            refreshSec = (p[K.refresh] ?: 20).coerceIn(5, 300),
            headlineAlerts = p[K.alerts] ?: false,
            chartTf = p[K.chartTf] ?: "15m",
            tvSymbol = p[K.tvSymbol] ?: "XAUUSD",
            analysisSymbol = p[K.anSymbol] ?: "XAUUSD",
            indicatorsJson = p[K.indicators] ?: "",
        )
    }

    suspend fun setTheme(id: Int) = context.dataStore.edit { it[K.theme] = id.coerceIn(1, 20) }
    suspend fun setBaseUrl(url: String) = context.dataStore.edit { it[K.baseUrl] = HqApi.normalizeBase(url) }
    suspend fun setRefresh(sec: Int) = context.dataStore.edit { it[K.refresh] = sec.coerceIn(5, 300) }
    suspend fun setAlerts(on: Boolean) = context.dataStore.edit { it[K.alerts] = on }
    suspend fun setChartTf(tf: String) = context.dataStore.edit { it[K.chartTf] = tf }
    suspend fun setTvSymbol(s: String) = context.dataStore.edit { it[K.tvSymbol] = s }
    suspend fun setAnalysisSymbol(s: String) = context.dataStore.edit { it[K.anSymbol] = s }
    suspend fun setIndicators(json: String) = context.dataStore.edit { it[K.indicators] = json }

    // ------------------------------------------------------------ token (encrypted at rest)
    private val secure: SharedPreferences? by lazy {
        runCatching {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, "hq_secure", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.onFailure { Log.w("HQ", "Encrypted storage unavailable; token kept in memory only", it) }.getOrNull()
    }
    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    fun loadToken() { _token.value = secure?.getString(TOKEN, null) ?: _token.value }

    /** True when it was persisted encrypted, false when only kept for this session. */
    fun saveToken(value: String?): Boolean {
        val v = value?.trim()?.takeIf { it.isNotEmpty() }
        _token.value = v
        val sp = secure ?: return false
        sp.edit().apply { if (v == null) remove(TOKEN) else putString(TOKEN, v) }.apply()
        return true
    }

    private companion object { const val TOKEN = "settings_token" }
}
