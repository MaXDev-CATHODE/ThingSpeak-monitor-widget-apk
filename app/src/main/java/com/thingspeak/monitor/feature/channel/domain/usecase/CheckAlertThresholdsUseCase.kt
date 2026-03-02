package com.thingspeak.monitor.feature.channel.domain.usecase

import com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import com.thingspeak.monitor.core.utils.safeToDouble
import javax.inject.Inject

/**
 * Checks if values in the latest feed entry exceed alert thresholds.
 *
 * Returns a list of exceeded [AlertThreshold]s. Empty list = no alarms.
 * Pure logic — no dependency on Android framework.
 */
class CheckAlertThresholdsUseCase @Inject constructor() {

    /**
     * @param entry      Latest feed entry
     * @param thresholds List of defined thresholds for this channel
     * @return           List of thresholds that were exceeded
     */
    operator fun invoke(
        entry: FeedEntry,
        thresholds: List<AlertThreshold>,
    ): List<AlertThreshold> {
        return thresholds.filter { threshold ->
            if (!threshold.isEnabled) return@filter false
            val value = entry.fields[threshold.fieldNumber].safeToDouble()
                ?: return@filter false
            val belowMin = threshold.minValue?.let { value < it } ?: false
            val aboveMax = threshold.maxValue?.let { value > it } ?: false
            belowMin || aboveMax
        }
    }
}
