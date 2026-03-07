package com.thingspeak.monitor.core.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.thingspeak.monitor.MainActivity
import com.thingspeak.monitor.R
import com.thingspeak.monitor.core.datastore.AppPreferences
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.notifications.AlertManager
import com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import com.thingspeak.monitor.feature.channel.domain.usecase.CheckAlertThresholdsUseCase
import com.thingspeak.monitor.feature.widget.ThingSpeakGlanceWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Foreground service for high-frequency data monitoring.
 */
@AndroidEntryPoint
class DataSyncService : Service() {

    @Inject lateinit var repository: ChannelRepository
    @Inject lateinit var syncChannelUseCase: com.thingspeak.monitor.feature.channel.domain.usecase.SyncChannelUseCase
    @com.thingspeak.monitor.core.di.IoDispatcher @Inject lateinit var ioDispatcher: CoroutineDispatcher
    @Inject lateinit var appPreferences: com.thingspeak.monitor.core.datastore.AppPreferences

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastNotificationContent: String? = null
    private var syncJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        val notification = createNotification("Initializing background monitoring...")
        startForeground(NOTIFICATION_ID, notification)

        startSyncLoop()
        
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            while (isActive) {
                try {
                    val isEnabled = appPreferences.observeIsHighFrequencyEnabled().first()
                    if (!isEnabled) {
                        Log.i(TAG, "High frequency monitoring disabled — stopping service")
                        stopSelf()
                        break
                    }

                    val intervalMinutes = appPreferences.observeHighFrequencyInterval().first()
                    Log.d(TAG, "Starting high-frequency sync (Interval: $intervalMinutes min)")
                    
                    updateNotification("Monitoring active (Interval: $intervalMinutes min)")
                    
                    val channels = repository.observeChannelList().first()
                    var anyAlertTriggered = false
                    var anyDataUpdated = false

                    channels.forEach { channel ->
                        val result = syncChannelUseCase(channel)
                        if (result.hasNewData) anyDataUpdated = true
                        if (result.alertStateChanged) anyAlertTriggered = true
                        
                        result.error?.let {
                            Log.w(TAG, "Sync error for channel ${channel.id}: ${it.message}")
                        }
                    }

                    if (anyDataUpdated || anyAlertTriggered) {
                        Log.d(TAG, "Update needed. triggering widget refresh.")
                        updateAllWidgets()
                    }
                    
                    delay(intervalMinutes * 60 * 1000L)
                } catch (e: Exception) {
                    Log.e(TAG, "Sync loop error", e)
                    delay(60 * 1000L) // Wait a minute on error
                }
            }
        }
    }


    private suspend fun updateAllWidgets() {
        val manager = GlanceAppWidgetManager(this)
        val widgetClasses = listOf(
            ThingSpeakGlanceWidget::class.java,
            com.thingspeak.monitor.feature.widget.ValueGridWidget::class.java
        )

        widgetClasses.forEach { widgetClass ->
            val glanceIds = manager.getGlanceIds(widgetClass)
            for (id in glanceIds) {
                if (widgetClass == ThingSpeakGlanceWidget::class.java) {
                    ThingSpeakGlanceWidget().update(this, id)
                } else if (widgetClass == com.thingspeak.monitor.feature.widget.ValueGridWidget::class.java) {
                    com.thingspeak.monitor.feature.widget.ValueGridWidget().update(this, id)
                }
            }
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ThingSpeak Monitor Active")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(content: String) {
        if (content == lastNotificationContent) return
        lastNotificationContent = content
        
        val notification = createNotification(content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for high-frequency data synchronization"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "DataSyncService"
        private const val CHANNEL_ID = "background_monitor_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, DataSyncService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start DataSyncService as FGS. Falling back to WorkManager.", e)
                // If FGS start fails (e.g. background restriction), trigger a one-time sync via WorkManager
                DataSyncWorker.runOnce(context)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DataSyncService::class.java)
            context.stopService(intent)
        }
    }
}
