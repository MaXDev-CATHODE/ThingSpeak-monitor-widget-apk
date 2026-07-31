package com.thingspeak.monitor.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * BroadcastReceiver that restarts the WidgetRefreshWorker when the device boots.
 * Ensures data updates even if the main app hasn't been opened recently.
 * On MY_PACKAGE_REPLACED, also triggers an immediate sync so widgets don't
 * show stale cached data until the next periodic cycle.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.i(WIDGET_LOG_TAG, "BootReceiver: Starting DataSyncWorker after boot/update")
            scheduleDataSync(context)
        }
    }

    companion object {
        fun scheduleDataSync(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<com.thingspeak.monitor.core.worker.RescheduleWorker>()
                .build()
            
            androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "reschedule_initialization",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    request
                )

            // Trigger immediate sync after package replacement so widgets
            // show fresh data right away instead of stale cached values.
            com.thingspeak.monitor.core.worker.DataSyncWorker.runOnce(context)
        }
    }
}
