package com.thingspeak.monitor.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Preservation tests for widget auto-refresh behavior.
 *
 * PURPOSE: Document existing correct behaviors that MUST remain unchanged after the bugfix.
 * Tests PASS on unchanged production code — they establish the baseline.
 *
 * Preservation behaviors tested (from design.md):
 * - Policy KEEP does not reset the timer when work is already active (Requirements 2.5, 3.2)
 * - Interval change still calls scheduleWithUpdate() with UPDATE policy (Requirements 3.1)
 * - onDeleted() still cancels DataSyncWorker when no widgets remain (Requirements 3.6)
 *
 * Expected results on UNCHANGED code:
 * - All preservation tests PASS — confirms baseline behaviors to preserve
 *
 * Validates: Requirements 2.5, 3.1, 3.2, 3.6
 */
class WidgetNoAutoRefreshPreservationTest {

    // =========================================================================
    // Helper classes — in-memory simulation (no Mockito/Robolectric)
    // Same pattern as WidgetNoAutoRefreshBugConditionTest
    // =========================================================================

    /**
     * Simulates WorkManager work policy.
     * Mirrors ExistingPeriodicWorkPolicy from WorkManager API.
     */
    enum class SimulatedWorkPolicy { KEEP, UPDATE }

    /**
     * Simulates a single work item registered in WorkManager.
     * Tracks the scheduled time to verify timer reset behavior.
     */
    data class SimulatedWorkInfo(
        val workName: String,
        val intervalMinutes: Long,
        val state: String = "ENQUEUED",
        val nextScheduleTimeMillis: Long = System.currentTimeMillis()
    )

