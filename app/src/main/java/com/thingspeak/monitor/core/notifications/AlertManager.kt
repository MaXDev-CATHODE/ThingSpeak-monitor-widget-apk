package com.thingspeak.monitor.core.notifications

import com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold

/**
 * Interface for managing and firing alert notifications when thresholds are violated.
 */
interface AlertManager {

    /**
     * Fires a notification regarding the given threshold violations for a channel.
     * Use when background sync detects anomalies.
     *
     * @param channelId the ID of the channel.
     * @param violations list of violated thresholds.
     */
    fun fireAlert(channelId: Long, violations: List<AlertThreshold>)
}
