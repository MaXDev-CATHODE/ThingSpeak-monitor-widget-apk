package com.thingspeak.monitor.core.notifications

import android.Manifest
import android.app.Notification
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
                "ThingSpeak Alerts", // Direct string for debugging
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts for ThingSpeak violations"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(alertsChannel)
        }
    }

    override fun fireAlert(channelId: Long, violations: List<AlertThreshold>) {
        android.util.Log.d("AlertManager", "fireAlert called for channel $channelId with ${violations.size} violations")
        
        if (!hasPostNotificationsPermission()) {
            android.util.Log.w("AlertManager", "MISSING POST_NOTIFICATIONS PERMISSION!")
            return
        }

        if (violations.isEmpty()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("channel_id", channelId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            channelId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_alert_summary, violations.size))
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY_THINGSPEAK_ALERTS)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        val summaryNotification = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setStyle(NotificationCompat.InboxStyle()
                .setSummaryText(context.getString(R.string.notification_channel_alerts_name)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY_THINGSPEAK_ALERTS)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        val signatureString = violations.joinToString("|") { "${it.fieldNumber}:${it.minValue}:${it.maxValue}" }
        val notificationId = (channelId.hashCode() * 31 + signatureString.hashCode())

        try {
            android.util.Log.d("AlertManager", "Posting notification for channel $channelId. ID=$notificationId. Channel=$ALERTS_CHANNEL_ID")
            notificationManager.notify(notificationId, notification)
            notificationManager.notify(SUMMARY_ID, summaryNotification)
        } catch (e: Exception) {
            android.util.Log.e("AlertManager", "Failed to post notification", e)
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
        private const val ALERTS_CHANNEL_ID = "alerts_channel_v2"
        private const val GROUP_KEY_THINGSPEAK_ALERTS = "com.thingspeak.monitor.ALERTS_GROUP"
        private const val SUMMARY_ID = 1000
    }
}
