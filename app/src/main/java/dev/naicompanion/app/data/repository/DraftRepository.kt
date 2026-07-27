package dev.naicompanion.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.naicompanion.app.core.prompt.TagEntry
import dev.naicompanion.app.data.backup.TagEntryDto
import dev.naicompanion.app.data.backup.toDto
import dev.naicompanion.app.data.backup.toTagEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.draftDataStore: DataStore<Preferences> by preferencesDataStore("draft")

/**
 * Persists the combo currently being edited so the builder survives process death. Kept out of
 * Room because it is a single small blob that is rewritten on every keystroke-level edit.
 */
class DraftRepository(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    val draft: Flow<List<TagEntry>> = context.draftDataStore.data.map { prefs ->
        val raw = prefs[KEY_TAGS] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<TagEntryDto>>(raw).map { it.toTagEntry() } }
            .getOrDefault(emptyList())
    }

    val draftName: Flow<String> = context.draftDataStore.data.map { it[KEY_NAME].orEmpty() }

    suspend fun save(entries: List<TagEntry>) {
        val encoded = json.encodeToString(entries.map { it.toDto() })
        context.draftDataStore.edit { it[KEY_TAGS] = encoded }
    }

    suspend fun saveName(name: String) {
        context.draftDataStore.edit { it[KEY_NAME] = name }
    }

    suspend fun clear() {
        context.draftDataStore.edit {
            it.remove(KEY_TAGS)
            it.remove(KEY_NAME)
        }
    }

    private companion object {
        val KEY_TAGS = stringPreferencesKey("draft_tags")
        val KEY_NAME = stringPreferencesKey("draft_name")
    }
}
