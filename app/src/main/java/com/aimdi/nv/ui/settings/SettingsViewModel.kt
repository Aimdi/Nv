package com.aimdi.nv.ui.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.nv.NaiComposerApp
import com.aimdi.nv.data.preview.PreviewPackManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val backup = NaiComposerApp.instance.backup
    private val previewPack = NaiComposerApp.instance.previewPack
    private val artists = NaiComposerApp.instance.artists

    var packState by mutableStateOf<PreviewPackManager.State>(PreviewPackManager.State.NotInstalled)
        private set
    var packUrl by mutableStateOf(PreviewPackManager.DEFAULT_PACK_URL)
        private set
    var artistCount by mutableStateOf(0)
        private set
    var lastMessage by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            previewPack.state.collectLatest { packState = it }
        }
        viewModelScope.launch {
            previewPack.refresh()
            artistCount = artists.count()
        }
    }

    fun onPackUrlChange(value: String) {
        packUrl = value
    }

    fun downloadPack() {
        viewModelScope.launch {
            previewPack.downloadAndInstall(packUrl.trim())
        }
    }

    fun uninstallPack() {
        viewModelScope.launch { previewPack.uninstall() }
    }

    fun exportTo(uri: Uri, openOutput: (Uri) -> java.io.OutputStream?) {
        viewModelScope.launch {
            try {
                openOutput(uri)?.use { backup.exportTo(it) }
                lastMessage = "Export complete"
            } catch (e: Exception) {
                lastMessage = "Export failed: ${e.message}"
            }
        }
    }

    fun importFrom(uri: Uri, openInput: (Uri) -> java.io.InputStream?, merge: Boolean) {
        viewModelScope.launch {
            try {
                val result = openInput(uri)?.use { backup.importFrom(it, merge) }
                lastMessage = if (result?.ok == true) {
                    "Imported ${result.promptsImported} prompts, ${result.combosImported} combos"
                } else {
                    result?.error ?: "Import failed"
                }
            } catch (e: Exception) {
                lastMessage = "Import failed: ${e.message}"
            }
        }
    }

    fun consumeMessage(): String? {
        val m = lastMessage
        lastMessage = null
        return m
    }
}
