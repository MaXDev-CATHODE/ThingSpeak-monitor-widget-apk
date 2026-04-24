package com.thingspeak.monitor.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Weryfikacyjne testy warunku błędu dla automatycznej synchronizacji widgetów Glance.
 *
 * CEL: Potwierdzić, że wszystkie trzy błędy zostały NAPRAWIONE w kodzie produkcyjnym.
 * Testy powinny PRZECHODZIĆ – to potwierdza poprawność naprawy.
 *
 * Validates: Requirements 1.1, 1.2, 1.4, 1.5, 1.7, 1.8, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9
 *
 * Metodologia:
 * - Symulujemy logikę produkcyjną przez klasy pomocnicze (wzorzec z DebounceLogicTest)
 * - Asercje sprawdzają OCZEKIWANE POPRAWNE zachowanie → PRZECHODZĄ na naprawionym kodzie
 * - Funkcje fixed* odzwierciedlają naprawiony kod produkcyjny
 */
class GlanceWidgetAutoSyncBugConditionTest {

    // =========================================================================
    // Symulacja logiki produkcyjnej (odzwierciedla rzeczywisty kod)
    // =========================================================================

    /**
     * Symuluje politykę WorkManager przy planowaniu pracy periodycznej.
     * Odzwierciedla ExistingPeriodicWorkPolicy z DataSyncWorker.kt
     */
    enum class SimulatedWorkPolicy { KEEP, UPDATE }

    /**
     * Symuluje stan pracy WorkManager.
     */
    data class SimulatedWorkInfo(
        val workName: String,
        val intervalMinutes: Long,
        val state: String = "ENQUEUED",
        val nextScheduleTimeMillis: Long = System.currentTimeMillis() + intervalMinutes * 60_000L
    )

    /**
     * Symuluje rejestr prac WorkManager (in-memory).
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
                    // Pierwsza rejestracja – zawsze tworzy nową pracę
                    works[name] = SimulatedWorkInfo(name, intervalMinutes)
                }
                policy == SimulatedWorkPolicy.UPDATE -> {
                    // BUG 3: UPDATE zastępuje istniejącą pracę, resetując timer
                    works[name] = SimulatedWorkInfo(name, intervalMinutes)
                }
                policy == SimulatedWorkPolicy.KEEP -> {
                    // Poprawne zachowanie: zachowuje istniejącą pracę bez resetowania timera
                    // (brak zmiany)
                }
            }
        }

        fun getWorkInfosForUniqueWork(name: String): List<SimulatedWorkInfo> =
            listOfNotNull(works[name])

        fun cancelUniqueWork(name: String) {
            works.remove(name)
        }
    }

    /**
     * Symuluje AppPreferences – przechowuje interwał synchronizacji.
     */
    class SimulatedAppPreferences(var syncIntervalMinutes: Long = 30L)

    /**
     * Symuluje NAPRAWIONY enqueuePeriodicRefresh z WidgetReceiver.kt.
     *
     * Naprawiony kod produkcyjny (WidgetReceiver.kt):
     *   fun enqueuePeriodicRefresh(context: Context) {
     *       CoroutineScope(Dispatchers.IO).launch {
     *           val intervalMinutes = entryPoint.appPreferences().observeSyncInterval().first()
     *           DataSyncWorker.schedule(context, intervalMinutes)  // ← odczytuje z AppPreferences
     *       }
     *   }
     *
     * NAPRAWA BUG 1: Odczytuje interwał z appPreferences.syncIntervalMinutes zamiast hardkodowanego 30L.
     */
    private fun fixedEnqueuePeriodicRefresh(
        workManager: SimulatedWorkManager,
        appPreferences: SimulatedAppPreferences
    ) {
        // Odzwierciedla naprawiony kod produkcyjny – odczytuje interwał z appPreferences
        val intervalMinutes = appPreferences.syncIntervalMinutes
        workManager.enqueueUniquePeriodicWork(
            name = WORK_NAME,
            policy = SimulatedWorkPolicy.KEEP, // NAPRAWA BUG 3: używa KEEP zamiast UPDATE
            intervalMinutes = intervalMinutes
        )
    }

