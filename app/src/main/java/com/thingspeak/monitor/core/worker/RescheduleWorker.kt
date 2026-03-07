package com.thingspeak.monitor.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.thingspeak.monitor.core.datastore.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Worker responsible for safely rescheduling the DataSyncWorker.
 * Used after device boot or app update to fetch the correct interval and restart periodic work.
 */
@HiltWorker
class RescheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val appPreferences: AppPreferences
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val intervalMinutes = appPreferences.observeSyncInterval().first()
            DataSyncWorker.schedule(applicationContext, intervalMinutes)

            val isHighFreqEnabled = appPreferences.observeIsHighFrequencyEnabled().first()
            if (isHighFreqEnabled) {
                DataSyncService.start(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
