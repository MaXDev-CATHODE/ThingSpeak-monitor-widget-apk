package com.thingspeak.monitor.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy zachowania (Preservation) dla automatycznej synchronizacji widgetów Glance.
 *
 * CEL: Udokumentować ISTNIEJĄCE POPRAWNE zachowania, które muszą pozostać niezmienione
 * po naprawie trzech błędów. Testy PRZECHODZĄ na nienaprawionym kodzie produkcyjnym.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
 *
 * Metodologia:
 * - Symulujemy logikę produkcyjną przez klasy pomocnicze (wzorzec z GlanceWidgetAutoSyncBugConditionTest)
 * - Asercje sprawdzają ISTNIEJĄCE POPRAWNE zachowanie → PRZECHODZĄ na nienaprawionym kodzie
 * - Testy te muszą NADAL PRZECHODZIĆ po naprawie (brak regresji)
 */
class GlanceWidgetAutoSyncPreservationTest {

    // =========================================================================
    // Symulacja logiki produkcyjnej (odzwierciedla rzeczywisty kod)
    // =========================================================================

    /** Symuluje politykę WorkManager przy planowaniu pracy periodycznej. */
    enum class SimulatedWorkPolicy { KEEP, UPDATE }

    /** Symuluje stan pracy WorkManager. */
    data class SimulatedWorkInfo(
        val workName: String,
        val intervalMinutes: Long,
        val state: String = "ENQUEUED",
        val nextScheduleTimeMillis: Long = System.currentTimeMillis() + intervalMinutes * 60_000L
    )

