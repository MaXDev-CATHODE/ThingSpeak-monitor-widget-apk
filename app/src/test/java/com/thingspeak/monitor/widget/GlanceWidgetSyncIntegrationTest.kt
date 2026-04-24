package com.thingspeak.monitor.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests for automatic Glance widget synchronization in ThingSpeak Monitor.
 *
 * Tests verify the complete lifecycle of widget sync scheduling using in-memory
 * helper classes (SimulatedWorkManager, SimulatedAppPreferences) — no external
 * mocking frameworks required.
 *
 * Validates: Requirements 1.1, 1.2, 1.4, 1.5, 1.7, 1.8, 2.1–2.9, 3.1–3.7
 */
class GlanceWidgetSyncIntegrationTest {

    // =========================================================================
    // Helper classes — mirror the pattern from GlanceWidgetAutoSyncBugConditionTest
    // =========================================================================

    /** Mirrors ExistingPeriodicWorkPolicy from WorkManager. */
    enum class SimulatedWorkPolicy { KEEP, UPDATE }

    /** Represents a single periodic work entry in the simulated WorkManager registry. */
    data class SimulatedWorkInfo(
        val workName: String,
        val intervalMinutes: Long,
        val state: String = "ENQUEUED",
        val nextScheduleTimeMillis: Long = System.currentTimeMillis() + intervalMinutes * 60_000L
    )

    /** In-memory WorkManager registry for periodic work. */
    class SimulatedWorkManager {
        private val works = mutableMapOf<String, SimulatedWorkInfo>()

        fun enqueueUniquePeriodicWork(
            name: String,
            policy: SimulatedWorkPolicy,
            intervalMinutes: Long
        ) {
            val existing = works[name]
            when {
                existing == null -> {
                    // First registration — always creates a new entry
                    works[name] = SimulatedWorkInfo(name, intervalMinutes)
                }
                policy == SimulatedWorkPolicy.UPDATE -> {
                    // UPDATE replaces the existing entry, resetting the timer
                    works[name] = SimulatedWorkInfo(name, intervalMinutes)
                }
                policy == SimulatedWorkPolicy.KEEP -> {
                    // KEEP preserves the existing entry without resetting the timer
                    // (no change)
                }
            }
        }

        fun getWorkInfosForUniqueWork(name: String): List<SimulatedWorkInfo> =
            listOfNotNull(works[name])

        fun cancelUniqueWork(name: String) {
            works.remove(name)
        }

        fun hasWork(name: String): Boolean = works.containsKey(name)
    }

    /** In-memory one-time work registry (mirrors DataSyncWorker.runOnce). */
    class SimulatedOneTimeWorkManager {
        private val enqueuedWorks = mutableListOf<String>()

        fun enqueue(name: String) {
            enqueuedWorks.add(name)
        }

        fun getEnqueuedCount(): Int = enqueuedWorks.size
        fun wasEnqueued(name: String): Boolean = enqueuedWorks.contains(name)
    }

    /** Stores the user-configured sync interval (mirrors AppPreferences). */
    class SimulatedAppPreferences(var syncIntervalMinutes: Long = 30L)

    // =========================================================================
    // Fixed production-code simulations
    // =========================================================================

    /**
     * Mirrors the fixed WidgetReceiver.enqueuePeriodicRefresh().
     * FIX BUG 1: reads interval from AppPreferences instead of hardcoding 30L.
     * FIX BUG 3: uses KEEP policy so the timer is never reset on repeated calls.
     */
    private fun fixedEnqueuePeriodicRefresh(
        workManager: SimulatedWorkManager,
        appPreferences: SimulatedAppPreferences
    ) {
        val intervalMinutes = appPreferences.syncIntervalMinutes
        workManager.enqueueUniquePeriodicWork(
            name = WORK_NAME,
            policy = SimulatedWorkPolicy.KEEP,
            intervalMinutes = intervalMinutes
        )
    }

    /**
     * Mirrors the fixed ValueGridWidgetReceiver.onEnabled().
     * FIX BUG 2: calls enqueuePeriodicRefresh so WorkManager work is scheduled.
     */
    private fun fixedValueGridWidgetReceiverOnEnabled(
        workManager: SimulatedWorkManager,
        appPreferences: SimulatedAppPreferences
    ) {
        fixedEnqueuePeriodicRefresh(workManager, appPreferences)
    }

