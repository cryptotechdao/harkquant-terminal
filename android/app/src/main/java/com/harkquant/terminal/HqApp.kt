package com.harkquant.terminal

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.harkquant.terminal.data.AppSettings
import com.harkquant.terminal.data.HqApi
import com.harkquant.terminal.data.NewsItem
import com.harkquant.terminal.data.Prefs
import com.harkquant.terminal.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Tiny service locator: one API client, one live repository and the preferences for the process. */
class HqApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var prefs: Prefs
    lateinit var api: HqApi
    lateinit var repo: Repository

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    @Volatile private var foreground = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        api = HqApi(baseUrl = { _settings.value.baseUrl }, token = { prefs.token.value })
        repo = Repository(api, appScope)
        createChannels()
        appScope.launch(Dispatchers.IO) { prefs.loadToken() }

        appScope.launch {
            var lastUrl: String? = null
            prefs.settings.collect { s ->
                _settings.value = s
                repo.refreshSec = s.refreshSec
                if (lastUrl != null && lastUrl != s.baseUrl && repo.isRunning) repo.restart()
                lastUrl = s.baseUrl
                if (!foreground) { if (s.headlineAlerts) repo.start() else repo.stop() }
            }
        }
        appScope.launch {
            repo.incoming.collect { item -> if (_settings.value.headlineAlerts && !foreground) notifyHeadline(item) }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { foreground = true; repo.start() }
            override fun onStop(owner: LifecycleOwner) {
                foreground = false
                // keep the stream only when the user asked for headline alerts
                if (!_settings.value.headlineAlerts) repo.stop()
            }
        })
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_HEADLINES, "High-impact headlines", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "HIGH and CRITICAL headlines from the HarkQuant News Desk"
            })
            nm.createNotificationChannel(NotificationChannel(CH_GENERAL, "General", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun notifyHeadline(item: NewsItem) {
        if (item.impact.uppercase() !in setOf("HIGH", "CRITICAL")) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, CH_HEADLINES)
            .setSmallIcon(R.drawable.ic_stat_hq)
            .setContentTitle("${item.impact.uppercase()} · ${item.label.ifBlank { item.sourceName }}")
            .setContentText(item.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.title))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(item.id.hashCode(), n) }
    }

    companion object {
        const val CH_HEADLINES = "headlines"
        const val CH_GENERAL = "general"
    }
}
