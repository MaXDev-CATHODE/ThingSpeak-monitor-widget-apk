package com.thingspeak.monitor.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.thingspeak.monitor.MainActivity
import com.thingspeak.monitor.R
import com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Implementation of [AlertManager] responsible for creating channels and triggering notifications.
 */
class AlertManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AlertManager {

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertsChannel = NotificationChannel(
                ALERTS_CHANNEL_ID,
                context.getString(R.string.notification_channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_alerts_desc)
                enableVibration(true)
            }
            
            val systemChannel = android.app.NotificationChannel(
                "system_updates",
                "System Updates",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General system notifications"
            }

            notificationManager.createNotificationChannel(alertsChannel)
            notificationManager.createNotificationChannel(systemChannel)
        }
    }

    override fun fireAlert(channelId: Long, violations: List<AlertThreshold>) {
        if (!hasPostNotificationsPermission()) {
            return
        }

        if (violations.isEmpty()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Potentially pass channel_id as extra to navigate specifically
            putExtra("channel_id", channelId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            channelId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build a notification for each violation, but since we want to avoid spam,
        // we summarize them or post a single notification per channel with an InboxStyle.
        val inboxStyle = NotificationCompat.InboxStyle()
        violations.forEach { violation ->
            val content = context.getString(
                R.string.notification_alert_content,
                violation.fieldName ?: "Field ${violation.fieldNumber}",
                if (violation.minValue != null) "below ${violation.minValue}" else "above ${violation.maxValue}"
            )
            inboxStyle.addLine(content)
        }

        val title = context.getString(R.string.notification_alert_title, channelId.toString())

        val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("Detected ${violations.size} thresholds violations!")
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY_THINGSPEAK_ALERTS)
            .setAutoCancel(true)
            .build()

        val summaryNotification = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setStyle(NotificationCompat.InboxStyle()
                .setSummaryText("Threshold alerts"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY_THINGSPEAK_ALERTS)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        try {
            // Channel ID is used as unique notification ID for the channel
            notificationManager.notify(channelId.toInt(), notification)
            notificationManager.notify(SUMMARY_ID, summaryNotification)
        } catch (e: SecurityException) {
            // Ignored, handled by hasPostNotificationsPermission but keeping for safety
        }
    }

    private fun hasPostNotificationsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    companion object {
        private const val ALERTS_CHANNEL_ID = "alerts_channel"
        private const val GROUP_KEY_THINGSPEAK_ALERTS = "com.thingspeak.monitor.ALERTS_GROUP"
        private const val SUMMARY_ID = 1000
    }
}