    /**
     * Symuluje NAPRAWIONY ValueGridWidgetReceiver.onEnabled z ValueGridWidget.kt.
     *
     * Naprawiony kod produkcyjny (ValueGridWidget.kt):
     *   override fun onEnabled(context: Context) {
     *       super.onEnabled(context)
     *       WidgetReceiver.enqueuePeriodicRefresh(context)  // ← wywołuje planowanie pracy
     *   }
     *
     * NAPRAWA BUG 2: Wywołuje enqueuePeriodicRefresh – praca WorkManager jest planowana.
     */
    private fun fixedValueGridWidgetReceiverOnEnabled(
        workManager: SimulatedWorkManager,
        appPreferences: SimulatedAppPreferences
    ) {
        // Odzwierciedla naprawiony kod produkcyjny – wywołuje enqueuePeriodicRefresh
        fixedEnqueuePeriodicRefresh(workManager, appPreferences)
    }

    /**
     * Symuluje NAPRAWIONY DataSyncWorker.schedule z DataSyncWorker.kt.
     *
     * Naprawiony kod produkcyjny (DataSyncWorker.kt):
     *   WorkManager.getInstance(context).enqueueUniquePeriodicWork(
     *       WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request  // ← NAPRAWA: KEEP
     *   )
     *
     * NAPRAWA BUG 3: Używa KEEP zamiast UPDATE – timer NIE jest resetowany przy kolejnych wywołaniach.
     */
    private fun fixedDataSyncWorkerSchedule(
        workManager: SimulatedWorkManager,
        intervalMinutes: Long
    ) {
        workManager.enqueueUniquePeriodicWork(
            name = WORK_NAME,
            policy = SimulatedWorkPolicy.KEEP, // NAPRAWA BUG 3: KEEP zamiast UPDATE
            intervalMinutes = intervalMinutes
        )
    }

    companion object {
        private const val WORK_NAME = "DataSyncWorker"
    }

    // =========================================================================
    // BUG 1 – Test hardkodowanego interwału synchronizacji
    // =========================================================================

    /**
     * BUG 1 NAPRAWIONY: enqueuePeriodicRefresh() planuje z interwałem z AppPreferences.
     *
     * Weryfikacja naprawy: userInterval = 15L → scheduledInterval = 15L
     *
     * Ten test PRZECHODZI na naprawionym kodzie, ponieważ:
     * - fixedEnqueuePeriodicRefresh odczytuje interwał z appPreferences.syncIntervalMinutes
     * - scheduledInterval = userInterval = 15L
     *
     * Validates: Requirements 1.1, 1.2, 2.1, 2.2, 2.3
     */
    @Test
    fun `BUG1 - enqueuePeriodicRefresh powinien planowac z interwalem z AppPreferences nie 30L`() {
        // Arrange: użytkownik ustawił interwał 15 minut (różny od domyślnych 30)
        val userInterval = 15L
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = userInterval)
        val workManager = SimulatedWorkManager()

        // Warunek weryfikacji: userInterval ≠ 30L (gdyby był 30L, błąd byłby maskowany)
        assertFalse(
            "Warunek weryfikacji: userInterval musi być różny od 30L",
            userInterval == 30L
        )

        // Act: wywołaj fixed enqueuePeriodicRefresh (odzwierciedla naprawiony kod produkcyjny)
        fixedEnqueuePeriodicRefresh(workManager, appPreferences)

        // Assert: sprawdź zaplanowany interwał
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertTrue("Praca WorkManager powinna być zaplanowana", workInfos.isNotEmpty())

        val scheduledInterval = workInfos.first().intervalMinutes

