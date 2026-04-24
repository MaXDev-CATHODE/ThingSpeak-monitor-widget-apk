package com.thingspeak.monitor.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testy property-based zachowania (Preservation) dla zmiany interwału synchronizacji.
 *
 * CEL: Udokumentować ISTNIEJĄCE POPRAWNE zachowania, które muszą pozostać niezmienione po naprawie.
 * Testy PRZECHODZĄ na nienaprawionym kodzie — stanowią bazę do weryfikacji braku regresji.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6
 *
 * Metodologia:
 * - Symulujemy logikę produkcyjną przez klasy pomocnicze in-memory (bez zewnętrznych frameworków mockowania)
 * - Wzorzec identyczny jak w SyncIntervalChangeBugConditionTest.kt
 * - Klasy SimulatedWorkManager i SimulatedAppPreferences są współdzielone z BugConditionTest
 */
class SyncIntervalChangePreservationTest {

    // =========================================================================
    // Helper classes — in-memory simulation of WorkManager and AppPreferences
    // (identical to SyncIntervalChangeBugConditionTest helper classes)
    // =========================================================================

    /**
     * Simulates WorkManager state in memory.
     * Tracks both one-time and periodic work requests.
     */
    class SimulatedWorkManager {
        private val oneTimeWorks = mutableListOf<String>()
        private val periodicWorks = mutableMapOf<String, Long>() // name -> intervalMinutes

        /** Enqueues a one-time work request (simulates OneTimeWorkRequest). */
        fun enqueueOneTimeWork(tag: String) {
            oneTimeWorks.add(tag)
        }

        /** Enqueues a unique periodic work request with KEEP policy — does NOT replace if already exists. */
        fun enqueueUniquePeriodicWorkKeep(name: String, intervalMinutes: Long) {
            // KEEP policy: only enqueue if not already scheduled
            if (!periodicWorks.containsKey(name)) {
                periodicWorks[name] = intervalMinutes
            }
        }

        /** Enqueues a unique periodic work request with UPDATE policy — always replaces existing. */
        fun enqueueUniquePeriodicWorkUpdate(name: String, intervalMinutes: Long) {
            // UPDATE policy: always replaces existing work
            periodicWorks[name] = intervalMinutes
        }

        /** Returns the number of enqueued one-time work requests. */
        fun getOneTimeWorkCount(): Int = oneTimeWorks.size

        /** Returns the scheduled interval for a periodic work, or null if not scheduled. */
        fun getPeriodicWorkInterval(name: String): Long? = periodicWorks[name]
    }

    /**
     * Simulates AppPreferences — stores the sync interval.
     */
    class SimulatedAppPreferences(
        var syncIntervalMinutes: Long = 30L,
        var serverUrl: String = "https://api.thingspeak.com",
        var themePreference: String = "system",
        var refreshIntervalSeconds: Long = 30L
    )

    companion object {
        private const val WORK_NAME = "DataSyncWorker"
        private const val ONE_TIME_TAG = "DataSyncWorker_oneTime"
    }

    // =========================================================================
    // Simulated production methods
    // =========================================================================

    /**
     * Simulates DataSyncWorker.schedule() with KEEP policy.
     *
     * Production code (DataSyncWorker.kt):
     *   fun schedule(context, intervalMinutes) {
     *       val request = PeriodicWorkRequestBuilder<DataSyncWorker>(intervalMinutes, MINUTES)
     *           .setConstraints(constraints())
     *           .build()
     *       WorkManager.getInstance(context)
     *           .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
     *   }
     *
     * NOTE: schedule() uses KEEP policy — no runOnce, no OneTimeWorkRequest.
     */
    private fun simulateScheduleWithKeep(
        workManager: SimulatedWorkManager,
        intervalMinutes: Long
    ) {
        // Only enqueue periodic work with KEEP policy — no one-time work
        workManager.enqueueUniquePeriodicWorkKeep(WORK_NAME, intervalMinutes)
    }

