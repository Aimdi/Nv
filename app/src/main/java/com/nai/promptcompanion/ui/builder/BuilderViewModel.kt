package com.nai.promptcompanion.ui.builder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nai.promptcompanion.NaiCompanionApp
import com.nai.promptcompanion.data.catalog.ArtistTagEntity
import com.nai.promptcompanion.data.user.ComboEntity
import com.nai.promptcompanion.novelai.TagEntry
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class BuilderViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as NaiCompanionApp).container
    private val builder = container.builderState
    private val catalog = container.catalog
    private val combos = container.combos

    val entries: StateFlow<List<TagEntry>> = builder.entries
    val rendered: StateFlow<String> = builder.rendered

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _suggestions = MutableStateFlow<List<ArtistTagEntity>>(emptyList())
    val suggestions: StateFlow<List<ArtistTagEntity>> = _suggestions

    var editingIndex = MutableStateFlow(-1)
        private set

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            query.debounce(200).collectLatest { q ->
                _suggestions.value = if (q.isBlank()) emptyList() else catalog.suggest(q)
            }
        }
    }

    private val query = MutableStateFlow("")

    fun onQueryChange(q: String) {
        query.value = q
    }

    fun addTag(tag: String) = builder.addTag(tag)
    fun addParsedPrompt(text: String) = builder.addParsedPrompt(text)
    fun moveEntry(from: Int, to: Int) = builder.move(from, to)
    fun updateEntry(index: Int, entry: TagEntry) = builder.updateAt(index, entry)
    fun removeEntry(index: Int) = builder.removeAt(index)
    fun openEditor(index: Int) { editingIndex.value = index }
    fun closeEditor() { editingIndex.value = -1 }

    fun clearAll() {
        builder.clear()
        _events.tryEmit("Cleared all tags")
    }

    fun saveCombo(name: String) {
        val list = entries.value
        if (list.isEmpty()) {
            _events.tryEmit("Nothing to save")
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            combos.save(
                ComboEntity(
                    name = name.trim().ifEmpty { "Combo" },
                    entriesJson = combos.encodeEntries(list),
                    createdAt = now,
                    updatedAt = now,
                )
            )
            _events.emit("Saved combo \"$name\"")
        }
    }

    fun notifyCopied() {
        _events.tryEmit("Copied to clipboard — paste into NovelAI")
    }

    fun notifyParsed() {
        _events.tryEmit("Parsed clipboard into tags")
    }

    fun notifyClipboardEmpty() {
        _events.tryEmit("Clipboard is empty")
    }
}