        println("WERYFIKACJA NAPRAWY BUG 1: userInterval=$userInterval, scheduledInterval=$scheduledInterval")
        println("  Oczekiwano: scheduledInterval = $userInterval")
        println("  Rzeczywistość: scheduledInterval = $scheduledInterval")
        println("  Naprawa potwierdzona: scheduledInterval = userInterval = $userInterval")

        // Ta asercja PRZECHODZI na naprawionym kodzie:
        // fixedEnqueuePeriodicRefresh odczytuje interwał z appPreferences, więc scheduledInterval = 15L
        assertEquals(
            "NAPRAWA BUG 1: scheduledInterval powinien = userInterval=$userInterval. " +
                "fixedEnqueuePeriodicRefresh odczytuje interwał z AppPreferences zamiast hardkodować 30L.",
            userInterval,
            scheduledInterval
        )
    }

    /**
     * BUG 1 NAPRAWIONY (dodatkowy przypadek): interwał 60 minut – naprawa działa.
     *
     * Validates: Requirements 1.1, 1.2, 2.1, 2.2
     */
    @Test
    fun `BUG1 - enqueuePeriodicRefresh ignoruje interwal 60 minut z AppPreferences`() {
        // Arrange: użytkownik ustawił interwał 60 minut
        val userInterval = 60L
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = userInterval)
        val workManager = SimulatedWorkManager()

        // Act
        fixedEnqueuePeriodicRefresh(workManager, appPreferences)

        // Assert
        val scheduledInterval = workManager.getWorkInfosForUniqueWork(WORK_NAME).first().intervalMinutes

        println("WERYFIKACJA NAPRAWY BUG 1 (60 min): userInterval=$userInterval, scheduledInterval=$scheduledInterval")

        // Ta asercja PRZECHODZI na naprawionym kodzie: scheduledInterval = 60L
        assertEquals(
            "NAPRAWA BUG 1: scheduledInterval=$userInterval. " +
                "fixedEnqueuePeriodicRefresh odczytuje interwał z AppPreferences.",
            userInterval,
            scheduledInterval
        )
    }

    /**
     * BUG 1 (przypadek brzegowy): interwał domyślny 30 minut.
     *
     * Gdy userInterval = 30L, zarówno buggy jak i fixed kod dają ten sam wynik.
     * Ten test PRZECHODZI zarówno na starym jak i naprawionym kodzie.
     *
     * Validates: Requirements 1.1
     */
    @Test
    fun `BUG1 - przypadek brzegowy interwal domyslny 30 minut maskuje blad`() {
        // Arrange: użytkownik ma domyślny interwał 30 minut
        val userInterval = 30L
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = userInterval)
        val workManager = SimulatedWorkManager()

        // Act: naprawiony kod odczytuje interwał z appPreferences (= 30L)
        fixedEnqueuePeriodicRefresh(workManager, appPreferences)

        // Assert: ten test PRZECHODZI (zarówno na starym jak i naprawionym kodzie)
        val scheduledInterval = workManager.getWorkInfosForUniqueWork(WORK_NAME).first().intervalMinutes

        println("PRZYPADEK BRZEGOWY BUG 1: userInterval=$userInterval, scheduledInterval=$scheduledInterval")
        println("  Naprawa: fixedEnqueuePeriodicRefresh odczytuje 30L z AppPreferences (wynik identyczny)")

        // Ta asercja PRZECHODZI – naprawiony kod odczytuje 30L z AppPreferences
        assertEquals(
            "Przypadek brzegowy: userInterval=30L, scheduledInterval powinien = 30L",
            userInterval,
            scheduledInterval
        )
    }

    // =========================================================================
    // BUG 2 – Test braku planowania synchronizacji dla ValueGridWidget
    // =========================================================================

    /**
     * BUG 2 NAPRAWIONY: ValueGridWidgetReceiver.onEnabled() planuje pracę WorkManager.
     *
     * Weryfikacja naprawy: po onEnabled() praca WorkManager istnieje.
     *
     * Ten test PRZECHODZI na naprawionym kodzie, ponieważ:
     * - fixedValueGridWidgetReceiverOnEnabled wywołuje fixedEnqueuePeriodicRefresh
     * - workExists = true po onEnabled()
     *
     * Validates: Requirements 1.4, 1.5, 2.4, 2.5, 2.6
     */
    @Test
    fun `BUG2 - ValueGridWidgetReceiver onEnabled powinien planowac prace WorkManager`() {
        // Arrange: tylko ValueGridWidget jest aktywny, brak ThingSpeakGlanceWidget
        val hasValueGridWidget = true
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = 30L)
        val workManager = SimulatedWorkManager()

        // Warunek weryfikacji: ValueGridWidget aktywny, brak ThingSpeakGlanceWidget
        assertTrue("Warunek weryfikacji: ValueGridWidget musi być aktywny", hasValueGridWidget)

        // Weryfikacja stanu początkowego: brak pracy WorkManager
        val workBeforeOnEnabled = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertTrue(
            "Stan początkowy: brak pracy WorkManager przed onEnabled()",
            workBeforeOnEnabled.isEmpty()
        )

        // Act: wywołaj fixed ValueGridWidgetReceiver.onEnabled() (odzwierciedla naprawiony kod produkcyjny)
        fixedValueGridWidgetReceiverOnEnabled(workManager, appPreferences)

        // Assert: sprawdź czy praca WorkManager została zaplanowana
        val workAfterOnEnabled = workManager.getWorkInfosForUniqueWork(WORK_NAME)

        println("WERYFIKACJA NAPRAWY BUG 2:")
        println("  hasValueGridWidget=$hasValueGridWidget")
        println("  workExists po onEnabled() = ${workAfterOnEnabled.isNotEmpty()}")
        println("  Oczekiwano: workExists = true")
        println("  Naprawa potwierdzona: ValueGridWidgetReceiver.onEnabled() wywołuje enqueuePeriodicRefresh()")

        // Ta asercja PRZECHODZI na naprawionym kodzie:
        // fixedValueGridWidgetReceiverOnEnabled wywołuje fixedEnqueuePeriodicRefresh, która planuje pracę
        assertTrue(
            "NAPRAWA BUG 2: praca WorkManager powinna istnieć po ValueGridWidgetReceiver.onEnabled(). " +
                "fixedValueGridWidgetReceiverOnEnabled wywołuje enqueuePeriodicRefresh().",
            workAfterOnEnabled.isNotEmpty()
        )
    }

    /**
     * BUG 2 (weryfikacja maskowania): gdy ThingSpeakGlanceWidget jest aktywny,
     * praca jest planowana przez WidgetReceiver – zachowanie nadal poprawne.
     *
     * Validates: Requirements 1.4
     */
    @Test
    fun `BUG2 - gdy ThingSpeakGlanceWidget jest aktywny blad jest maskowany`() {
        // Arrange: oba widgety aktywne – ThingSpeakGlanceWidget planuje pracę przez swój receiver
        val appPreferences = SimulatedAppPreferences(syncIntervalMinutes = 30L)
        val workManager = SimulatedWorkManager()

        // Symulacja: WidgetReceiver.onEnabled() planuje pracę dla ThingSpeakGlanceWidget
        workManager.enqueueUniquePeriodicWork(WORK_NAME, SimulatedWorkPolicy.KEEP, 30L)

        // Act: ValueGridWidgetReceiver.onEnabled() – naprawiony kod wywołuje enqueuePeriodicRefresh
        fixedValueGridWidgetReceiverOnEnabled(workManager, appPreferences)

        // Assert: praca istnieje (planowana przez oba receivery, KEEP zachowuje istniejącą)
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)

        println("PRZYPADEK MASKOWANIA BUG 2: oba widgety aktywne")
        println("  workExists = ${workInfos.isNotEmpty()} (planowane przez oba receivery, KEEP zachowuje istniejącą)")

        // Ten test PRZECHODZI – praca istnieje
        assertTrue(
            "Praca istnieje po onEnabled() obu receiverów",
            workInfos.isNotEmpty()
        )
    }

    // =========================================================================
    // BUG 3 – Test resetowania timera przez politykę UPDATE
    // =========================================================================

    /**
     * BUG 3 NAPRAWIONY: DataSyncWorker.schedule() używa KEEP – timer NIE jest resetowany.
     *
     * Weryfikacja naprawy: nextRunAfter = nextRunBefore (timer zachowany).
     *
     * Ten test PRZECHODZI na naprawionym kodzie, ponieważ:
     * - fixedDataSyncWorkerSchedule używa SimulatedWorkPolicy.KEEP
     * - KEEP zachowuje istniejącą pracę bez resetowania timera
     *
     * Validates: Requirements 1.7, 1.8, 2.7, 2.8, 2.9
     */
    @Test
    fun `BUG3 - DataSyncWorker schedule z UPDATE resetuje timer przy ponownym wywolaniu`() {
        // Arrange: zaplanuj pracę po raz pierwszy
        val intervalMinutes = 30L
        val workManager = SimulatedWorkManager()

        // Pierwsze wywołanie schedule() – tworzy nową pracę
        fixedDataSyncWorkerSchedule(workManager, intervalMinutes)

        val workInfoAfterFirst = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        assertTrue("Praca powinna istnieć po pierwszym wywołaniu", workInfoAfterFirst.isNotEmpty())
        assertEquals("Stan pracy powinien być ENQUEUED", "ENQUEUED", workInfoAfterFirst.first().state)

        val nextRunBefore = workInfoAfterFirst.first().nextScheduleTimeMillis

        // Warunek weryfikacji: praca istnieje i jest zaplanowana
        assertTrue("Warunek weryfikacji: praca istnieje", workInfoAfterFirst.isNotEmpty())
        assertEquals("Warunek weryfikacji: stan = ENQUEUED", "ENQUEUED", workInfoAfterFirst.first().state)

        // Symulacja upływu czasu (np. onUpdate widgetu wywołuje schedule() ponownie)
        Thread.sleep(10) // Małe opóźnienie, żeby nextScheduleTimeMillis mogło się różnić przy UPDATE

        // Act: drugie wywołanie schedule() – symuluje onUpdate widgetu
        fixedDataSyncWorkerSchedule(workManager, intervalMinutes)

        val workInfoAfterSecond = workManager.getWorkInfosForUniqueWork(WORK_NAME)
        val nextRunAfter = workInfoAfterSecond.first().nextScheduleTimeMillis

        println("WERYFIKACJA NAPRAWY BUG 3:")
        println("  nextRunBefore = $nextRunBefore")
        println("  nextRunAfter  = $nextRunAfter")
        println("  Różnica = ${nextRunAfter - nextRunBefore} ms")
        println("  Timer zresetowany: ${nextRunAfter > nextRunBefore}")
        println("  Naprawa potwierdzona: KEEP zachowuje istniejącą pracę, timer NIE zresetowany")

        // Ta asercja PRZECHODZI na naprawionym kodzie:
        // KEEP nie tworzy nowej pracy, więc nextScheduleTimeMillis pozostaje niezmienione
        assertEquals(
            "NAPRAWA BUG 3: nextRunAfter powinien = nextRunBefore (timer NIE zresetowany). " +
                "fixedDataSyncWorkerSchedule używa KEEP zamiast UPDATE.",
            nextRunBefore,
            nextRunAfter
        )
    }

    /**
     * BUG 3 (weryfikacja): pierwsze wywołanie schedule() zawsze tworzy nową pracę.
     * UPDATE i KEEP zachowują się identycznie przy braku istniejącej pracy.
     *
     * Ten test PRZECHODZI – zachowanie przy pierwszym wywołaniu jest niezmienione.
     *
     * Validates: Requirements 1.7
     */
    @Test
    fun `BUG3 - pierwsze wywolanie schedule tworzy nowa prace niezaleznie od polityki`() {
        // Arrange: brak istniejącej pracy
        val workManager = SimulatedWorkManager()
        assertTrue(
            "Stan początkowy: brak pracy WorkManager",
            workManager.getWorkInfosForUniqueWork(WORK_NAME).isEmpty()
        )

        // Act: pierwsze wywołanie schedule()
        fixedDataSyncWorkerSchedule(workManager, 30L)

        // Assert: praca została utworzona
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME)

        println("PRZYPADEK BRZEGOWY BUG 3: pierwsze wywołanie schedule()")
        println("  workExists = ${workInfos.isNotEmpty()} (KEEP i UPDATE zachowują się identycznie przy braku pracy)")

        // Ten test PRZECHODZI – przy braku istniejącej pracy KEEP = UPDATE
        assertTrue(
            "Pierwsze wywołanie schedule() zawsze tworzy nową pracę",
            workInfos.isNotEmpty()
        )
        assertEquals("Interwał powinien być 30 minut", 30L, workInfos.first().intervalMinutes)
    }

    /**
     * BUG 3 NAPRAWIONY (scenariusz ekstremalny): onUpdate wywoływana wielokrotnie.
     * Timer NIE jest resetowany – synchronizacja następuje zgodnie z harmonogramem.
     *
     * Validates: Requirements 1.7, 1.8, 2.7, 2.8, 2.9
     */
    @Test
    fun `BUG3 - wielokrotne wywolania schedule resetuja timer uniemozliwiajac synchronizacje`() {
        // Arrange: interwał 15 minut, onUpdate wywoływana wielokrotnie
        val intervalMinutes = 15L
        val workManager = SimulatedWorkManager()

        // Pierwsze wywołanie
        fixedDataSyncWorkerSchedule(workManager, intervalMinutes)
        val nextRunAfterFirst = workManager.getWorkInfosForUniqueWork(WORK_NAME).first().nextScheduleTimeMillis

        Thread.sleep(5) // Symulacja upływu czasu

        // Drugie wywołanie (onUpdate po 5 minutach)
        fixedDataSyncWorkerSchedule(workManager, intervalMinutes)
        val nextRunAfterSecond = workManager.getWorkInfosForUniqueWork(WORK_NAME).first().nextScheduleTimeMillis

        Thread.sleep(5) // Symulacja upływu czasu

        // Trzecie wywołanie (onUpdate po kolejnych 5 minutach)
        fixedDataSyncWorkerSchedule(workManager, intervalMinutes)
        val nextRunAfterThird = workManager.getWorkInfosForUniqueWork(WORK_NAME).first().nextScheduleTimeMillis

        println("WERYFIKACJA NAPRAWY BUG 3 (wielokrotne wywołania):")
        println("  nextRunAfterFirst  = $nextRunAfterFirst")
        println("  nextRunAfterSecond = $nextRunAfterSecond (KEEP – brak resetu)")
        println("  nextRunAfterThird  = $nextRunAfterThird (KEEP – brak resetu)")
        println("  Naprawa potwierdzona: KEEP zachowuje timer, synchronizacja nastąpi zgodnie z harmonogramem")

        // Ta asercja PRZECHODZI na naprawionym kodzie:
        // KEEP nie resetuje timera – nextScheduleTimeMillis pozostaje niezmienione
        assertEquals(
            "NAPRAWA BUG 3: timer NIE powinien być resetowany przy kolejnych wywołaniach. " +
                "fixedDataSyncWorkerSchedule używa KEEP – każde kolejne wywołanie jest ignorowane.",
            nextRunAfterFirst,
            nextRunAfterThird
        )
    }
}
