package com.thingspeak.monitor.core.notifications

import com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold

/**
 * Interface for managing and firing alert notifications when thresholds are violated.
 */
interface AlertManager {

    /**
     * Fires a notification regarding the given threshold violations for a channel.
     *
     * @param channelId the ID of the channel.
     * @param violations list of violated thresholds.
     */
    fun fireAlert(channelId: Long, violations: List<AlertThreshold>)

    /**
     * Fires a notification regarding the given rule violations for a channel or widget.
     *
     * @param channelId the ID of the channel.
     * @param violations list of violated rules.
     * @param fieldNames map of field numbers to names (optional).
     */
    fun fireRuleAlert(channelId: Long, violations: List<com.thingspeak.monitor.feature.channel.domain.model.AlertRule>, fieldNames: Map<Int, String> = emptyMap())
}
