package dev.naicompanion.app.ui.packs

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.naicompanion.app.data.packs.InstalledPack
import dev.naicompanion.app.data.packs.PackDescriptor
import dev.naicompanion.app.data.packs.PackInstallState
import dev.naicompanion.app.data.packs.PackRepository
import dev.naicompanion.app.data.settings.SettingsRepository
import dev.naicompanion.app.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PacksUiState(
    val available: List<PackDescriptor> = emptyList(),
    val installed: List<InstalledPack> = emptyList(),
    val installState: PackInstallState = PackInstallState.Idle,
    val manifestUrl: String = "",
    val loadingManifest: Boolean = false,
    val manifestError: String? = null,
) {
    fun isInstalled(descriptor: PackDescriptor): InstalledPack? =
        installed.firstOrNull { it.id == descriptor.id }
}

class PacksViewModel(
    private val packRepository: PackRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val available = MutableStateFlow<List<PackDescriptor>>(emptyList())
    private val loadingManifest = MutableStateFlow(false)
    private val manifestError = MutableStateFlow<String?>(null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var installJob: Job? = null

    val uiState: StateFlow<PacksUiState> = combine(
        available,
        packRepository.installed,
        packRepository.installState,
        settingsRepository.settings,
        combine(loadingManifest, manifestError) { loading, error -> loading to error },
    ) { availablePacks, installed, installState, settings, status ->
        PacksUiState(
            available = availablePacks,
            installed = installed,
            installState = installState,
            manifestUrl = settings.packManifestUrl,
            loadingManifest = status.first,
            manifestError = status.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PacksUiState())

    init {
        refreshManifest()
    }

    fun refreshManifest() {
        viewModelScope.launch {
            loadingManifest.value = true
            manifestError.value = null
            val url = settingsRepository.settings.first().packManifestUrl
            packRepository.fetchManifest(url)
                .onSuccess { available.value = it.packs }
                .onFailure { manifestError.value = it.message ?: "Could not load the pack list" }
            loadingManifest.value = false
        }
    }

    fun install(descriptor: PackDescriptor) {
        if (installJob?.isActive == true) {
            _messages.tryEmit("Another pack is already installing")
            return
        }
        installJob = viewModelScope.launch {
            packRepository.install(descriptor)
                .onSuccess { _messages.emit("Installed ${it.name}") }
                .onFailure { _messages.emit(it.message ?: "Install failed") }
        }
    }

    fun cancelInstall() {
        installJob?.cancel()
        installJob = null
        packRepository.clearInstallState()
        _messages.tryEmit("Install cancelled")
    }

    fun importFromFile(uri: Uri) {
        installJob = viewModelScope.launch {
            packRepository.installFromUri(uri)
                .onSuccess { _messages.emit("Imported ${it.name} (${it.imageCount} images)") }
                .onFailure { _messages.emit(it.message ?: "Import failed") }
        }
    }

    fun remove(pack: InstalledPack) {
        viewModelScope.launch {
            packRepository.remove(pack.id)
            _messages.emit("Removed ${pack.name}")
        }
    }

    fun setManifestUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setPackManifestUrl(url)
            refreshManifest()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PacksViewModel(
                    packRepository = container.packRepository,
                    settingsRepository = container.settingsRepository,
                )
            }
        }
    }
}
