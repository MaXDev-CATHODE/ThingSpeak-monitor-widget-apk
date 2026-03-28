package com.thingspeak.monitor.feature.channel.domain.usecase

import com.thingspeak.monitor.feature.channel.domain.model.AlertRule
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckAlertRulesUseCaseTest {

    private val useCase = CheckAlertRulesUseCase()

    @Test
    fun `should detect GREATER_THAN violation`() {
        val entry = FeedEntry(1L, "2024-01-01T00:00:00Z", mapOf(1 to "25.5"))
        val rules = listOf(
            AlertRule(id = 1, channelId = 1, fieldNumber = 1, thresholdValue = 20.0, condition = "GREATER_THAN", isEnabled = true)
        )

        val violations = useCase(entry, rules)

        assertEquals(1, violations.size)
        assertEquals("GREATER_THAN", violations[0].condition)
    }

    @Test
    fun `should NOT detect GREATER_THAN if value is lower`() {
        val entry = FeedEntry(1L, "2024-01-01T00:00:00Z", mapOf(1 to "15.0"))
        val rules = listOf(
            AlertRule(id = 1, channelId = 1, fieldNumber = 1, thresholdValue = 20.0, condition = "GREATER_THAN", isEnabled = true)
        )

        val violations = useCase(entry, rules)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun `should detect LESS_THAN violation`() {
        val entry = FeedEntry(1L, "2024-01-01T00:00:00Z", mapOf(1 to "5.0"))
        val rules = listOf(
            AlertRule(id = 1, channelId = 1, fieldNumber = 1, thresholdValue = 10.0, condition = "LESS_THAN", isEnabled = true)
        )

        val violations = useCase(entry, rules)

        assertEquals(1, violations.size)
        assertEquals("LESS_THAN", violations[0].condition)
    }

    @Test
    fun `should handle multiple fields and rules`() {
        val entry = FeedEntry(1L, "2024-01-01T00:00:00Z", mapOf(1 to "30.0", 2 to "5.0"))
        val rules = listOf(
            AlertRule(id = 1, channelId = 1, fieldNumber = 1, thresholdValue = 20.0, condition = "GREATER_THAN", isEnabled = true),
            AlertRule(id = 2, channelId = 1, fieldNumber = 2, thresholdValue = 10.0, condition = "LESS_THAN", isEnabled = true),
            AlertRule(id = 3, channelId = 1, fieldNumber = 3, thresholdValue = 50.0, condition = "GREATER_THAN", isEnabled = true)
        )

        val violations = useCase(entry, rules)

        assertEquals(2, violations.size)
        assertTrue(violations.any { it.fieldNumber == 1 })
        assertTrue(violations.any { it.fieldNumber == 2 })
    }

    @Test
    fun `should ignore disabled rules`() {
        val entry = FeedEntry(1L, "2024-01-01T00:00:00Z", mapOf(1 to "30.0"))
        val rules = listOf(
            AlertRule(id = 1, channelId = 1, fieldNumber = 1, thresholdValue = 20.0, condition = "GREATER_THAN", isEnabled = false)
        )

        val violations = useCase(entry, rules)

        assertTrue(violations.isEmpty())
    }
    
    @Test
    fun `should handle invalid numeric values gracefully`() {
        val entry = FeedEntry(1L, "2024-01-01T00:00:00Z", mapOf(1 to "INVALID"))
        val rules = listOf(
            AlertRule(id = 1, channelId = 1, fieldNumber = 1, thresholdValue = 20.0, condition = "GREATER_THAN", isEnabled = true)
        )

        val violations = useCase(entry, rules)

        assertTrue(violations.isEmpty())
    }
}
