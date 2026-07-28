package dev.naicompanion.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * App-scoped DataStore instances.
 *
 * The repositories take a [DataStore] rather than a [Context] so that tests can supply an
 * isolated store; the property delegates below are process-global and would otherwise leak state
 * between tests.
 */
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")
private val Context.draftStore: DataStore<Preferences> by preferencesDataStore("draft")

fun settingsDataStore(context: Context): DataStore<Preferences> = context.settingsStore

fun draftDataStore(context: Context): DataStore<Preferences> = context.draftStore
