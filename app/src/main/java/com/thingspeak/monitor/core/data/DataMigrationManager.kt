package com.thingspeak.monitor.core.data

import com.thingspeak.monitor.core.datastore.AlertPreferences
import com.thingspeak.monitor.core.datastore.AppPreferences
import com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages data migration between different sources (e.g., DataStore -> Room).
 * Wykonywane jednorazowo przy starcie aplikacji lub po aktualizacji.
 */
@Singleton
class DataMigrationManager @Inject constructor(
    private val alertPreferences: AlertPreferences,
    private val appPreferences: AppPreferences,
    private val channelRepository: ChannelRepository,
    @com.thingspeak.monitor.core.di.ApplicationScope private val scope: CoroutineScope
) {
    fun startMigration() {
        scope.launch {
            if (appPreferences.observeMigrationCompleted().first()) return@launch
            
            try {
                migrateAlertsFromDataStoreToRoom()
                appPreferences.setMigrationCompleted(true)
            } catch (e: Exception) {
                // In a real app, use Timber or Firebase to log migration failure
            }
        }
    }

    private suspend fun migrateAlertsFromDataStoreToRoom() {
        // Fetch alerts from DataStore
        val oldAlerts = alertPreferences.observe().first()
        if (oldAlerts.isNotEmpty()) {
            oldAlerts.forEach { alert ->
                channelRepository.saveAlert(
                    AlertThreshold(
                        channelId = alert.channelId,
                        fieldNumber = alert.fieldNumber,
                        fieldName = alert.fieldName,
                        minValue = alert.minValue,
                        maxValue = alert.maxValue,
                        isEnabled = alert.isEnabled
                    )
                )
            }
            // After successful migration, we clear old data to avoid redundancy
            alertPreferences.clearAll() 
        }
    }
}