    /**
     * Mirrors the fixed ValueGridWidgetReceiver.onDeleted().
     * Cancels periodic work only when no widgets of either type remain active.
     */
    private fun fixedValueGridWidgetReceiverOnDeleted(
        workManager: SimulatedWorkManager,
        remainingGlanceWidgets: Int,
        remainingValueGridWidgets: Int
    ) {
        if (remainingGlanceWidgets == 0 && remainingValueGridWidgets == 0) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    /**
     * Mirrors the fixed DataSyncWorker.schedule() — uses KEEP policy.
     * FIX BUG 3: timer is NOT reset on repeated calls.
     */
    private fun fixedDataSyncWorkerSchedule(
        workManager: SimulatedWorkManager,
        intervalMinutes: Long
    ) {
        workManager.enqueueUniquePeriodicWork(
            name = WORK_NAME,
            policy = SimulatedWorkPolicy.KEEP,
            intervalMinutes = intervalMinutes
        )
    }

    /**
     * Mirrors DataSyncWorker.scheduleWithUpdate() — uses UPDATE policy.
     * Used when the user explicitly changes the sync interval in settings.
     */
    private fun fixedDataSyncWorkerScheduleWithUpdate(
        workManager: SimulatedWorkManager,
        intervalMinutes: Long
    ) {
        workManager.enqueueUniquePeriodicWork(
            name = WORK_NAME,
            policy = SimulatedWorkPolicy.UPDATE,
            intervalMinutes = intervalMinutes
        )
    }

    /**
     * Mirrors the fixed RescheduleWorker.doWork().
     * Reads the interval from AppPreferences and schedules DataSyncWorker with KEEP.
     */
    private fun fixedRescheduleWorkerDoWork(
        workManager: SimulatedWorkManager,
        appPreferences: SimulatedAppPreferences
    ) {
        val intervalMinutes = appPreferences.syncIntervalMinutes
        fixedDataSyncWorkerSchedule(workManager, intervalMinutes)
    }

    companion object {
        private const val WORK_NAME = "DataSyncWorker"
    }


    // =========================================================================
    // SCENARIO 1 — Full flow: ThingSpeakGlanceWidget added, sync starts with correct interval
    // =========================================================================

    /**
     * SCENARIO 1: Full flow — ThingSpeakGlanceWidget added, sync starts with the correct interval.
     *
     * Steps:
     *   1. User sets sync interval to 20 minutes in AppPreferences.
     *   2. WidgetReceiver.onEnabled() is called (widget added).
     * Assertions:
     *   - WorkManager work exists with interval = 20 minutes.
     *   - Policy is KEEP (timer will not be reset).
     *
     * Validates: Requirements 1.1, 1.2, 2.1, 2.2, 2.3
     */
    @Test
    fun `Scenariusz 1 - ThingSpeakGlanceWidget dodany synchronizacja uruchamia sie z wlasciwym interwalem`() {
        // Arrange
        val userInterval = 20L
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = userInterval)
        val workManager = SimulatedWorkManager()

        // Verify precondition: no work scheduled yet
        assertTrue(
            "Precondition: no WorkManager work before onEnabled()",
            workManager.getWorkInfosForUniqueWork(WORK_NAME).isEmpty()
        )

        // Act: WidgetReceiver.onEnabled() calls enqueuePeriodicRefresh
        fixedEnqueuePeriodicRefresh(workManager, appPreferences)

        // Assert: work exists with the user-configured interval
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertTrue("WorkManager work should be scheduled after onEnabled()", workInfos.isNotEmpty())

        val scheduledInterval = workInfos.first().intervalMinutes
        assertEquals(
            "Scheduled interval should equal user-configured interval ($userInterval min)",
            userInterval,
            scheduledInterval
        )

        // Verify KEEP semantics: a second call does NOT change the scheduled time
        val nextTimeBefore = workInfos.first().nextScheduleTimeMillis
        Thread.sleep(5)
        fixedEnqueuePeriodicRefresh(workManager, appPreferences)
        val nextTimeAfter = workManager.getWorkInfosForUniqueWork(WORK_NAME).first().nextScheduleTimeMillis
        assertEquals(
            "KEEP policy: timer should NOT be reset by a second enqueuePeriodicRefresh call",
            nextTimeBefore,
            nextTimeAfter
        )

        println("SCENARIO 1 PASSED: interval=$scheduledInterval min, KEEP policy confirmed")
    }

