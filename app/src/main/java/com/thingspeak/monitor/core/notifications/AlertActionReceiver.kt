package com.thingspeak.monitor.core.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.thingspeak.monitor.core.worker.DataSyncWorker

/**
 * Handles notification action button intents:
 * - DISMISS: cancels the notification.
 * - REFRESH: triggers immediate DataSyncWorker sync.
 */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val summaryId = intent.getIntExtra(EXTRA_SUMMARY_ID, -1)

        when (intent.action) {
            ACTION_DISMISS -> {
                Log.i(TAG, "Dismiss action: notificationId=$notificationId, summaryId=$summaryId")
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (notificationId > 0) nm.cancel(notificationId)
                if (summaryId > 0) nm.cancel(summaryId)
            }
            ACTION_REFRESH -> {
                Log.i(TAG, "REFRESH action: triggering DataSyncWorker")
                DataSyncWorker.runOnce(context)
                // Also dismiss after triggering sync
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (notificationId > 0) nm.cancel(notificationId)
                if (summaryId > 0) nm.cancel(summaryId)
            }
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.thingspeak.monitor.ACTION_DISMISS_ALERT"
        const val ACTION_REFRESH = "com.thingspeak.monitor.ACTION_REFRESH_ALERT"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_SUMMARY_ID = "summary_id"
        private const val TAG = "AlertActionReceiver"
    }
}