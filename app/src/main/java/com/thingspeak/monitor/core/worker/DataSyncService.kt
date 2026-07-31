package com.thingspeak.monitor.core.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.thingspeak.monitor.MainActivity
import com.thingspeak.monitor.R
import com.thingspeak.monitor.core.datastore.AppPreferences
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import com.thingspeak.monitor.feature.channel.domain.usecase.SyncChannelUseCase
import com.thingspeak.monitor.feature.widget.WIDGET_LOG_TAG
import com.thingspeak.monitor.feature.widget.WidgetChartGenerator
import com.thingspeak.monitor.feature.widget.WidgetPrefsKeys
import com.thingspeak.monitor.feature.widget.WidgetUpdateHelper
import com.thingspeak.monitor.feature.channel.domain.model.toSavedChannel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Foreground service for high-frequency data monitoring.
 */
@AndroidEntryPoint
class DataSyncService : Service() {

    @Inject lateinit var repository: ChannelRepository
    @Inject lateinit var syncChannelUseCase: SyncChannelUseCase
    @com.thingspeak.monitor.core.di.IoDispatcher @Inject lateinit var ioDispatcher: CoroutineDispatcher
    @Inject lateinit var appPreferences: AppPreferences

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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

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
                    val startTime = System.currentTimeMillis()
                    Log.i(TAG, "High-frequency sync CYCLE START: interval=$intervalMinutes min")
                    
                    updateNotification("Monitoring active (Interval: $intervalMinutes min)")
                    
                    val channels = repository.observeChannelList().first()

                    channels.forEach { channel ->
                        Log.v(TAG, "Syncing channel ${channel.id} via Service...")
                        val result = syncChannelUseCase(channel)
                        updateWidgetsForChannel(result)
                    }

                    val duration = System.currentTimeMillis() - startTime
                    Log.i(TAG, "High-frequency sync CYCLE END. Took ${duration}ms. Sleeping for $intervalMinutes min.")

                    delay(intervalMinutes * 60 * 1000L)
                } catch (e: Exception) {
                    Log.e(TAG, "Sync loop error", e)
                    delay(60 * 1000L) // Wait a minute on error
                }
            }
        }
    }

    private suspend fun updateWidgetsForChannel(result: SyncChannelUseCase.Result) {
        val channel = result.channel

        val chartBase64: String? = run {
            if (channel.preferredChartFields?.isNotEmpty() != true &&
                channel.widgetVisibleFields?.isNotEmpty() != true) return@run null
            val entries = repository.observeFeed(channel.id).first()
            if (entries.isEmpty()) return@run null
            try {
                WidgetChartGenerator.generateChartBase64(
                    context = this@DataSyncService,
                    entries = entries.reversed(),
                    fieldIndices = channel.preferredChartFields?.ifEmpty { null }
                            ?: channel.widgetVisibleFields?.ifEmpty { null }
                            ?: setOf(1),
                    isNormalized = channel.isNormalized
                )
            } catch (e: Exception) {
                Log.w(TAG, "updateWidgetsForChannel: Chart generation failed for ${channel.id}", e)
                null
            }
        }

        WidgetUpdateHelper.pushToBoundWidgets(
            context = this,
            channel = channel.toSavedChannel(),
            latestFeed = result.latestEntry,
            violatedMinFields = result.allViolations.filter {
                it.condition == WidgetPrefsKeys.ALERT_CONDITION_LESS_THAN
            }.map { it.fieldNumber }.toSet(),
            violatedMaxFields = result.allViolations.filter {
                it.condition == WidgetPrefsKeys.ALERT_CONDITION_GREATER_THAN
            }.map { it.fieldNumber }.toSet(),
            minSetFields = result.channelRules.filter {
                it.condition == WidgetPrefsKeys.ALERT_CONDITION_LESS_THAN && it.isEnabled
            }.map { it.fieldNumber }.toSet(),
            maxSetFields = result.channelRules.filter {
                it.condition == WidgetPrefsKeys.ALERT_CONDITION_GREATER_THAN && it.isEnabled
            }.map { it.fieldNumber }.toSet(),
            chartBase64 = chartBase64
        )
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
        private val TAG = WIDGET_LOG_TAG
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
                DataSyncWorker.runOnce(context)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DataSyncService::class.java)
            context.stopService(intent)
        }
    }
}