package com.aimdi.nv.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aimdi.nv.BuildConfig
import com.aimdi.nv.data.preview.PreviewPackManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resolver = context.contentResolver

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportTo(uri) { resolver.openOutputStream(it) }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importFrom(uri, { resolver.openInputStream(it) }, merge = true)
        }
    }

    LaunchedEffect(viewModel.lastMessage) {
        viewModel.consumeMessage()?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            "NaiComposer ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Text("Backup", style = MaterialTheme.typography.titleLarge)
        Text(
            "Export / import prompts & combos as versioned JSON via the system file picker.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                exportLauncher.launch("naicomposer-backup.json")
            }) { Text("Export JSON") }
            FilledTonalButton(onClick = {
                importLauncher.launch(arrayOf("application/json", "text/*"))
            }) { Text("Import JSON") }
        }

        Spacer(Modifier.height(28.dp))
        Text("Preview pack", style = MaterialTheme.typography.titleLarge)
        Text(
            "Optional WebP thumbnail pack (~150–350 MB) downloaded into app-private storage. " +
                "Not bundled in the APK. Sources: NAI v3 / Illustrious — label shown per artist.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = viewModel.packUrl,
            onValueChange = viewModel::onPackUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pack URL (zip)") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))

        when (val state = viewModel.packState) {
            is PreviewPackManager.State.NotInstalled -> {
                Text("Status: not installed", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = viewModel::downloadPack) { Text("Download pack") }
            }
            is PreviewPackManager.State.Installed -> {
                Text("Status: installed", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = viewModel::uninstallPack) { Text("Remove pack") }
            }
            is PreviewPackManager.State.Downloading -> {
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is PreviewPackManager.State.Failed -> {
                Text("Failed: ${state.message}", color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::downloadPack) { Text("Retry") }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Catalog", style = MaterialTheme.typography.titleLarge)
        Text(
            "${viewModel.artistCount} artists seeded from deus-ex-machina/novelai-anime-v3-artist-comparison (Apache-2.0).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        Text("About", style = MaterialTheme.typography.titleLarge)
        Text(
            "Offline NovelAI prompt companion. Compose tags with NovelAI weighting, " +
                "save prompts, browse artist tags. Copy → paste into the NovelAI PWA. " +
                "No NovelAI API calls.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            // Soft hint for Obtainium users
            scope.launch {
                snackbarHostState.showSnackbar("Track GitHub Releases with Obtainium for updates")
            }
        }) { Text("Updates via Obtainium") }
        Spacer(Modifier.height(32.dp))
    }
}
