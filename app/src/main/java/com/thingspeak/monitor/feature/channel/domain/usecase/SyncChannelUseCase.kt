package com.thingspeak.monitor.feature.channel.domain.usecase

import com.thingspeak.monitor.core.di.IoDispatcher
import com.thingspeak.monitor.core.notifications.AlertManager
import com.thingspeak.monitor.feature.alert.domain.model.FiredAlert
import com.thingspeak.monitor.feature.channel.domain.model.*
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import com.thingspeak.monitor.feature.channel.domain.usecase.CheckAlertThresholdsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Orchestrates channel synchronization, alert evaluation, and notification triggering.
 */
class SyncChannelUseCase @Inject constructor(
    private val repository: ChannelRepository,
    private val checkThresholds: CheckAlertThresholdsUseCase,
    private val alertManager: AlertManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    data class Result(
        val hasNewData: Boolean,
        val alertStateChanged: Boolean,
        val error: Throwable? = null
    )

    suspend operator fun invoke(channel: Channel): Result = withContext(ioDispatcher) {
        try {
            // 1. Refresh data from API
            val timespanHours = channel.chartProcessingPeriod.takeIf { it > 0 } ?: 24
            val resultsCount = (timespanHours * 60).coerceAtMost(500)
            repository.refreshFeed(channel.id, channel.apiKey, resultsCount)

            // 2. Load latest data and check for updates
            val entries = repository.observeFeed(channel.id).first()
            if (entries.isEmpty()) return@withContext Result(false, false)

            val latestEntry = entries.first()
            val lastProcessedId = channel.lastProcessedEntryId
            
            // Find entries that haven't been processed for alerts yet
            val newEntries = entries.filter { it.entryId > lastProcessedId }.reversed() // Oldest first
            
            // If no new entries, we still check the latest one to allow alert persistence/clearing
            val entriesToCheck = if (newEntries.isNotEmpty()) newEntries else listOf(latestEntry)

            // 3. Load alert thresholds and advanced rules
            val thresholds = repository.getAlertsForChannel(channel.id).filter { it.isEnabled }
            val advancedRules = repository.getAlertRulesForChannel(channel.id).filter { it.isEnabled }
            
            val allFieldNumbers = (thresholds.map { it.fieldNumber } + advancedRules.map { it.fieldNumber }).distinct()
            val violationsToNotify = mutableListOf<AlertThreshold>()
            var anyAlertStateChanged = false

            // 4. Process each field for violations
            allFieldNumbers.forEach { fieldNum ->
                val fieldThresholds = thresholds.filter { it.fieldNumber == fieldNum }
                val fieldRules = advancedRules.filter { it.fieldNumber == fieldNum }

                // Use the latest entry that has data for this field
                val latestEntryForField = entries.firstOrNull { it.fields[fieldNum] != null } ?: latestEntry
                val fieldValue = latestEntryForField.fields[fieldNum]?.toDoubleOrNull()
                
                // Stale data heuristic: skip evaluation if the field hasn't updated in 5 entry cycles
                val entryAge = latestEntry.entryId - latestEntryForField.entryId
                val isStale = entryAge > 5

                if (isStale) {
                    val fired = repository.getFiredAlert(channel.id, fieldNum)
                    if (fired != null) {
                        repository.deleteFiredAlert(channel.id, fieldNum)
                        anyAlertStateChanged = true
                    }
                    return@forEach
                }

                val currentViolations = mutableSetOf<AlertThreshold>()
                
                // Evaluate legacy thresholds
                if (fieldThresholds.isNotEmpty()) {
                    currentViolations.addAll(checkThresholds(latestEntryForField, fieldThresholds))
                }

                // Evaluate advanced rules
                fieldRules.forEach { rule ->
                    val value = latestEntryForField.fields[rule.fieldNumber]?.toDoubleOrNull()
                    if (value != null) {
                        val isViolated = when (rule.condition) {
                            "GREATER_THAN" -> value > rule.thresholdValue
                            "LESS_THAN" -> value < rule.thresholdValue
                            else -> false
                        }
                        if (isViolated) {
                            currentViolations.add(AlertThreshold(
                                channelId = channel.id,
                                fieldNumber = rule.fieldNumber,
                                fieldName = channel.fieldNames[rule.fieldNumber] ?: "Field ${rule.fieldNumber}",
                                minValue = if (rule.condition == "LESS_THAN") rule.thresholdValue else null,
                                maxValue = if (rule.condition == "GREATER_THAN") rule.thresholdValue else null
                            ))
                        }
                    }
                }

                val fired = repository.getFiredAlert(channel.id, fieldNum)
                val currentSignature = currentViolations
                    .sortedBy { "${it.minValue}:${it.maxValue}" }
                    .joinToString("|") { "${it.minValue}:${it.maxValue}" }

                if (currentViolations.isNotEmpty()) {
                    if (fired == null || fired.violationSignature != currentSignature) {
                        violationsToNotify.addAll(currentViolations)
                        repository.saveFiredAlert(FiredAlert(
                            channelId = channel.id,
                            fieldNumber = fieldNum,
                            lastFiredEntryId = latestEntry.entryId,
                            lastFiredTimestamp = System.currentTimeMillis(),
                            violationSignature = currentSignature
                        ))
                        anyAlertStateChanged = true
                    }
                } else if (fired != null) {
                    repository.deleteFiredAlert(channel.id, fieldNum)
                    anyAlertStateChanged = true
                }
            }

            // 5. Fire notifications
            if (violationsToNotify.isNotEmpty()) {
                alertManager.fireAlert(channel.id, violationsToNotify)
            }

            // 6. Update processing state
            if (newEntries.isNotEmpty()) {
                repository.updateChannel(channel.copy(
                    lastProcessedEntryId = newEntries.last().entryId
                ))
            }

            Result(
                hasNewData = newEntries.isNotEmpty(),
                alertStateChanged = anyAlertStateChanged
            )
        } catch (e: Exception) {
            Result(false, false, e)
        }
    }
}
