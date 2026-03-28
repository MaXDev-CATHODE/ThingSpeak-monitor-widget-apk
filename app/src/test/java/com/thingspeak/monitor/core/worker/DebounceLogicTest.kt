package com.thingspeak.monitor.core.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Test logiczny mechanizmu debouncingu (Agent 3.1).
 * Symuluje zachowanie DataSyncWorker w zakresie śledzenia stanu alarmów.
 */
class DebounceLogicTest {

    data class MockAlertRule(val field: Int, val condition: String, val threshold: Double)
    data class MockFiredAlert(val field: Int, var signature: String)

    @Test
    fun `test debounce logic with signatures`() {
        val channelId = 12345L
        val fieldNum = 1
        val rules = listOf(MockAlertRule(fieldNum, "GREATER_THAN", 100.0))
        
        // Stan bazy danych (pusty na początku)
        var firedAlertInDb: MockFiredAlert? = null
        var notificationsSent = 0

        // SYMULACJA 1: Pierwsze naruszenie (Value = 105)
        println("Simulation 1: Value 105 (Violation starts)")
        val violations1 = rules.filter { 105.0 > it.threshold }
        val signature1 = violations1.joinToString("|") { "${it.condition}:${it.threshold}" }
        
        if (signature1.isNotEmpty()) {
            if (firedAlertInDb == null || firedAlertInDb?.signature != signature1) {
                notificationsSent++
                firedAlertInDb = MockFiredAlert(fieldNum, signature1)
                println("  -> Notification SENT (New Signature: $signature1)")
            }
        }
        
        assertEquals(1, notificationsSent)
        assertNotNull(firedAlertInDb)
        assertEquals("GREATER_THAN:100.0", firedAlertInDb?.signature)

        // SYMULACJA 2: Kontynuacja naruszenia (Value = 110)
        println("Simulation 2: Value 110 (Violation continues)")
        val violations2 = rules.filter { 110.0 > it.threshold }
        val signature2 = violations2.joinToString("|") { "${it.condition}:${it.threshold}" }

        if (signature2.isNotEmpty()) {
            if (firedAlertInDb == null || firedAlertInDb?.signature != signature2) {
                notificationsSent++
                firedAlertInDb = MockFiredAlert(fieldNum, signature2)
            } else {
                println("  -> Notification SKIPPED (Debounced - Signature match)")
            }
        }

        assertEquals(1, notificationsSent) // NADAL 1, nie powinno wzrosnąć!

        // SYMULACJA 3: Powrót do normy (Value = 90)
        println("Simulation 3: Value 90 (Back to normal)")
        val violations3 = rules.filter { 90.0 > it.threshold }
        val signature3 = violations3.joinToString("|") { "${it.condition}:${it.threshold}" }

        if (signature3.isEmpty()) {
            if (firedAlertInDb != null) {
                firedAlertInDb = null
                println("  -> State CLEARED in DB")
            }
        }

        assertNull(firedAlertInDb)
        assertEquals(1, notificationsSent)

        // SYMULACJA 4: Ponowne naruszenie (Value = 102)
        println("Simulation 4: Value 102 (Violation re-occurs)")
        val violations4 = rules.filter { 102.0 > it.threshold }
        val signature4 = violations4.joinToString("|") { "${it.condition}:${it.threshold}" }

        if (signature4.isNotEmpty()) {
            if (firedAlertInDb == null || firedAlertInDb?.signature != signature4) {
                notificationsSent++
                firedAlertInDb = MockFiredAlert(fieldNum, signature4)
                println("  -> Notification SENT (New state after reset)")
            }
        }

        assertEquals(2, notificationsSent) // Teraz powinno być 2!
    }
}
