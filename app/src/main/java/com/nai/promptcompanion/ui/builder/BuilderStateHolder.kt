package com.nai.promptcompanion.ui.builder

import com.nai.promptcompanion.data.prefs.SettingsStore
import com.nai.promptcompanion.novelai.NovelaiSyntax
import com.nai.promptcompanion.novelai.TagEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Holds the in-progress combo. Shared between the Builder screen (editing),
 * the Tags browser ("add to combo") and the Library ("load into builder").
 * The draft survives process death via DataStore.
 */
class BuilderStateHolder(
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow<List<TagEntry>>(emptyList())
    val entries: StateFlow<List<TagEntry>> = _entries

    val rendered: StateFlow<String> = entries
        .map { NovelaiSyntax.render(it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), "")

    /**
     * Restores the draft, then starts persisting changes. The ordering matters:
     * auto-persist must not run before the restore, or the initial empty state
     * could clobber the saved draft.
     */
    fun restoreThenAutoPersist() {
        scope.launch {
            val raw = settings.builderDraftJson.first()
            if (raw != null) {
                runCatching {
                    json.decodeFromString<List<TagEntry>>(raw)
                }.onSuccess { _entries.value = TagEntry.ensureIds(it) }
            }
            startAutoPersist()
        }
    }

    @OptIn(FlowPreview::class)
    private fun startAutoPersist() {
        scope.launch {
            entries.debounce(400).collect { list ->
                settings.saveBuilderDraft(json.encodeToString(list))
            }
        }
    }

    fun addTag(tag: String) {
        val cleaned = tag.trim()
        if (cleaned.isEmpty()) return
        _entries.value = _entries.value + TagEntry(tag = cleaned).withId()
    }

    fun addParsedPrompt(text: String) {
        val parsed = NovelaiSyntax.parse(text)
        if (parsed.isNotEmpty()) {
            _entries.value = TagEntry.ensureIds(_entries.value + parsed)
        }
    }

    fun setAll(newEntries: List<TagEntry>) {
        _entries.value = TagEntry.ensureIds(newEntries)
    }

    fun updateAt(index: Int, entry: TagEntry) {
        _entries.value = _entries.value.mapIndexed { i, e -> if (i == index) entry else e }
    }

    fun removeAt(index: Int) {
        _entries.value = _entries.value.filterIndexed { i, _ -> i != index }
    }

    fun move(from: Int, to: Int) {
        val list = _entries.value.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        _entries.value = list
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