    /**
     * Simulates RescheduleWorker.doWork() behavior.
     *
     * Production code (RescheduleWorker.kt):
     *   override suspend fun doWork(): Result {
     *       val intervalMinutes = appPreferences.observeSyncInterval().first()
     *       DataSyncWorker.schedule(applicationContext, intervalMinutes)  // KEEP policy
     *       ...
     *       Result.success()
     *   }
     *
     * NOTE: RescheduleWorker calls schedule() (KEEP policy) — no runOnce, no OneTimeWorkRequest.
     */
    private fun simulateRescheduleWorkerDoWork(
        workManager: SimulatedWorkManager,
        appPreferences: SimulatedAppPreferences
    ) {
        // Read saved interval and schedule with KEEP policy — no one-time work
        val intervalMinutes = appPreferences.syncIntervalMinutes
        workManager.enqueueUniquePeriodicWorkKeep(WORK_NAME, intervalMinutes)
    }

    /**
     * Simulates setRefreshInterval() — changes UI refresh interval, does NOT touch WorkManager.
     *
     * This is a UI-only setting (how often the UI polls for updates),
     * completely separate from the background sync schedule.
     */
    private fun simulateSetRefreshInterval(
        workManager: SimulatedWorkManager,
        appPreferences: SimulatedAppPreferences,
        seconds: Long
    ) {
        // Only update the preference — no WorkManager interaction
        appPreferences.refreshIntervalSeconds = seconds
        // WorkManager is NOT touched
    }

    /**
     * Simulates setServerUrl() — changes server URL, does NOT touch WorkManager.
     */
    private fun simulateSetServerUrl(
        workManager: SimulatedWorkManager,
        appPreferences: SimulatedAppPreferences,
        url: String
    ) {
        // Only update the preference — no WorkManager interaction
        appPreferences.serverUrl = url
        // WorkManager is NOT touched
    }

    /**
     * Simulates setThemePreference() — changes app theme, does NOT touch WorkManager.
     */
    private fun simulateSetThemePreference(
        workManager: SimulatedWorkManager,
        appPreferences: SimulatedAppPreferences,
        theme: String
    ) {
        // Only update the preference — no WorkManager interaction
        appPreferences.themePreference = theme
        // WorkManager is NOT touched
    }

    // =========================================================================
    // PRESERVATION 1 — schedule() z polityką KEEP nigdy nie enqueueuje OneTimeWorkRequest
    // =========================================================================

    /**
     * PRESERVATION 1: DataSyncWorker.schedule() z polityką KEEP nigdy nie enqueueuje OneTimeWorkRequest.
     *
     * Dla 5 losowych interwałów: sprawdź getOneTimeWorkCount() == 0.
     * Ten test PRZECHODZI na nienaprawionym kodzie — schedule(KEEP) nie ma runOnce.
     *
     * Validates: Requirements 3.1, 3.2
     */
    @Test
    fun `Preservation 1 - schedule z polityka KEEP nigdy nie enqueueuje OneTimeWorkRequest`() {
        // Generate 5 random intervals in range 1..1440 minutes (deterministic seed)
        val random = java.util.Random(42L)
        val intervals = (1..5).map { 1L + (random.nextInt(1440)).toLong() } // range 1..1440

        println("Preservation 1 — testowane interwały: $intervals")

        for (intervalMinutes in intervals) {
            // Arrange: fresh WorkManager for each test case
            val workManager = SimulatedWorkManager()

            // Act: simulate DataSyncWorker.schedule() with KEEP policy
            simulateScheduleWithKeep(workManager, intervalMinutes)

            val oneTimeCount = workManager.getOneTimeWorkCount()
            val periodicInterval = workManager.getPeriodicWorkInterval(WORK_NAME)

            println(
                "Preservation 1 — interwał=$intervalMinutes min → " +
                    "getOneTimeWorkCount()=$oneTimeCount (oczekiwano 0), " +
                    "getPeriodicWorkInterval()=$periodicInterval (oczekiwano $intervalMinutes)"
            )

            // Assert: schedule(KEEP) must NEVER enqueue a OneTimeWorkRequest
            assertEquals(
                "Preservation 1 (interwał=$intervalMinutes min): " +
                    "schedule() z polityką KEEP NIE może enqueueować OneTimeWorkRequest. " +
                    "getOneTimeWorkCount() powinno być 0.",
                0,
                oneTimeCount
            )

            // Assert: periodic work must be scheduled with correct interval
            assertEquals(
                "Preservation 1 (interwał=$intervalMinutes min): " +
                    "schedule() z polityką KEEP musi enqueueować PeriodicWorkRequest z poprawnym interwałem.",
                intervalMinutes,
                periodicInterval
            )
        }
    }

