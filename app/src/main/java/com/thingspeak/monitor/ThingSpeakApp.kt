package com.thingspeak.monitor

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.gms.ads.MobileAds
import com.thingspeak.monitor.feature.widget.BootReceiver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point for ThingSpeak Monitor Widget.
 *
 * The [@HiltAndroidApp] annotation initializes the Hilt component and generates
 * the base dependency container. Implements [Configuration.Provider] to supply
 * a custom [HiltWorkerFactory] for WorkManager, enabling @HiltWorker injection.
 */
@HiltAndroidApp
class ThingSpeakApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var migrationManager: com.thingspeak.monitor.core.data.DataMigrationManager

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)

        migrationManager.startMigration()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