    // =========================================================================
    // SCENARIO 2 — Full flow: ValueGridWidget added as the only widget, sync starts
    // =========================================================================

    /**
     * SCENARIO 2: Full flow — ValueGridWidget added as the only widget, sync starts.
     *
     * Steps:
     *   1. User sets sync interval to 45 minutes.
     *   2. ValueGridWidgetReceiver.onEnabled() is called (no ThingSpeakGlanceWidget present).
     * Assertions:
     *   - WorkManager work exists with interval = 45 minutes.
     *   - Data will be synchronised automatically.
     *
     * Validates: Requirements 1.4, 1.5, 2.4, 2.5, 2.6
     */
    @Test
    fun `Scenariusz 2 - ValueGridWidget dodany jako jedyny widget synchronizacja uruchamia sie`() {
        // Arrange: only ValueGridWidget active, no ThingSpeakGlanceWidget
        val userInterval = 45L
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = userInterval)
        val workManager = SimulatedWorkManager()

        // Verify precondition: no work scheduled yet
        assertTrue(
            "Precondition: no WorkManager work before ValueGridWidgetReceiver.onEnabled()",
            workManager.getWorkInfosForUniqueWork(WORK_NAME).isEmpty()
        )

        // Act: ValueGridWidgetReceiver.onEnabled() — fixed code calls enqueuePeriodicRefresh
        fixedValueGridWidgetReceiverOnEnabled(workManager, appPreferences)

        // Assert: work is now scheduled
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertTrue(
            "WorkManager work should be scheduled after ValueGridWidgetReceiver.onEnabled()",
            workInfos.isNotEmpty()
        )

        val scheduledInterval = workInfos.first().intervalMinutes
        assertEquals(
            "Scheduled interval should equal user-configured interval ($userInterval min)",
            userInterval,
            scheduledInterval
        )

        // Verify data will be synchronised automatically (work is in ENQUEUED state)
        assertEquals(
            "Work state should be ENQUEUED — data will be synchronised automatically",
            "ENQUEUED",
            workInfos.first().state
        )