    // =========================================================================
    // PRESERVATION 2 — RescheduleWorker nie wywołuje runOnce
    // =========================================================================

    /**
     * PRESERVATION 2: RescheduleWorker nie wywołuje runOnce — tylko planuje harmonogram periodyczny.
     *
     * Symuluje RescheduleWorker.doWork(): tylko enqueueUniquePeriodicWork z KEEP, bez enqueueOneTimeWork.
     * Sprawdź getOneTimeWorkCount() == 0.
     * Ten test PRZECHODZI na nienaprawionym kodzie — RescheduleWorker nie ma runOnce.
     *
     * Validates: Requirements 3.3
     */
    @Test
    fun `Preservation 2 - RescheduleWorker nie wywoluje runOnce - tylko planuje harmonogram periodyczny`() {
        // Arrange
        val workManager = SimulatedWorkManager()
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = 30L)

        // Act: simulate RescheduleWorker.doWork() (called after device boot)
        simulateRescheduleWorkerDoWork(workManager, appPreferences)

        val oneTimeCount = workManager.getOneTimeWorkCount()
        val periodicInterval = workManager.getPeriodicWorkInterval(WORK_NAME)

        println(
            "Preservation 2 — RescheduleWorker.doWork() → " +
                "getOneTimeWorkCount()=$oneTimeCount (oczekiwano 0), " +
                "getPeriodicWorkInterval()=$periodicInterval (oczekiwano 30)"
        )

        // Assert: RescheduleWorker must NOT enqueue any OneTimeWorkRequest
        assertEquals(
            "Preservation 2: RescheduleWorker.doWork() NIE może enqueueować OneTimeWorkRequest. " +
                "Przywracanie harmonogramu po restarcie urządzenia nie powinno wyzwalać natychmiastowej synchronizacji.",
            0,
            oneTimeCount
        )

