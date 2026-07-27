package app.promptcompanion.nai.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.promptcompanion.nai.BuildConfig
import app.promptcompanion.nai.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onPackUrlChange: (String) -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Prompt Companion", style = MaterialTheme.typography.headlineMedium)
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))

            Text("Preview image pack", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Optional WebP thumbnails (~150–350 MB) download into app-private storage on first run. " +
                    "Not bundled in the APK. Host the zip on GitHub Releases and paste the URL below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.packUrl,
                onValueChange = onPackUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pack ZIP URL") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            val pack = state.pack
            Text(
                when {
                    pack == null -> "Checking…"
                    pack.installed -> "Installed · v${pack.version} · ${pack.bytesOnDisk / (1024 * 1024)} MB"
                    else -> "Not installed"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(state.progressText, style = MaterialTheme.typography.bodySmall)
            }
            state.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDownload, enabled = !state.downloading && state.packUrl.isNotBlank()) {
                Text(if (pack?.installed == true) "Re-download pack" else "Download pack")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRemove, enabled = pack?.installed == true && !state.downloading) {
                Text("Remove pack")
            }

            Spacer(Modifier.height(28.dp))
            Text("Data attributions", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Artist catalog seeded from deus-ex-machina/novelai-anime-v3-artist-comparison " +
                    "(artists.json, Apache-2.0). Previews are NAI v3-era — NovelAI V4.5 may reproduce " +
                    "some artists differently. Optional Illustrious/NoobAI packs (MIT, ThetaCursed) " +
                    "should be labeled separately when added.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "This app is an offline composer only. It does not call the NovelAI API.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text("Distribution", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Ship a signed APK on GitHub Releases and track updates with Obtainium.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