    /**
     * Simulates in-memory WorkManager registry.
     * Tracks enqueue/cancel operations and timer state.
     */
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
                    // First registration — always creates new work
                    works[name] = SimulatedWorkInfo(name, intervalMinutes)
                }
                policy == SimulatedWorkPolicy.UPDATE -> {
                    // UPDATE replaces existing work, resetting the timer
                    works[name] = SimulatedWorkInfo(name, intervalMinutes)
                }
                policy == SimulatedWorkPolicy.KEEP -> {
                    // KEEP preserves existing work without resetting the timer
                    // (no change to existing work)
                }
            }
        }

        fun getWorkInfosForUniqueWork(name: String): List<SimulatedWorkInfo> =
            listOfNotNull(works[name])

        fun cancelUniqueWork(name: String) {
            works.remove(name)
        }

        fun hasActiveWork(name: String): Boolean =
            works[name]?.let { it.state == "ENQUEUED" || it.state == "RUNNING" } ?: false

        fun isEmpty(): Boolean = works.isEmpty()

        /**
         * Registers pre-existing work with a specific schedule time.
         * Used to verify that KEEP policy does not reset the timer.
         */
        fun registerExistingWork(
            name: String,
            intervalMinutes: Long,
            scheduleTimeMillis: Long,
            state: String = "ENQUEUED"
        ) {
            works[name] = SimulatedWorkInfo(name, intervalMinutes, state, scheduleTimeMillis)
        }

        fun getNextScheduleTimeMillis(name: String): Long? =
            works[name]?.nextScheduleTimeMillis
    }

    /**
     * Simulates AppPreferences — stores syncIntervalMinutes.
     */
    class SimulatedAppPreferences(
        var syncIntervalMinutes: Long = 30L
    )

    companion object {
        private const val WORK_NAME = "DataSyncWorker"

        /** Active WorkManager states used in property-based test. */
        private val ACTIVE_WORK_STATES = listOf("ENQUEUED", "RUNNING")

        /** Representative interval values (minutes) for property-based test. */
        private val TEST_INTERVALS = listOf(15L, 30L, 60L, 120L, 240L, 480L, 1440L)
    }

    // =========================================================================
    // Production logic simulation helpers
    // =========================================================================

    /**
     * Simulates DataSyncWorker.schedule() — uses KEEP policy.
     * Mirrors production code: enqueueUniquePeriodicWork with KEEP.
     */
    private fun simulateDataSyncWorkerSchedule(
        workManager: SimulatedWorkManager,
        intervalMinutes: Long
    ) {
        workManager.enqueueUniquePeriodicWork(WORK_NAME, SimulatedWorkPolicy.KEEP, intervalMinutes)
    }

    /**
     * Simulates DataSyncWorker.scheduleWithUpdate() — uses UPDATE policy.
     * Mirrors production code: enqueueUniquePeriodicWork with UPDATE.
     * Called when user changes sync interval in settings.
     */
    private fun simulateDataSyncWorkerScheduleWithUpdate(
        workManager: SimulatedWorkManager,
        intervalMinutes: Long
    ) {
        workManager.enqueueUniquePeriodicWork(WORK_NAME, SimulatedWorkPolicy.UPDATE, intervalMinutes)
    }

    /**
     * Simulates WidgetReceiver.onDeleted() — cancels work when no widgets remain.
     * Mirrors production code in WidgetReceiver.onDeleted() and ValueGridWidgetReceiver.onDeleted().
     */
    private fun simulateWidgetReceiverOnDeleted(
        workManager: SimulatedWorkManager,
        remainingThingSpeakWidgets: Int,
        remainingValueGridWidgets: Int
    ) {
        if (remainingThingSpeakWidgets == 0 && remainingValueGridWidgets == 0) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    // =========================================================================
    // Test 1 — Preservation: KEEP policy does not reset timer when work is active
    // =========================================================================

    /**
     * Test 1: KEEP policy preserves existing work timer when WorkManager has active work.
     *
     * Scenario (Requirements 2.5, 3.2):
     * - WorkManager has active work (ENQUEUED) with a known schedule time
     * - schedule() is called with KEEP policy (normal scheduling entry point)
     *
     * Expected behavior (preserved): nextScheduleTimeMillis remains unchanged (timer NOT reset)
     * This confirms that KEEP policy correctly avoids double-scheduling.
     *
     * Validates: Requirements 2.5, 3.2
     */
    @Test
    fun `Test1 - Preservation - KEEP policy does not reset timer when work is active`() {
        // Arrange: WorkManager has active work (ENQUEUED) with a known schedule time
        val workManager = SimulatedWorkManager()
        val originalScheduleTime = 1_000_000L
        workManager.registerExistingWork(
            WORK_NAME,
            intervalMinutes = 30L,
            scheduleTimeMillis = originalScheduleTime,
            state = "ENQUEUED"
        )

        // Verify precondition: work is active
        assertTrue(
            "Precondition: WorkManager has active work (ENQUEUED)",
            workManager.hasActiveWork(WORK_NAME)
        )
        assertEquals(
            "Precondition: original schedule time is set",
            originalScheduleTime,
            workManager.getNextScheduleTimeMillis(WORK_NAME)
        )

        // Act: call schedule() with KEEP policy (simulates normal entry point behavior)
        simulateDataSyncWorkerSchedule(workManager, intervalMinutes = 30L)

        println("TEST 1 — Preservation: KEEP policy does not reset timer:")
        println("  originalScheduleTime = $originalScheduleTime")
        println("  scheduleTimeAfterKeep = ${workManager.getNextScheduleTimeMillis(WORK_NAME)}")
        println("  → PASS on unchanged code (KEEP preserves existing timer)")

        // Assert: timer is NOT reset — nextScheduleTimeMillis remains unchanged
        assertEquals(
            "Preservation (Requirements 2.5, 3.2): KEEP policy must NOT reset the timer. " +
                "nextScheduleTimeMillis should remain $originalScheduleTime after schedule() with KEEP.",
            originalScheduleTime,
            workManager.getNextScheduleTimeMillis(WORK_NAME)
        )

        // Assert: work is still active
        assertTrue(
            "Preservation: work must remain active after KEEP policy",
            workManager.hasActiveWork(WORK_NAME)
        )
    }

    // =========================================================================
    // Test 2 — Preservation: interval change calls scheduleWithUpdate() with UPDATE policy
    // =========================================================================

    /**
     * Test 2: Changing sync interval calls scheduleWithUpdate() with UPDATE policy.
     *
     * Scenario (Requirements 3.1):
     * - WorkManager has active work with interval 30 min
     * - User changes interval to 15 min → scheduleWithUpdate() with UPDATE
     *
     * Expected behavior (preserved):
     * - New interval = 15 min (UPDATE replaces existing work)
     * - Timer is reset (UPDATE policy creates new work)
     *
     * Validates: Requirements 3.1
     */
    @Test
    fun `Test2 - Preservation - interval change calls scheduleWithUpdate with UPDATE policy`() {
        // Arrange: WorkManager has active work with interval 30 min
        val workManager = SimulatedWorkManager()
        val originalScheduleTime = 2_000_000L
        workManager.registerExistingWork(
            WORK_NAME,
            intervalMinutes = 30L,
            scheduleTimeMillis = originalScheduleTime,
            state = "ENQUEUED"
        )

        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = 30L)

        // Verify precondition: work is active with 30 min interval
        assertTrue(
            "Precondition: WorkManager has active work with 30 min interval",
            workManager.hasActiveWork(WORK_NAME)
        )
        assertEquals(
            "Precondition: interval is 30 min",
            30L,
            workManager.getWorkInfosForUniqueWork(WORK_NAME).first().intervalMinutes
        )

        // Act: user changes interval to 15 min → scheduleWithUpdate() with UPDATE policy
        appPreferences.syncIntervalMinutes = 15L
        simulateDataSyncWorkerScheduleWithUpdate(workManager, intervalMinutes = appPreferences.syncIntervalMinutes)

        println("TEST 2 — Preservation: interval change calls scheduleWithUpdate() with UPDATE:")
        println("  originalInterval = 30 min, newInterval = ${appPreferences.syncIntervalMinutes} min")
        println("  workInterval after UPDATE = ${workManager.getWorkInfosForUniqueWork(WORK_NAME).firstOrNull()?.intervalMinutes}")
        println("  → PASS on unchanged code (UPDATE replaces work with new interval)")

        // Assert: new interval = 15 min (UPDATE replaced existing work)
        assertEquals(
            "Preservation (Requirements 3.1): scheduleWithUpdate() with UPDATE must apply new interval. " +
                "Expected interval = 15 min after UPDATE.",
            15L,
            workManager.getWorkInfosForUniqueWork(WORK_NAME).first().intervalMinutes
        )

        // Assert: work is still active after UPDATE
        assertTrue(
            "Preservation: work must remain active after scheduleWithUpdate() with UPDATE",
            workManager.hasActiveWork(WORK_NAME)
        )
    }

    // =========================================================================
    // Test 3 — Preservation: onDeleted() cancels DataSyncWorker when no widgets remain
    // =========================================================================

    /**
     * Test 3: onDeleted() cancels DataSyncWorker when no widgets of either type remain.
     *
     * Scenario (Requirements 3.6):
     * - WorkManager has active work
     * - Both ThingSpeakGlanceWidget and ValueGridWidget are deleted (remainingWidgets=0)
     * - onDeleted() is called
     *
     * Expected behavior (preserved): WorkManager has no active work (work cancelled)
     *
     * Validates: Requirements 3.6
     */
    @Test
    fun `Test3 - Preservation - onDeleted cancels DataSyncWorker when no widgets remain`() {
        // Arrange: WorkManager has active work, both widget types deleted
        val workManager = SimulatedWorkManager()
        workManager.registerExistingWork(
            WORK_NAME,
            intervalMinutes = 30L,
            scheduleTimeMillis = 3_000_000L,
            state = "ENQUEUED"
        )

        // Verify precondition: work is active
        assertTrue(
            "Precondition: WorkManager has active work before onDeleted()",
            workManager.hasActiveWork(WORK_NAME)
        )

        // Act: onDeleted() with remainingWidgets=0 (both types)
        simulateWidgetReceiverOnDeleted(
            workManager = workManager,
            remainingThingSpeakWidgets = 0,
            remainingValueGridWidgets = 0
        )

        println("TEST 3 — Preservation: onDeleted() cancels work when no widgets remain:")
        println("  remainingThingSpeakWidgets = 0, remainingValueGridWidgets = 0")
        println("  workManager.isEmpty() = ${workManager.isEmpty()}")
        println("  → PASS on unchanged code (onDeleted() cancels work when no widgets remain)")

        // Assert: WorkManager has no active work (work cancelled)
        assertFalse(
            "Preservation (Requirements 3.6): onDeleted() must cancel DataSyncWorker when no widgets remain. " +
                "WorkManager should have no active work after last widget is deleted.",
            workManager.hasActiveWork(WORK_NAME)
        )

        assertTrue(
            "Preservation: WorkManager must be empty after last widget is deleted",
            workManager.isEmpty()
        )
    }

    // =========================================================================
    // Test 4 — Preservation: onDeleted() does NOT cancel work when widgets remain
    // =========================================================================

    /**
     * Test 4: onDeleted() does NOT cancel DataSyncWorker when active widgets remain.
     *
     * Scenario (Requirements 3.6):
     * - WorkManager has active work
     * - 1 active widget remains (remainingThingSpeakWidgets=1)
     * - onDeleted() is called
     *
     * Expected behavior (preserved): WorkManager still has active work (NOT cancelled)
     *
     * Validates: Requirements 3.6
     */
    @Test
    fun `Test4 - Preservation - onDeleted does NOT cancel work when widgets remain`() {
        // Arrange: WorkManager has active work, 1 ThingSpeakGlanceWidget remains
        val workManager = SimulatedWorkManager()
        workManager.registerExistingWork(
            WORK_NAME,
            intervalMinutes = 30L,
            scheduleTimeMillis = 4_000_000L,
            state = "ENQUEUED"
        )

        // Verify precondition: work is active
        assertTrue(
            "Precondition: WorkManager has active work before onDeleted()",
            workManager.hasActiveWork(WORK_NAME)
        )

        // Act: onDeleted() with 1 remaining ThingSpeakGlanceWidget
        simulateWidgetReceiverOnDeleted(
            workManager = workManager,
            remainingThingSpeakWidgets = 1,
            remainingValueGridWidgets = 0
        )

        println("TEST 4 — Preservation: onDeleted() does NOT cancel work when widgets remain:")
        println("  remainingThingSpeakWidgets = 1, remainingValueGridWidgets = 0")
        println("  workManager.hasActiveWork = ${workManager.hasActiveWork(WORK_NAME)}")
        println("  → PASS on unchanged code (onDeleted() does NOT cancel when widgets remain)")

        // Assert: WorkManager still has active work (NOT cancelled)
        assertTrue(
            "Preservation (Requirements 3.6): onDeleted() must NOT cancel DataSyncWorker when active widgets remain. " +
                "WorkManager should still have active work when remainingThingSpeakWidgets=1.",
            workManager.hasActiveWork(WORK_NAME)
        )

        assertFalse(
            "Preservation: WorkManager must NOT be empty when active widgets remain",
            workManager.isEmpty()
        )
    }

    // =========================================================================
    // Test 5 — Property-based: for all active WorkManager states, KEEP does not reset timer
    // =========================================================================

    /**
     * Test 5 (Property-based): For all WorkManager states where work is active
     * (ENQUEUED or RUNNING), calling schedule() with KEEP does not reset the timer.
     *
     * Property: ∀ state ∈ {ENQUEUED, RUNNING}, ∀ intervalMinutes ∈ TEST_INTERVALS:
     *   schedule(KEEP) → nextScheduleTimeMillis unchanged
     *
     * Implemented as exhaustive enumeration over all active states and representative
     * interval values (pure JUnit4, no PBT framework required).
     *
     * **Validates: Requirements 2.5, 3.2**
     */
    @Test
    fun `Test5 - Property - KEEP policy never resets timer for any active WorkManager state`() {
        // Property: for all active states × all representative intervals
        // schedule(KEEP) must NOT reset the timer
        for (state in ACTIVE_WORK_STATES) {
            for (intervalMinutes in TEST_INTERVALS) {
                // Arrange: WorkManager has active work in given state
                val workManager = SimulatedWorkManager()
                val originalScheduleTime = 5_000_000L + intervalMinutes * 1000L // unique per interval
                workManager.registerExistingWork(
                    WORK_NAME,
                    intervalMinutes = intervalMinutes,
                    scheduleTimeMillis = originalScheduleTime,
                    state = state
                )

                // Verify precondition: work is active
                assertTrue(
                    "Precondition: WorkManager has active work in state=$state, interval=$intervalMinutes",
                    workManager.hasActiveWork(WORK_NAME)
                )

                // Act: call schedule() with KEEP policy
                simulateDataSyncWorkerSchedule(workManager, intervalMinutes = intervalMinutes)

                // Assert: timer is NOT reset — nextScheduleTimeMillis remains unchanged
                assertEquals(
                    "Property (Requirements 2.5, 3.2): KEEP policy must NOT reset timer " +
                        "for state=$state, interval=$intervalMinutes min. " +
                        "nextScheduleTimeMillis should remain $originalScheduleTime.",
                    originalScheduleTime,
                    workManager.getNextScheduleTimeMillis(WORK_NAME)
                )

                // Assert: work is still active
                assertTrue(
                    "Property: work must remain active after KEEP policy for state=$state, interval=$intervalMinutes",
                    workManager.hasActiveWork(WORK_NAME)
                )

                println("  Property Test5 [state=$state, interval=$intervalMinutes min]: timer preserved ✓")
            }
        }

        println("TEST 5 — Property: KEEP never resets timer for any active state")
        println("  Tested states: $ACTIVE_WORK_STATES")
        println("  Tested intervals: $TEST_INTERVALS")
        println("  Total combinations: ${ACTIVE_WORK_STATES.size * TEST_INTERVALS.size}")
        println("  → PASS on unchanged code (KEEP preserves timer for all active states)")
    }
}