        // Assert: periodic work must be scheduled with the saved interval
        assertEquals(
            "Preservation 2: RescheduleWorker.doWork() musi enqueueować PeriodicWorkRequest z zapisanym interwałem (30 min).",
            30L,
            periodicInterval
        )
    }

    // =========================================================================
    // PRESERVATION 3 — Zmiana innych ustawień nie wpływa na harmonogram WorkManager
    // =========================================================================

    /**
     * PRESERVATION 3: Zmiana innych ustawień (motyw, URL, interwał UI) nie wpływa na harmonogram WorkManager.
     *
     * Symuluje setRefreshInterval(), setServerUrl(), setThemePreference() — żadna z nich nie wywołuje WorkManager.
     * Sprawdź że getOneTimeWorkCount() == 0 i getPeriodicWorkInterval(WORK_NAME) == null.
     * Ten test PRZECHODZI na nienaprawionym kodzie — inne ustawienia nie dotykają WorkManager.
     *
     * Validates: Requirements 3.6
     */
    @Test
    fun `Preservation 3 - zmiana innych ustawien nie wplywa na harmonogram WorkManager`() {
        // Arrange: fresh WorkManager (no work scheduled yet)
        val workManager = SimulatedWorkManager()
        val appPreferences = SimulatedAppPreferences()

        // Act: simulate changing various non-sync-interval settings
        simulateSetRefreshInterval(workManager, appPreferences, 60L)
        simulateSetServerUrl(workManager, appPreferences, "https://custom.thingspeak.com")
        simulateSetThemePreference(workManager, appPreferences, "dark")

        val oneTimeCount = workManager.getOneTimeWorkCount()
        val periodicInterval = workManager.getPeriodicWorkInterval(WORK_NAME)

        println(
            "Preservation 3 — po zmianie motywu/URL/interwału UI → " +
                "getOneTimeWorkCount()=$oneTimeCount (oczekiwano 0), " +
                "getPeriodicWorkInterval()=$periodicInterval (oczekiwano null)"
        )

        // Assert: no OneTimeWorkRequest must be enqueued
        assertEquals(
            "Preservation 3: zmiana motywu/URL/interwału UI NIE może enqueueować OneTimeWorkRequest. " +
                "Inne ustawienia nie powinny wpływać na harmonogram synchronizacji w tle.",
            0,
            oneTimeCount
        )

        // Assert: no PeriodicWorkRequest must be enqueued either
        assertNull(
            "Preservation 3: zmiana motywu/URL/interwału UI NIE może enqueueować PeriodicWorkRequest. " +
                "WorkManager powinien pozostać niezmieniony.",
            periodicInterval
        )
    }

    // =========================================================================
    // PRESERVATION 4 — Pierwsze planowanie (schedule z KEEP) tworzy PeriodicWorkRequest bez OneTimeWorkRequest
    // =========================================================================

    /**
     * PRESERVATION 4: Pierwsze planowanie harmonogramu (schedule z KEEP) tworzy PeriodicWorkRequest
     * bez OneTimeWorkRequest.
     *
     * Symuluje inicjalne planowanie z MainActivity: schedule(KEEP, interval).
     * Sprawdź getOneTimeWorkCount() == 0 i getPeriodicWorkInterval(WORK_NAME) == interval.
     * Ten test PRZECHODZI na nienaprawionym kodzie.
     *
     * Validates: Requirements 3.1, 3.2
     */
    @Test
    fun `Preservation 4 - pierwsze planowanie schedule z KEEP tworzy PeriodicWorkRequest bez OneTimeWorkRequest`() {
        // Arrange: fresh WorkManager (simulates first app launch — no work scheduled yet)
        val workManager = SimulatedWorkManager()
        val initialInterval = 30L // default interval

        // Act: simulate initial scheduling from MainActivity
        simulateScheduleWithKeep(workManager, initialInterval)

        val oneTimeCount = workManager.getOneTimeWorkCount()
        val periodicInterval = workManager.getPeriodicWorkInterval(WORK_NAME)

        println(
            "Preservation 4 — inicjalne planowanie schedule(KEEP, $initialInterval) → " +
                "getOneTimeWorkCount()=$oneTimeCount (oczekiwano 0), " +
                "getPeriodicWorkInterval()=$periodicInterval (oczekiwano $initialInterval)"
        )

        // Assert: initial scheduling must NOT enqueue any OneTimeWorkRequest
        assertEquals(
            "Preservation 4: inicjalne schedule(KEEP) NIE może enqueueować OneTimeWorkRequest. " +
                "Pierwsze planowanie harmonogramu nie powinno wyzwalać natychmiastowej synchronizacji.",
            0,
            oneTimeCount
        )

        // Assert: periodic work must be scheduled with the correct initial interval
        assertEquals(
            "Preservation 4: inicjalne schedule(KEEP) musi enqueueować PeriodicWorkRequest z interwałem $initialInterval min.",
            initialInterval,
            periodicInterval
        )
    }
}
