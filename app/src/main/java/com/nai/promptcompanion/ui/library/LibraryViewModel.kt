package com.nai.promptcompanion.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nai.promptcompanion.NaiCompanionApp
import com.nai.promptcompanion.data.backup.ImportResult
import com.nai.promptcompanion.data.backup.decodeBackup
import com.nai.promptcompanion.data.backup.encodeBackup
import com.nai.promptcompanion.data.repo.buildBackupFromRepos
import com.nai.promptcompanion.data.user.ComboEntity
import com.nai.promptcompanion.data.user.PromptEntity
import com.nai.promptcompanion.novelai.NovelaiSyntax
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as NaiCompanionApp).container
    private val prompts = container.prompts
    private val combos = container.combos
    private val builder = container.builderState
    private val backupIo = container.backupIo

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** Emitted after "load into builder" so the UI can switch tabs. */
    private val _goToBuilder = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val goToBuilder: SharedFlow<Unit> = _goToBuilder.asSharedFlow()

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query

    val promptList: StateFlow<List<PromptEntity>> = query
        .flatMapLatest { prompts.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val comboList: StateFlow<List<ComboEntity>> = combos.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(q: String) {
        query.value = q
    }

    fun addPrompt(title: String, body: String) {
        if (body.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            prompts.save(
                PromptEntity(
                    title = title.trim().ifEmpty { "Prompt" },
                    body = body.trim(),
                    createdAt = now,
                    updatedAt = now,
                )
            )
            _events.emit("Prompt saved")
        }
    }

    fun deletePrompt(prompt: PromptEntity) {
        viewModelScope.launch {
            prompts.delete(prompt)
            _events.emit("Prompt deleted")
        }
    }

    fun togglePromptFavorite(prompt: PromptEntity) {
        viewModelScope.launch { prompts.toggleFavorite(prompt) }
    }

    fun deleteCombo(combo: ComboEntity) {
        viewModelScope.launch {
            combos.delete(combo)
            _events.emit("Combo deleted")
        }
    }

    fun toggleComboFavorite(combo: ComboEntity) {
        viewModelScope.launch { combos.toggleFavorite(combo) }
    }

    fun loadPromptIntoBuilder(prompt: PromptEntity) {
        val parsed = NovelaiSyntax.parse(prompt.body)
        if (parsed.isEmpty()) {
            _events.tryEmit("Could not parse prompt into tags")
            return
        }
        builder.setAll(parsed)
        _events.tryEmit("Loaded \"${prompt.title}\" into builder")
        _goToBuilder.tryEmit(Unit)
    }

    fun loadComboIntoBuilder(combo: ComboEntity) {
        val entries = combos.decodeEntries(combo)
        if (entries.isEmpty()) {
            _events.tryEmit("Combo is empty")
            return
        }
        builder.setAll(entries)
        _events.tryEmit("Loaded \"${combo.name}\" into builder")
        _goToBuilder.tryEmit(Unit)
    }

    fun comboPreview(combo: ComboEntity): String =
        NovelaiSyntax.render(combos.decodeEntries(combo))

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val backup = buildBackupFromRepos(prompts, combos, container.favorites)
            val ok = backupIo.write(uri, encodeBackup(backup))
            _events.emit(if (ok) "Backup exported" else "Export failed")
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val raw = backupIo.read(uri)
            if (raw == null) {
                _events.emit("Import failed: unreadable file")
                return@launch
            }
            runCatching { decodeBackup(raw) }
                .onSuccess { backup ->
                    val now = System.currentTimeMillis()
                    backup.prompts.forEach {
                        prompts.save(
                            PromptEntity(
                                title = it.title,
                                body = it.body,
                                favorite = it.favorite,
                                createdAt = it.createdAt.takeIf { ts -> ts > 0 } ?: now,
                                updatedAt = it.updatedAt.takeIf { ts -> ts > 0 } ?: now,
                            )
                        )
                    }
                    backup.combos.forEach {
                        combos.save(
                            ComboEntity(
                                name = it.name,
                                entriesJson = it.entriesJson,
                                favorite = it.favorite,
                                createdAt = it.createdAt.takeIf { ts -> ts > 0 } ?: now,
                                updatedAt = it.updatedAt.takeIf { ts -> ts > 0 } ?: now,
                            )
                        )
                    }
                    container.favorites.addAll(
                        backup.favoriteTags.map { it.tag to it.addedAt }
                    )
                    val result = ImportResult(
                        prompts = backup.prompts.size,
                        combos = backup.combos.size,
                        favoriteTags = backup.favoriteTags.size,
                    )
                    _events.emit(
                        "Imported ${result.prompts} prompts, ${result.combos} combos, ${result.favoriteTags} favorite tags"
                    )
                }
                .onFailure {
                    _events.emit("Import failed: not a valid backup file")
                }
        }
    }
}
