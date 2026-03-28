package com.thingspeak.monitor.feature.channel.domain.usecase

import com.thingspeak.monitor.feature.channel.domain.model.AlertRule
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import com.thingspeak.monitor.core.utils.safeToDouble
import javax.inject.Inject

/**
 * Checks if values in the latest feed entry exceed advanced alert rules.
 *
 * Returns a list of violated [AlertRule]s. Empty list = no alarms.
 */
class CheckAlertRulesUseCase @Inject constructor() {

    /**
     * @param entry Latest feed entry
     * @param rules List of defined rules (global or widget-specific)
     * @return      List of rules that were violated
     */
    operator fun invoke(
        entry: FeedEntry,
        rules: List<AlertRule>,
    ): List<AlertRule> {
        return rules.filter { rule ->
            if (!rule.isEnabled) return@filter false
            
            val value = entry.fields[rule.fieldNumber].safeToDouble()
                ?: return@filter false
                
            when (rule.condition) {
                "GREATER_THAN" -> value > rule.thresholdValue
                "LESS_THAN" -> value < rule.thresholdValue
                "EQUAL" -> value == rule.thresholdValue
                else -> false
            }
        }
    }
}