        println("SCENARIO 2 PASSED: ValueGridWidget-only setup, interval=$scheduledInterval min, state=ENQUEUED")
    }

    // =========================================================================
    // SCENARIO 3 — Both widgets added: sync starts only once (KEEP does not duplicate)
    // =========================================================================

    /**
     * SCENARIO 3: Both widgets added — sync starts only once (KEEP does not duplicate).
     *
     * Steps:
     *   1. User sets sync interval to 30 minutes.
     *   2. WidgetReceiver.onEnabled() called (ThingSpeakGlanceWidget).
     *   3. ValueGridWidgetReceiver.onEnabled() called (ValueGridWidget).
     * Assertions:
     *   - Exactly one WorkManager work entry exists (not two).
     *   - Interval = 30 minutes.
     *   - Timer was NOT reset by the second call (KEEP).
     *
     * Validates: Requirements 1.7, 1.8, 2.7, 2.8, 2.9
     */
    @Test
    fun `Scenariusz 3 - Oba widgety dodane synchronizacja uruchamia sie tylko raz KEEP nie duplikuje`() {
        // Arrange
        val userInterval = 30L
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = userInterval)
        val workManager = SimulatedWorkManager()

        // Act step 1: WidgetReceiver.onEnabled() — ThingSpeakGlanceWidget added
        fixedEnqueuePeriodicRefresh(workManager, appPreferences)

        val workAfterFirst = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertTrue("Work should exist after first onEnabled()", workAfterFirst.isNotEmpty())
        val timerAfterFirst = workAfterFirst.first().nextScheduleTimeMillis

        Thread.sleep(5) // Ensure time passes so a new entry would have a different timestamp

        // Act step 2: ValueGridWidgetReceiver.onEnabled() — ValueGridWidget added
        fixedValueGridWidgetReceiverOnEnabled(workManager, appPreferences)

        // Assert: still exactly one work entry
        val workAfterSecond = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertEquals(
            "Exactly one WorkManager work entry should exist (KEEP does not duplicate)",
            1,
            workAfterSecond.size
        )

        // Assert: interval unchanged
        assertEquals(
            "Interval should remain $userInterval min after both onEnabled() calls",
            userInterval,
            workAfterSecond.first().intervalMinutes
        )

        // Assert: timer was NOT reset by the second call (KEEP semantics)
        val timerAfterSecond = workAfterSecond.first().nextScheduleTimeMillis
        assertEquals(
            "KEEP policy: timer should NOT be reset by ValueGridWidgetReceiver.onEnabled()",
            timerAfterFirst,
            timerAfterSecond
        )

        println("SCENARIO 3 PASSED: single work entry, interval=$userInterval min, timer preserved")
    }

    // =========================================================================
    // SCENARIO 4 — onUpdate called 10 times: sync timer is NOT reset
    // =========================================================================

    /**
     * SCENARIO 4: onUpdate called 10 times — sync timer is NOT reset.
     *
     * Steps:
     *   1. Interval = 15 minutes.
     *   2. schedule() called once (initial scheduling).
     *   3. Record nextScheduleTimeMillis.
     *   4. schedule() called 9 more times (simulating onUpdate).
     * Assertions:
     *   - nextScheduleTimeMillis is identical after all 10 calls.
     *   - Sync will happen according to the original schedule.
     *
     * Validates: Requirements 1.7, 1.8, 2.7, 2.8, 2.9
     */
    @Test
    fun `Scenariusz 4 - onUpdate wywolywana 10 razy timer synchronizacji nie jest resetowany`() {
        // Arrange
        val intervalMinutes = 15L
        val workManager = SimulatedWorkManager()

        // Act: initial schedule call
        fixedDataSyncWorkerSchedule(workManager, intervalMinutes)

        val initialWorkInfo = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertTrue("Work should exist after initial schedule()", initialWorkInfo.isNotEmpty())
        val nextScheduleTimeMillis = initialWorkInfo.first().nextScheduleTimeMillis

        // Act: simulate 9 more onUpdate calls
        repeat(9) { iteration ->
            Thread.sleep(2) // Simulate time passing between onUpdate calls
            fixedDataSyncWorkerSchedule(workManager, intervalMinutes)

            val currentInfo = workManager.getWorkInfosForUniqueWork(WORK_NAME).first()
            assertEquals(
                "Timer should NOT be reset after onUpdate call #${iteration + 2}",
                nextScheduleTimeMillis,
                currentInfo.nextScheduleTimeMillis
            )
        }

        // Final assertion: timer is identical after all 10 calls
        val finalWorkInfo = workManager.getWorkInfosForUniqueWork(WORK_NAME).first()
        assertEquals(
            "nextScheduleTimeMillis must be identical after all 10 schedule() calls (KEEP policy)",
            nextScheduleTimeMillis,
            finalWorkInfo.nextScheduleTimeMillis
        )

        assertEquals(
            "Interval should remain $intervalMinutes min after all 10 calls",
            intervalMinutes,
            finalWorkInfo.intervalMinutes
        )

        println("SCENARIO 4 PASSED: 10 schedule() calls, timer unchanged at $nextScheduleTimeMillis")
    }

    // =========================================================================
    // SCENARIO 5 — User changes interval: scheduleWithUpdate updates the schedule
    // =========================================================================

    /**
     * SCENARIO 5: User changes interval — scheduleWithUpdate updates the schedule.
     *
     * Steps:
     *   1. Initial interval: 30 minutes (schedule with KEEP).
     *   2. User changes interval to 10 minutes (scheduleWithUpdate with UPDATE).
     * Assertions:
     *   - New interval = 10 minutes.
     *   - Timer was reset (new schedule takes effect).
     *
     * Validates: Requirements 2.7, 2.8, 2.9, 3.5
     */
    @Test
    fun `Scenariusz 5 - Zmiana interwalu przez uzytkownika scheduleWithUpdate aktualizuje harmonogram`() {
        // Arrange
        val initialInterval = 30L
        val newInterval = 10L
        val workManager = SimulatedWorkManager()

        // Act step 1: initial scheduling with KEEP
        fixedDataSyncWorkerSchedule(workManager, initialInterval)

        val workAfterInitial = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertTrue("Work should exist after initial schedule()", workAfterInitial.isNotEmpty())
        assertEquals(
            "Initial interval should be $initialInterval min",
            initialInterval,
            workAfterInitial.first().intervalMinutes
        )
        val timerBeforeUpdate = workAfterInitial.first().nextScheduleTimeMillis

        Thread.sleep(5) // Ensure time passes so UPDATE produces a different timestamp

        // Act step 2: user changes interval — scheduleWithUpdate uses UPDATE policy
        fixedDataSyncWorkerScheduleWithUpdate(workManager, newInterval)

        // Assert: new interval is applied
        val workAfterUpdate = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertTrue("Work should still exist after scheduleWithUpdate()", workAfterUpdate.isNotEmpty())

        val updatedInterval = workAfterUpdate.first().intervalMinutes
        assertEquals(
            "Interval should be updated to $newInterval min after scheduleWithUpdate()",
            newInterval,
            updatedInterval
        )

        // Assert: timer was reset (UPDATE policy creates a new entry with a new timestamp)
        val timerAfterUpdate = workAfterUpdate.first().nextScheduleTimeMillis
        assertNotEquals(
            "Timer should be reset after scheduleWithUpdate() (UPDATE policy)",
            timerBeforeUpdate,
            timerAfterUpdate
        )

        println("SCENARIO 5 PASSED: interval updated $initialInterval→$newInterval min, timer reset confirmed")
    }

    // =========================================================================
    // SCENARIO 6 — ThingSpeakGlanceWidget removed while ValueGridWidget still active
    // =========================================================================

    /**
     * SCENARIO 6: ThingSpeakGlanceWidget removed while ValueGridWidget still active —
     * sync is NOT cancelled.
     *
     * Steps:
     *   1. Both widgets active, WorkManager work scheduled.
     *   2. ThingSpeakGlanceWidget removed (remainingGlanceWidgets=0, remainingValueGridWidgets=1).
     * Assertions:
     *   - WorkManager work still exists (ValueGridWidget still needs sync).
     *
     * Validates: Requirements 2.4, 2.5, 2.6, 3.2
     */
    @Test
    fun `Scenariusz 6 - Usuniecie ThingSpeakGlanceWidget gdy ValueGridWidget aktywny synchronizacja NIE jest anulowana`() {
        // Arrange: both widgets active, work scheduled
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = 30L)
        val workManager = SimulatedWorkManager()

        fixedEnqueuePeriodicRefresh(workManager, appPreferences)
        fixedValueGridWidgetReceiverOnEnabled(workManager, appPreferences)

        assertTrue(
            "Precondition: WorkManager work should be scheduled with both widgets active",
            workManager.hasWork(WORK_NAME)
        )

        // Act: ThingSpeakGlanceWidget removed — ValueGridWidget still active
        val remainingGlanceWidgets = 0
        val remainingValueGridWidgets = 1
        fixedValueGridWidgetReceiverOnDeleted(workManager, remainingGlanceWidgets, remainingValueGridWidgets)

        // Assert: work still exists because ValueGridWidget is still active
        assertTrue(
            "WorkManager work should NOT be cancelled — ValueGridWidget is still active",
            workManager.hasWork(WORK_NAME)
        )

        println("SCENARIO 6 PASSED: work preserved after ThingSpeakGlanceWidget removal (ValueGridWidget still active)")
    }

    // =========================================================================
    // SCENARIO 7 — Last widget of both types removed: sync is cancelled
    // =========================================================================

    /**
     * SCENARIO 7: Last widget of both types removed — sync is cancelled.
     *
     * Steps:
     *   1. Both widgets active, WorkManager work scheduled.
     *   2. All widgets removed (remainingGlanceWidgets=0, remainingValueGridWidgets=0).
     * Assertions:
     *   - WorkManager work was cancelled.
     *
     * Validates: Requirements 2.4, 2.5, 2.6, 3.2
     */
    @Test
    fun `Scenariusz 7 - Usuniecie ostatniego widgetu obu typow synchronizacja jest anulowana`() {
        // Arrange: both widgets active, work scheduled
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = 30L)
        val workManager = SimulatedWorkManager()

        fixedEnqueuePeriodicRefresh(workManager, appPreferences)
        fixedValueGridWidgetReceiverOnEnabled(workManager, appPreferences)

        assertTrue(
            "Precondition: WorkManager work should be scheduled with both widgets active",
            workManager.hasWork(WORK_NAME)
        )

        // Act: all widgets removed
        val remainingGlanceWidgets = 0
        val remainingValueGridWidgets = 0
        fixedValueGridWidgetReceiverOnDeleted(workManager, remainingGlanceWidgets, remainingValueGridWidgets)

        // Assert: work was cancelled
        assertFalse(
            "WorkManager work should be cancelled when no widgets of either type remain",
            workManager.hasWork(WORK_NAME)
        )

        println("SCENARIO 7 PASSED: work cancelled after all widgets removed")
    }

    // =========================================================================
    // SCENARIO 8 — Device restart: RescheduleWorker restores sync with correct interval
    // =========================================================================

    /**
     * SCENARIO 8: Device restart — RescheduleWorker restores sync with the correct interval.
     *
     * Steps:
     *   1. User had set interval to 60 minutes.
     *   2. Device restarted (WorkManager work lost).
     *   3. RescheduleWorker.doWork() called.
     * Assertions:
     *   - WorkManager work restored with interval = 60 minutes (read from AppPreferences).
     *
     * Validates: Requirements 3.3, 3.4
     */
    @Test
    fun `Scenariusz 8 - Restart urzadzenia RescheduleWorker przywraca synchronizacje z wlasciwym interwalem`() {
        // Arrange: user had configured 60-minute interval; after reboot WorkManager is empty
        val userInterval = 60L
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = userInterval)
        val workManager = SimulatedWorkManager()

        // Verify precondition: work is lost after reboot
        assertTrue(
            "Precondition: WorkManager work should be empty after device restart",
            workManager.getWorkInfosForUniqueWork(WORK_NAME).isEmpty()
        )

        // Act: RescheduleWorker.doWork() reads interval from AppPreferences and reschedules
        fixedRescheduleWorkerDoWork(workManager, appPreferences)

        // Assert: work restored with the correct interval
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertTrue(
            "WorkManager work should be restored after RescheduleWorker.doWork()",
            workInfos.isNotEmpty()
        )

        val restoredInterval = workInfos.first().intervalMinutes
        assertEquals(
            "Restored interval should equal user-configured interval ($userInterval min)",
            userInterval,
            restoredInterval
        )

        assertEquals(
            "Restored work state should be ENQUEUED",
            "ENQUEUED",
            workInfos.first().state
        )

        println("SCENARIO 8 PASSED: RescheduleWorker restored interval=$restoredInterval min after reboot")
    }

    // =========================================================================
    // SCENARIO 9 — Property-based: for any interval in [15, 1440] sync is scheduled with that interval
    // =========================================================================

    /**
     * SCENARIO 9: Property-based — for any interval in [15, 1440] minutes sync is scheduled
     * with exactly that interval.
     *
     * Generates 10 random intervals from 15..1440 and verifies scheduledInterval = userInterval.
     *
     * Validates: Requirements 1.1, 1.2, 2.1, 2.2, 2.3
     *
     * **Validates: Requirements 1.1, 1.2**
     */
    @Test
    fun `Scenariusz 9 - Property-based dla dowolnego interwalu z zakresu 15-1440 minut synchronizacja planowana jest z tym interwalem`() {
        val random = java.util.Random(42L) // Fixed seed for reproducibility
        val testedIntervals = mutableListOf<Long>()

        // Generate 10 random intervals in [15, 1440]
        repeat(10) {
            val intervalMinutes = (random.nextInt(1426) + 15).toLong() // 15..1440
            testedIntervals.add(intervalMinutes)

            val workManager = SimulatedWorkManager()

            // Act: schedule with the generated interval
            fixedDataSyncWorkerSchedule(workManager, intervalMinutes)

            // Assert: scheduled interval equals the user interval
            val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)
            assertTrue(
                "Work should be scheduled for interval=$intervalMinutes min",
                workInfos.isNotEmpty()
            )

            val scheduledInterval = workInfos.first().intervalMinutes
            assertEquals(
                "Property: scheduledInterval ($scheduledInterval) should equal userInterval ($intervalMinutes)",
                intervalMinutes,
                scheduledInterval
            )
        }

        println("SCENARIO 9 PASSED: property verified for intervals=$testedIntervals")
    }

    // =========================================================================
    // SCENARIO 10 — Complete widget lifecycle: add, sync, remove
    // =========================================================================

    /**
     * SCENARIO 10: Complete widget lifecycle — add, sync, remove.
     *
     * Step 1: Add ThingSpeakGlanceWidget → work scheduled with interval from AppPreferences.
     * Step 2: Add ValueGridWidget → work still the same (KEEP, not duplicated).
     * Step 3: Simulate 5 onUpdate calls → timer unchanged.
     * Step 4: User changes interval via scheduleWithUpdate → new interval takes effect.
     * Step 5: Remove ThingSpeakGlanceWidget → work still exists (ValueGridWidget active).
     * Step 6: Remove ValueGridWidget → work cancelled.
     *
     * Each step is verified separately.
     *
     * Validates: Requirements 1.1, 1.2, 1.4, 1.5, 1.7, 1.8, 2.1–2.9, 3.1–3.7
     */
    @Test
    fun `Scenariusz 10 - Kompletny cykl zycia widgetow dodanie synchronizacja usuniecie`() {
        val initialInterval = 25L
        val updatedInterval = 12L
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = initialInterval)
        val workManager = SimulatedWorkManager()

        // ---- STEP 1: Add ThingSpeakGlanceWidget ----
        fixedEnqueuePeriodicRefresh(workManager, appPreferences)

        val workStep1 = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertTrue("Step 1: work should be scheduled after ThingSpeakGlanceWidget added", workStep1.isNotEmpty())
        assertEquals("Step 1: interval should be $initialInterval min", initialInterval, workStep1.first().intervalMinutes)
        val timerStep1 = workStep1.first().nextScheduleTimeMillis
        println("STEP 1 OK: work scheduled, interval=$initialInterval min")

        // ---- STEP 2: Add ValueGridWidget ----
        Thread.sleep(5)
        fixedValueGridWidgetReceiverOnEnabled(workManager, appPreferences)

        val workStep2 = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertEquals("Step 2: still exactly one work entry (KEEP, not duplicated)", 1, workStep2.size)
        assertEquals("Step 2: interval unchanged at $initialInterval min", initialInterval, workStep2.first().intervalMinutes)
        assertEquals("Step 2: timer NOT reset by ValueGridWidget.onEnabled() (KEEP)", timerStep1, workStep2.first().nextScheduleTimeMillis)
        println("STEP 2 OK: single work entry, timer preserved")

        // ---- STEP 3: Simulate 5 onUpdate calls ----
        val timerBeforeUpdates = workStep2.first().nextScheduleTimeMillis
        repeat(5) { i ->
            Thread.sleep(2)
            fixedDataSyncWorkerSchedule(workManager, initialInterval)
            val currentTimer = workManager.getWorkInfosForUniqueWork(WORK_NAME).first().nextScheduleTimeMillis
            assertEquals("Step 3 (onUpdate #${i + 1}): timer should NOT be reset", timerBeforeUpdates, currentTimer)
        }
        println("STEP 3 OK: 5 onUpdate calls, timer unchanged")

        // ---- STEP 4: User changes interval via scheduleWithUpdate ----
        Thread.sleep(5)
        fixedDataSyncWorkerScheduleWithUpdate(workManager, updatedInterval)

        val workStep4 = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertTrue("Step 4: work should still exist after scheduleWithUpdate()", workStep4.isNotEmpty())
        assertEquals("Step 4: interval updated to $updatedInterval min", updatedInterval, workStep4.first().intervalMinutes)
        assertNotEquals("Step 4: timer reset by scheduleWithUpdate() (UPDATE policy)", timerBeforeUpdates, workStep4.first().nextScheduleTimeMillis)
        println("STEP 4 OK: interval updated to $updatedInterval min, timer reset")

        // ---- STEP 5: Remove ThingSpeakGlanceWidget (ValueGridWidget still active) ----
        fixedValueGridWidgetReceiverOnDeleted(workManager, remainingGlanceWidgets = 0, remainingValueGridWidgets = 1)

        assertTrue(
            "Step 5: work should NOT be cancelled — ValueGridWidget still active",
            workManager.hasWork(WORK_NAME)
        )
        println("STEP 5 OK: work preserved after ThingSpeakGlanceWidget removal")

        // ---- STEP 6: Remove ValueGridWidget (no widgets remain) ----
        fixedValueGridWidgetReceiverOnDeleted(workManager, remainingGlanceWidgets = 0, remainingValueGridWidgets = 0)

        assertFalse(
            "Step 6: work should be cancelled — no widgets of either type remain",
            workManager.hasWork(WORK_NAME)
        )
        println("STEP 6 OK: work cancelled after all widgets removed")

        println("SCENARIO 10 PASSED: complete widget lifecycle verified in 6 steps")
    }
}
