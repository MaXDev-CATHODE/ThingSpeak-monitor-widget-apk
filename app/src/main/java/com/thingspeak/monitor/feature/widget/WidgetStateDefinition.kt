package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.glance.state.GlanceStateDefinition
import java.io.File

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import androidx.glance.state.PreferencesGlanceStateDefinition

/**
 * Standard GlanceStateDefinition for DataStore Preferences.
 * Using the built-in PreferencesGlanceStateDefinition is safer for multi-process
 * consistency as it doesn't try to manually cache instances in a way that breaks sync.
 */
object WidgetPreferencesStateDefinition : GlanceStateDefinition<Preferences> by PreferencesGlanceStateDefinition
