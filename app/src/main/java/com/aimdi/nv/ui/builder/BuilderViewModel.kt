package com.aimdi.nv.ui.builder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.nv.NaiComposerApp
import com.aimdi.nv.domain.NovelAiPromptRenderer
import com.aimdi.nv.domain.TagEntry
import kotlinx.coroutines.launch
import java.util.UUID

class BuilderViewModel : ViewModel() {
    private val combos get() = NaiComposerApp.instance.combos
    private val prompts get() = NaiComposerApp.instance.prompts

    var entries by mutableStateOf(listOf<TagEntry>())
        private set

    var draftInput by mutableStateOf("")
        private set

    var editingId by mutableStateOf<String?>(null)
        private set

    var useArtistPrefix by mutableStateOf(true)
        private set

    var useNumericWeights by mutableStateOf(false)
        private set

    val renderedPrompt: String
        get() = NovelAiPromptRenderer.render(entries)

    fun onDraftChange(value: String) {
        draftInput = value
    }

    fun toggleArtistPrefix() {
        useArtistPrefix = !useArtistPrefix
    }

    fun toggleNumericMode() {
        useNumericWeights = !useNumericWeights
    }

    fun addTag(raw: String = draftInput, forceArtistPrefix: Boolean = useArtistPrefix) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        // Support comma-separated paste
        val parts = tag.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val newEntries = parts.map { part ->
            TagEntry(
                id = UUID.randomUUID().toString(),
                tag = part,
                forceArtistPrefix = forceArtistPrefix && !part.contains(':'),
            )
        }
        entries = entries + newEntries
        draftInput = ""
    }

    fun remove(id: String) {
        entries = entries.filterNot { it.id == id }
        if (editingId == id) editingId = null
    }

    fun move(from: Int, to: Int) {
        if (from == to) return
        if (from !in entries.indices || to !in entries.indices) return
        val list = entries.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        entries = list
    }

    fun setEditing(id: String?) {
        editingId = id
    }

    fun updateEntry(id: String, transform: (TagEntry) -> TagEntry) {
        entries = entries.map { if (it.id == id) transform(it) else it }
    }

    fun bumpBracket(id: String, delta: Int) {
        updateEntry(id) { e ->
            val next = (e.bracketCount + delta).coerceIn(-5, 5)
            e.copy(bracketCount = next, numericWeight = if (useNumericWeights) e.numericWeight else null)
        }
    }

    fun setNumericWeight(id: String, weight: Float?) {
        updateEntry(id) { it.copy(numericWeight = weight) }
    }

    fun toggleEnabled(id: String) {
        updateEntry(id) { it.copy(enabled = !it.enabled) }
    }

    fun clear() {
        entries = emptyList()
        editingId = null
    }

    fun replaceEntries(newEntries: List<TagEntry>) {
        entries = newEntries.map { it.copy(id = UUID.randomUUID().toString()) }
        editingId = null
    }

    /**
     * Load a free-text prompt into the builder by splitting on commas.
     * Weighting tokens are preserved as literal tags for now.
     */
    fun loadPromptText(text: String) {
        val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        entries = parts.map {
            TagEntry(
                id = UUID.randomUUID().toString(),
                tag = it,
                forceArtistPrefix = false,
            )
        }
        editingId = null
    }

    fun saveAsCombo(title: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                combos.save(title, entries)
                onDone(true)
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    fun saveAsPrompt(title: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                prompts.save(title, renderedPrompt)
                onDone(true)
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }
}
