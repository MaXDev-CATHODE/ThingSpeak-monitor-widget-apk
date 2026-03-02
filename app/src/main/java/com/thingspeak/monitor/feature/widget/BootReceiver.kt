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
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            Log.i("BootReceiver", "Starting DataSyncWorker after boot/update")
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
        }
    }
}
