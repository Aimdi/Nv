package dev.naicompanion.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.naicompanion.app.core.prompt.NovelAiModel
import dev.naicompanion.app.data.backup.BackupRepository
import dev.naicompanion.app.data.backup.ImportMode
import dev.naicompanion.app.data.repository.CatalogRepository
import dev.naicompanion.app.data.repository.CatalogStatus
import dev.naicompanion.app.data.settings.AppSettings
import dev.naicompanion.app.data.settings.SettingsRepository
import dev.naicompanion.app.di.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val catalogRepository: CatalogRepository,
    val appVersion: String,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val catalogStatus: StateFlow<CatalogStatus> = catalogRepository.status

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch { catalogRepository.refresh() }
    }

    fun setModel(model: NovelAiModel) = launchSetting { settingsRepository.setModel(model) }
    fun setUnderscoresToSpaces(value: Boolean) =
        launchSetting { settingsRepository.setUnderscoresToSpaces(value) }

    fun setArtistPrefix(value: Boolean) =
        launchSetting { settingsRepository.setArtistPrefix(value) }

    fun setMultilineSeparator(value: Boolean) =
        launchSetting { settingsRepository.setMultilineSeparator(value) }

    fun setBrowserColumns(value: Int) =
        launchSetting { settingsRepository.setBrowserColumns(value) }

    fun setCopyOpensNovelAi(value: Boolean) =
        launchSetting { settingsRepository.setCopyOpensNovelAi(value) }

    fun setOnlineTagSearch(value: Boolean) =
        launchSetting { settingsRepository.setOnlineTagSearch(value) }

    fun setOnlineArtistPreviews(value: Boolean) =
        launchSetting { settingsRepository.setOnlineArtistPreviews(value) }

    fun setAllowNsfwTags(value: Boolean) =
        launchSetting { settingsRepository.setAllowNsfwTags(value) }

    fun suggestedBackupFileName(): String = backupRepository.suggestedFileName()

    fun export(uri: Uri) {
        viewModelScope.launch {
            val current = settingsRepository.settings.first()
            backupRepository.exportTo(uri, current)
                .onSuccess { summary ->
                    _messages.emit(
                        "Exported ${summary.prompts} prompts, ${summary.combos} combos, " +
                            "${summary.favoriteTags} favorites",
                    )
                }
                .onFailure { _messages.emit(it.message ?: "Export failed") }
        }
    }

    fun import(uri: Uri, mode: ImportMode) {
        viewModelScope.launch {
            backupRepository.importFrom(uri, mode)
                .onSuccess { summary ->
                    _messages.emit(
                        "Imported ${summary.prompts} prompts, ${summary.combos} combos, " +
                            "${summary.favoriteTags} favorites",
                    )
                }
                .onFailure { _messages.emit(it.message ?: "Import failed") }
        }
    }

    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    backupRepository = container.backupRepository,
                    catalogRepository = container.catalogRepository,
                    appVersion = dev.naicompanion.app.BuildConfig.VERSION_NAME,
                )
            }
        }
    }
}