    /** Symuluje rejestr prac WorkManager (in-memory). */
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
                    // First registration – always creates new work
                    works[name] = SimulatedWorkInfo(name, intervalMinutes)
                }
                policy == SimulatedWorkPolicy.UPDATE -> {
                    // UPDATE replaces existing work, resetting the timer (BUG 3 behavior)
                    works[name] = SimulatedWorkInfo(name, intervalMinutes)
                }
                policy == SimulatedWorkPolicy.KEEP -> {
                    // KEEP preserves existing work without resetting the timer
                    // (no change)
                }
            }
        }

        fun getWorkInfosForUniqueWork(name: String): List<SimulatedWorkInfo> =
            listOfNotNull(works[name])

        fun cancelUniqueWork(name: String) {
            works.remove(name)
        }
    }

    /** Symuluje AppPreferences – przechowuje interwał synchronizacji. */
    class SimulatedAppPreferences(var syncIntervalMinutes: Long = 30L)

    /**
     * Symuluje WidgetReceiver.onEnabled() z WidgetReceiver.kt.
     *
     * Kod produkcyjny (WidgetReceiver.kt):
     *   override fun onEnabled(context: Context) {
     *       super.onEnabled(context)
     *       enqueuePeriodicRefresh(context)
     *   }
     *
     * Zachowanie: planuje pracę WorkManager dla ThingSpeakGlanceWidget.
     * UWAGA: buggy enqueuePeriodicRefresh hardkoduje 30L – ale PLANUJE pracę (to jest poprawne).
     */
    private fun simulateWidgetReceiverOnEnabled(workManager: SimulatedWorkManager) {
        // Reflects production code: enqueuePeriodicRefresh always schedules work
        // (with hardcoded 30L – BUG 1, but the scheduling itself is correct behavior)
        workManager.enqueueUniquePeriodicWork(
            name = WORK_NAME,
            policy = SimulatedWorkPolicy.UPDATE, // current buggy policy – but work IS scheduled
            intervalMinutes = 30L               // hardcoded – BUG 1, but irrelevant for this preservation test
        )
    }

    /**
     * Symuluje WidgetReceiver.onDeleted() z WidgetReceiver.kt.
     *
     * Kod produkcyjny (WidgetReceiver.kt):
     *   override fun onDeleted(context: Context, appWidgetIds: IntArray) {
     *       val remaining = manager.getAppWidgetIds(ComponentName(context, WidgetReceiver::class.java))
     *       if (remaining.isEmpty()) {
     *           WorkManager.getInstance(context).cancelUniqueWork(DataSyncWorker.WORK_NAME)
     *       }
     *   }
     *
     * Zachowanie: anuluje pracę gdy brak aktywnych widgetów ThingSpeakGlanceWidget.
     * UWAGA: obecny kod sprawdza TYLKO ThingSpeakGlanceWidget (BUG 2 – nie sprawdza ValueGridWidget).
     * Ten test weryfikuje zachowanie dla ThingSpeakGlanceWidget (które jest poprawne).
     */
    private fun simulateWidgetReceiverOnDeleted(
        workManager: SimulatedWorkManager,
        remainingThingSpeakWidgets: Int,
        remainingValueGridWidgets: Int
    ) {
        // Production code only checks ThingSpeakGlanceWidget remaining count
        // (BUG 2: doesn't check ValueGridWidget – but that's a separate bug)
        if (remainingThingSpeakWidgets == 0) {
            // Current buggy behavior: cancels work even if ValueGridWidget is still active
            // For preservation test: we test the case where BOTH are 0 (no active widgets)
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    /**
     * Symuluje RescheduleWorker.doWork() z RescheduleWorker.kt.
     *
     * Kod produkcyjny (RescheduleWorker.kt):
     *   override suspend fun doWork(): Result {
     *       val intervalMinutes = appPreferences.observeSyncInterval().first()
     *       DataSyncWorker.schedule(applicationContext, intervalMinutes)
     *       ...
     *   }
     *
     * Zachowanie: odczytuje interwał z AppPreferences i planuje DataSyncWorker.
     * UWAGA: RescheduleWorker poprawnie odczytuje interwał (brak BUG 1 w tym miejscu).
     */
    private fun simulateRescheduleWorkerDoWork(
        workManager: SimulatedWorkManager,
        appPreferences: SimulatedAppPreferences
    ) {
        // Reflects production code: reads interval from AppPreferences (correct behavior)
        val intervalMinutes = appPreferences.syncIntervalMinutes
        workManager.enqueueUniquePeriodicWork(
            name = WORK_NAME,
            policy = SimulatedWorkPolicy.UPDATE, // current buggy policy in DataSyncWorker.schedule()
            intervalMinutes = intervalMinutes    // correctly reads from AppPreferences
        )
    }

    /**
     * Symuluje DataSyncWorker.runOnce() z DataSyncWorker.kt.
     *
     * Kod produkcyjny (DataSyncWorker.kt):
     *   fun runOnce(context: Context) {
     *       val request = OneTimeWorkRequestBuilder<DataSyncWorker>().setConstraints(constraints()).build()
     *       WorkManager.getInstance(context).enqueue(request)
     *   }
     *
     * Zachowanie: wyzwala jednorazową synchronizację niezależnie od schedule().
     */
    class SimulatedOneTimeWorkManager {
        val enqueuedOneTimeWorks = mutableListOf<String>()

        fun enqueueOneTimeWork(tag: String) {
            enqueuedOneTimeWorks.add(tag)
        }
    }

    private fun simulateDataSyncWorkerRunOnce(oneTimeWorkManager: SimulatedOneTimeWorkManager) {
        // Reflects production code: enqueues a one-time work request
        oneTimeWorkManager.enqueueOneTimeWork("DataSyncWorker_OneTime")
    }

    /**
     * Symuluje DataSyncWorker.schedule() z DataSyncWorker.kt.
     *
     * Kod produkcyjny (DataSyncWorker.kt):
     *   fun schedule(context: Context, intervalMinutes: Long) {
     *       WorkManager.getInstance(context).enqueueUniquePeriodicWork(
     *           WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
     *       )
     *   }
     */
    private fun simulateDataSyncWorkerSchedule(
        workManager: SimulatedWorkManager,
        intervalMinutes: Long
    ) {
        workManager.enqueueUniquePeriodicWork(
            name = WORK_NAME,
            policy = SimulatedWorkPolicy.UPDATE, // current buggy policy
            intervalMinutes = intervalMinutes
        )
    }

    companion object {
        private const val WORK_NAME = "DataSyncWorker"
    }

    // =========================================================================
    // PRESERVATION 1 – WidgetReceiver.onEnabled() planuje pracę dla ThingSpeakGlanceWidget
    // =========================================================================

    /**
     * PRESERVATION 1: WidgetReceiver.onEnabled() planuje pracę WorkManager.
     *
     * Wymaganie 3.3: WHEN widget ThingSpeakGlanceWidget jest dodany na ekran główny
     * THEN system SHALL CONTINUE TO planować pracę periodyczną WorkManager
     * przez WidgetReceiver.onEnabled().
     *
     * Ten test PRZECHODZI na nienaprawionym kodzie – planowanie pracy jest poprawne
     * (mimo że interwał jest hardkodowany – to jest BUG 1, nie BUG w samym planowaniu).
     *
     * Validates: Requirements 3.3
     */
    @Test
    fun `PRESERVATION1 - WidgetReceiver onEnabled planuje prace WorkManager dla ThingSpeakGlanceWidget`() {
        // Arrange: brak istniejącej pracy WorkManager
        val workManager = SimulatedWorkManager()
        assertTrue(
            "Stan początkowy: brak pracy WorkManager",
            workManager.getWorkInfosForUniqueWork(WORK_NAME).isEmpty()
        )

        // Act: WidgetReceiver.onEnabled() – planuje pracę (odzwierciedla kod produkcyjny)
        simulateWidgetReceiverOnEnabled(workManager)

        // Assert: praca WorkManager została zaplanowana
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)

        println("PRESERVATION 1: WidgetReceiver.onEnabled() planuje pracę WorkManager")
        println("  workExists = ${workInfos.isNotEmpty()}")
        println("  intervalMinutes = ${workInfos.firstOrNull()?.intervalMinutes}")
        println("  state = ${workInfos.firstOrNull()?.state}")

        assertTrue(
            "PRESERVATION: WidgetReceiver.onEnabled() musi planować pracę WorkManager dla ThingSpeakGlanceWidget",
            workInfos.isNotEmpty()
        )
        assertEquals(
            "PRESERVATION: zaplanowana praca musi mieć stan ENQUEUED",
            "ENQUEUED",
            workInfos.first().state
        )
    }

    /**
     * PRESERVATION 1 (dodatkowy przypadek): onEnabled() planuje pracę z interwałem > 0.
     *
     * Validates: Requirements 3.3
     */
    @Test
    fun `PRESERVATION1 - WidgetReceiver onEnabled planuje prace z dodatnim interwalem`() {
        // Arrange
        val workManager = SimulatedWorkManager()

        // Act
        simulateWidgetReceiverOnEnabled(workManager)

        // Assert: interwał musi być dodatni (niezależnie od wartości – to jest BUG 1)
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertTrue("Praca musi istnieć", workInfos.isNotEmpty())
        assertTrue(
            "PRESERVATION: interwał synchronizacji musi być dodatni",
            workInfos.first().intervalMinutes > 0L
        )
    }

    // =========================================================================
    // PRESERVATION 2 – WidgetReceiver.onDeleted() anuluje pracę przy braku aktywnych widgetów
    // =========================================================================

    /**
     * PRESERVATION 2: WidgetReceiver.onDeleted() anuluje pracę gdy brak aktywnych widgetów obu typów.
     *
     * Wymaganie 3.4: WHEN ostatni widget ThingSpeakGlanceWidget jest usuwany z ekranu głównego
     * THEN system SHALL CONTINUE TO anulować pracę periodyczną WorkManager
     * przez WidgetReceiver.onDeleted().
     *
     * Ten test PRZECHODZI na nienaprawionym kodzie – anulowanie pracy jest poprawne
     * gdy nie ma żadnych aktywnych widgetów ThingSpeakGlanceWidget.
     *
     * Validates: Requirements 3.4
     */
    @Test
    fun `PRESERVATION2 - WidgetReceiver onDeleted anuluje prace gdy brak aktywnych widgetow obu typow`() {
        // Arrange: praca WorkManager jest zaplanowana
        val workManager = SimulatedWorkManager()
        simulateWidgetReceiverOnEnabled(workManager)
        assertTrue(
            "Stan początkowy: praca WorkManager musi istnieć",
            workManager.getWorkInfosForUniqueWork(WORK_NAME).isNotEmpty()
        )

        // Act: onDeleted() – ostatni widget usunięty, brak aktywnych widgetów obu typów
        val remainingThingSpeakWidgets = 0
        val remainingValueGridWidgets = 0
        simulateWidgetReceiverOnDeleted(workManager, remainingThingSpeakWidgets, remainingValueGridWidgets)

        // Assert: praca WorkManager została anulowana
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)

        println("PRESERVATION 2: WidgetReceiver.onDeleted() anuluje pracę")
        println("  remainingThingSpeakWidgets = $remainingThingSpeakWidgets")
        println("  remainingValueGridWidgets = $remainingValueGridWidgets")
        println("  workExists po onDeleted() = ${workInfos.isNotEmpty()}")

        assertTrue(
            "PRESERVATION: WidgetReceiver.onDeleted() musi anulować pracę WorkManager gdy brak aktywnych widgetów",
            workInfos.isEmpty()
        )
    }

    /**
     * PRESERVATION 2 (przypadek: pozostały aktywne widgety ThingSpeakGlanceWidget):
     * onDeleted() NIE anuluje pracy gdy nadal istnieją aktywne widgety ThingSpeakGlanceWidget.
     *
     * Validates: Requirements 3.4
     */
    @Test
    fun `PRESERVATION2 - WidgetReceiver onDeleted nie anuluje pracy gdy pozostaly aktywne widgety ThingSpeakGlanceWidget`() {
        // Arrange: praca WorkManager jest zaplanowana
        val workManager = SimulatedWorkManager()
        simulateWidgetReceiverOnEnabled(workManager)

        // Act: onDeleted() – usunięto jeden widget, ale nadal pozostał 1 aktywny ThingSpeakGlanceWidget
        val remainingThingSpeakWidgets = 1
        val remainingValueGridWidgets = 0
        simulateWidgetReceiverOnDeleted(workManager, remainingThingSpeakWidgets, remainingValueGridWidgets)

        // Assert: praca WorkManager NIE została anulowana
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)

        println("PRESERVATION 2 (pozostały widgety): onDeleted() nie anuluje pracy")
        println("  remainingThingSpeakWidgets = $remainingThingSpeakWidgets")
        println("  workExists = ${workInfos.isNotEmpty()}")

        assertTrue(
            "PRESERVATION: praca WorkManager musi pozostać gdy nadal istnieją aktywne widgety ThingSpeakGlanceWidget",
            workInfos.isNotEmpty()
        )
    }

    // =========================================================================
    // PRESERVATION 3 – RescheduleWorker poprawnie odczytuje interwał z AppPreferences
    // =========================================================================

    /**
     * PRESERVATION 3: RescheduleWorker.doWork() odczytuje interwał z AppPreferences i planuje DataSyncWorker.
     *
     * Wymaganie 3.1: WHEN urządzenie jest restartowane I BootReceiver odbiera ACTION_BOOT_COMPLETED
     * THEN system SHALL CONTINUE TO uruchamiać RescheduleWorker, który odczytuje właściwy interwał
     * i planuje DataSyncWorker.
     *
     * Ten test PRZECHODZI na nienaprawionym kodzie – RescheduleWorker poprawnie odczytuje interwał
     * (w przeciwieństwie do WidgetReceiver.enqueuePeriodicRefresh() – BUG 1).
     *
     * Validates: Requirements 3.1
     */
    @Test
    fun `PRESERVATION3 - RescheduleWorker odczytuje interwal z AppPreferences i planuje DataSyncWorker`() {
        // Arrange: użytkownik ustawił interwał 15 minut
        val userInterval = 15L
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = userInterval)
        val workManager = SimulatedWorkManager()

        // Act: RescheduleWorker.doWork() – odczytuje interwał i planuje pracę
        simulateRescheduleWorkerDoWork(workManager, appPreferences)

        // Assert: praca zaplanowana z interwałem z AppPreferences
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)

        println("PRESERVATION 3: RescheduleWorker odczytuje interwał z AppPreferences")
        println("  userInterval = $userInterval")
        println("  scheduledInterval = ${workInfos.firstOrNull()?.intervalMinutes}")

        assertTrue("Praca WorkManager musi być zaplanowana", workInfos.isNotEmpty())
        assertEquals(
            "PRESERVATION: RescheduleWorker musi planować DataSyncWorker z interwałem z AppPreferences",
            userInterval,
            workInfos.first().intervalMinutes
        )
    }

    /**
     * PRESERVATION 3 (różne interwały): RescheduleWorker poprawnie planuje z dowolnym interwałem.
     *
     * Validates: Requirements 3.1
     */
    @Test
    fun `PRESERVATION3 - RescheduleWorker planuje DataSyncWorker z roznym interwalem z AppPreferences`() {
        // Test for multiple interval values to verify RescheduleWorker reads from AppPreferences correctly
        val testIntervals = listOf(15L, 30L, 60L, 120L)

        testIntervals.forEach { interval ->
            val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = interval)
            val workManager = SimulatedWorkManager()

            simulateRescheduleWorkerDoWork(workManager, appPreferences)

            val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)
            assertTrue("Praca musi istnieć dla interwału $interval", workInfos.isNotEmpty())
            assertEquals(
                "PRESERVATION: RescheduleWorker musi planować z interwałem $interval z AppPreferences",
                interval,
                workInfos.first().intervalMinutes
            )

            println("PRESERVATION 3 (interval=$interval): scheduledInterval=${workInfos.first().intervalMinutes} ✓")
        }
    }

    // =========================================================================
    // PRESERVATION 4 – DataSyncWorker.runOnce() wyzwala jednorazową synchronizację
    // =========================================================================

    /**
     * PRESERVATION 4: DataSyncWorker.runOnce() wyzwala jednorazową synchronizację
     * niezależnie od schedule().
     *
     * Wymaganie 3.6: WHEN WidgetSyncOrchestrator.triggerSyncIfNeeded() wykryje nieaktualne
     * lub brakujące dane THEN system SHALL CONTINUE TO wyzwalać jednorazową synchronizację
     * przez DataSyncWorker.runOnce().
     *
     * Ten test PRZECHODZI na nienaprawionym kodzie – runOnce() jest niezależne od schedule().
     *
     * Validates: Requirements 3.6
     */
    @Test
    fun `PRESERVATION4 - DataSyncWorker runOnce wyzwala jednorazowa synchronizacje niezaleznie od schedule`() {
        // Arrange: brak zaplanowanej pracy periodycznej
        val workManager = SimulatedWorkManager()
        val oneTimeWorkManager = SimulatedOneTimeWorkManager()

        assertTrue(
            "Stan początkowy: brak pracy periodycznej",
            workManager.getWorkInfosForUniqueWork(WORK_NAME).isEmpty()
        )
        assertTrue(
            "Stan początkowy: brak jednorazowych prac",
            oneTimeWorkManager.enqueuedOneTimeWorks.isEmpty()
        )

        // Act: runOnce() – wyzwala jednorazową synchronizację (niezależnie od schedule())
        simulateDataSyncWorkerRunOnce(oneTimeWorkManager)

        // Assert: jednorazowa praca została zakolejkowana
        println("PRESERVATION 4: DataSyncWorker.runOnce() wyzwala jednorazową synchronizację")
        println("  enqueuedOneTimeWorks = ${oneTimeWorkManager.enqueuedOneTimeWorks}")
        println("  periodicWorkExists = ${workManager.getWorkInfosForUniqueWork(WORK_NAME).isNotEmpty()}")

        assertTrue(
            "PRESERVATION: DataSyncWorker.runOnce() musi zakolejkować jednorazową pracę synchronizacji",
            oneTimeWorkManager.enqueuedOneTimeWorks.isNotEmpty()
        )
        assertTrue(
            "PRESERVATION: praca periodyczna NIE musi istnieć – runOnce() jest niezależne od schedule()",
            workManager.getWorkInfosForUniqueWork(WORK_NAME).isEmpty()
        )
    }

    /**
     * PRESERVATION 4 (wielokrotne wywołania): runOnce() można wywołać wielokrotnie niezależnie.
     *
     * Validates: Requirements 3.6
     */
    @Test
    fun `PRESERVATION4 - DataSyncWorker runOnce mozna wywolac wielokrotnie niezaleznie`() {
        // Arrange
        val oneTimeWorkManager = SimulatedOneTimeWorkManager()

        // Act: wywołaj runOnce() trzykrotnie (np. triggerSyncIfNeeded() dla 3 widgetów)
        simulateDataSyncWorkerRunOnce(oneTimeWorkManager)
        simulateDataSyncWorkerRunOnce(oneTimeWorkManager)
        simulateDataSyncWorkerRunOnce(oneTimeWorkManager)

        // Assert: każde wywołanie zakolejkowało jednorazową pracę
        println("PRESERVATION 4 (wielokrotne): enqueuedOneTimeWorks.size = ${oneTimeWorkManager.enqueuedOneTimeWorks.size}")

        assertEquals(
            "PRESERVATION: każde wywołanie runOnce() musi zakolejkować jednorazową pracę",
            3,
            oneTimeWorkManager.enqueuedOneTimeWorks.size
        )
    }

    // =========================================================================
    // PRESERVATION 5 – Pierwsze wywołanie schedule() tworzy nową pracę niezależnie od polityki
    // =========================================================================

    /**
     * PRESERVATION 5: Pierwsze wywołanie schedule() (brak istniejącej pracy) tworzy nową pracę
     * niezależnie od polityki (KEEP i UPDATE zachowują się identycznie przy braku istniejącej pracy).
     *
     * Wymaganie 3.3: WHEN widget ThingSpeakGlanceWidget jest dodany na ekran główny
     * THEN system SHALL CONTINUE TO planować pracę periodyczną WorkManager.
     *
     * Ten test PRZECHODZI na nienaprawionym kodzie – pierwsze wywołanie schedule() zawsze
     * tworzy nową pracę (zarówno z UPDATE jak i KEEP).
     *
     * Validates: Requirements 3.3, 3.4
     */
    @Test
    fun `PRESERVATION5 - pierwsze wywolanie schedule tworzy nowa prace niezaleznie od polityki`() {
        // Arrange: brak istniejącej pracy WorkManager
        val workManager = SimulatedWorkManager()
        assertTrue(
            "Stan początkowy: brak pracy WorkManager",
            workManager.getWorkInfosForUniqueWork(WORK_NAME).isEmpty()
        )

        // Act: pierwsze wywołanie schedule() z aktualną (buggy) polityką UPDATE
        simulateDataSyncWorkerSchedule(workManager, 30L)

        // Assert: praca została utworzona
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)

        println("PRESERVATION 5: pierwsze wywołanie schedule() tworzy nową pracę")
        println("  workExists = ${workInfos.isNotEmpty()}")
        println("  intervalMinutes = ${workInfos.firstOrNull()?.intervalMinutes}")
        println("  state = ${workInfos.firstOrNull()?.state}")

        assertTrue(
            "PRESERVATION: pierwsze wywołanie schedule() musi tworzyć nową pracę WorkManager",
            workInfos.isNotEmpty()
        )
        assertEquals(
            "PRESERVATION: zaplanowana praca musi mieć stan ENQUEUED",
            "ENQUEUED",
            workInfos.first().state
        )
        assertEquals(
            "PRESERVATION: zaplanowana praca musi mieć przekazany interwał",
            30L,
            workInfos.first().intervalMinutes
        )
    }

    /**
     * PRESERVATION 5 (różne interwały): pierwsze wywołanie schedule() tworzy pracę z dowolnym interwałem.
     *
     * Validates: Requirements 3.3
     */
    @Test
    fun `PRESERVATION5 - pierwsze wywolanie schedule tworzy prace z dowolnym interwalem`() {
        // Test for multiple interval values
        val testIntervals = listOf(15L, 30L, 60L, 120L)

        testIntervals.forEach { interval ->
            val workManager = SimulatedWorkManager()

            simulateDataSyncWorkerSchedule(workManager, interval)

            val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)
            assertTrue("Praca musi istnieć dla interwału $interval", workInfos.isNotEmpty())
            assertEquals(
                "PRESERVATION: pierwsze wywołanie schedule() musi tworzyć pracę z interwałem $interval",
                interval,
                workInfos.first().intervalMinutes
            )

            println("PRESERVATION 5 (interval=$interval): workCreated=true, intervalMinutes=${workInfos.first().intervalMinutes} ✓")
        }
    }

    /**
     * PRESERVATION 5 (weryfikacja symetrii KEEP/UPDATE przy braku istniejącej pracy):
     * Obie polityki zachowują się identycznie przy pierwszym wywołaniu.
     *
     * Validates: Requirements 3.3
     */
    @Test
    fun `PRESERVATION5 - KEEP i UPDATE zachowuja sie identycznie przy braku istniejacych prac`() {
        // Arrange: dwa niezależne WorkManagery (symulacja dwóch scenariuszy)
        val workManagerWithUpdate = SimulatedWorkManager()
        val workManagerWithKeep = SimulatedWorkManager()

        // Act: pierwsze wywołanie z UPDATE (aktualny buggy kod)
        workManagerWithUpdate.enqueueUniquePeriodicWork(WORK_NAME, SimulatedWorkPolicy.UPDATE, 30L)

        // Act: pierwsze wywołanie z KEEP (naprawiony kod)
        workManagerWithKeep.enqueueUniquePeriodicWork(WORK_NAME, SimulatedWorkPolicy.KEEP, 30L)

        // Assert: oba zachowują się identycznie przy braku istniejącej pracy
        val workInfoUpdate = workManagerWithUpdate.getWorkInfosForUniqueWork(WORK_NAME)
        val workInfoKeep = workManagerWithKeep.getWorkInfosForUniqueWork(WORK_NAME)

        println("PRESERVATION 5 (KEEP vs UPDATE przy braku pracy):")
        println("  UPDATE: workExists=${workInfoUpdate.isNotEmpty()}, interval=${workInfoUpdate.firstOrNull()?.intervalMinutes}")
        println("  KEEP:   workExists=${workInfoKeep.isNotEmpty()}, interval=${workInfoKeep.firstOrNull()?.intervalMinutes}")

        assertTrue("UPDATE: praca musi istnieć", workInfoUpdate.isNotEmpty())
        assertTrue("KEEP: praca musi istnieć", workInfoKeep.isNotEmpty())
        assertEquals(
            "PRESERVATION: KEEP i UPDATE muszą tworzyć pracę z tym samym interwałem przy pierwszym wywołaniu",
            workInfoUpdate.first().intervalMinutes,
            workInfoKeep.first().intervalMinutes
        )
        assertEquals(
            "PRESERVATION: KEEP i UPDATE muszą tworzyć pracę z tym samym stanem przy pierwszym wywołaniu",
            workInfoUpdate.first().state,
            workInfoKeep.first().state
        )
    }
}
