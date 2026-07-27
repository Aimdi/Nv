package com.nai.promptcompanion.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val BUILDER_DRAFT = stringPreferencesKey("builder_draft_json")
        val IMAGEPACK_URL = stringPreferencesKey("imagepack_manifest_url")
        val IMAGEPACK_VERSION = intPreferencesKey("imagepack_installed_version")
        val SEEDED_CATALOG_COUNT = intPreferencesKey("seeded_catalog_count")
    }

    val builderDraftJson: Flow<String?> =
        context.dataStore.data.map { it[Keys.BUILDER_DRAFT] }

    suspend fun saveBuilderDraft(json: String) {
        context.dataStore.edit { it[Keys.BUILDER_DRAFT] = json }
    }

    /** Runtime override for the pack manifest URL; falls back to the baked-in default. */
    val imagePackUrl: Flow<String?> =
        context.dataStore.data.map { it[Keys.IMAGEPACK_URL]?.takeIf(String::isNotBlank) }

    suspend fun setImagePackUrl(url: String) {
        context.dataStore.edit { it[Keys.IMAGEPACK_URL] = url.trim() }
    }

    /** 0 (or absent) = pack not installed. */
    val installedPackVersion: Flow<Int> =
        context.dataStore.data.map { it[Keys.IMAGEPACK_VERSION] ?: 0 }

    suspend fun setInstalledPackVersion(version: Int) {
        context.dataStore.edit { it[Keys.IMAGEPACK_VERSION] = version }
    }

    val seededCatalogCount: Flow<Int> =
        context.dataStore.data.map { it[Keys.SEEDED_CATALOG_COUNT] ?: 0 }

    suspend fun setSeededCatalogCount(count: Int) {
        context.dataStore.edit { it[Keys.SEEDED_CATALOG_COUNT] = count }
    }
}
