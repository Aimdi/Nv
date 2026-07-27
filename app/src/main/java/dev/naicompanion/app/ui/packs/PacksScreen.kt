package dev.naicompanion.app.ui.packs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.naicompanion.app.data.packs.InstalledPack
import dev.naicompanion.app.data.packs.PackDescriptor
import dev.naicompanion.app.data.packs.PackInstallState
import dev.naicompanion.app.ui.common.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacksScreen(
    viewModel: PacksViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingRemoval by remember { mutableStateOf<InstalledPack?>(null) }
    var editingUrl by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFromFile) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preview packs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshManifest) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh pack list")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("How previews work", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Preview images are downloaded separately from the app and " +
                                "stored in private app storage, so they are never evicted the way " +
                                "browser caches are. Remove a pack any time to reclaim the space.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            (state.installState as? PackInstallState.Downloading)?.let { downloading ->
                item {
                    ProgressCard(
                        title = "Downloading",
                        detail = "${formatBytes(downloading.bytesRead)} of " +
                            formatBytes(downloading.totalBytes),
                        fraction = downloading.fraction.takeIf { downloading.totalBytes > 0 },
                        onCancel = viewModel::cancelInstall,
                    )
                }
            }

            (state.installState as? PackInstallState.Verifying)?.let {
                item {
                    ProgressCard(
                        title = "Verifying download",
                        detail = "Checking the pack checksum",
                        fraction = null,
                        onCancel = viewModel::cancelInstall,
                    )
                }
            }

            (state.installState as? PackInstallState.Extracting)?.let { extracting ->
                item {
                    ProgressCard(
                        title = "Unpacking",
                        detail = "${extracting.filesDone} of ${extracting.filesTotal} files",
                        fraction = extracting.fraction,
                        onCancel = viewModel::cancelInstall,
                    )
                }
            }

            if (state.installed.isNotEmpty()) {
                item { SectionHeader("Installed") }
                items(state.installed, key = { it.id }) { pack ->
                    InstalledPackCard(pack = pack, onRemove = { pendingRemoval = pack })
                }
            }

            item { SectionHeader("Available") }

            if (state.loadingManifest) {
                item { Text("Loading pack list...", style = MaterialTheme.typography.bodyMedium) }
            }

            state.manifestError?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Could not load the pack list",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = "You can still build a pack yourself with the scripts in " +
                                    "tools/ and import the zip below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            items(state.available, key = { it.id }) { descriptor ->
                AvailablePackCard(
                    descriptor = descriptor,
                    installed = state.isInstalled(descriptor),
                    busy = state.installState !is PackInstallState.Idle &&
                        state.installState !is PackInstallState.Done &&
                        state.installState !is PackInstallState.Failed,
                    onInstall = { viewModel.install(descriptor) },
                )
            }

            item { SectionHeader("Import from a file") }
            item {
                Card {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Build a pack on a computer with tools/build_pack.py, copy the " +
                                "zip to your phone, and import it here. No hosting required.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = { importLauncher.launch(arrayOf("application/zip", "*/*")) }) {
                            Text("Choose a pack zip")
                        }
                    }
                }
            }

            item { SectionHeader("Pack source") }
            item {
                Card {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (editingUrl) {
                            var url by remember { mutableStateOf(state.manifestUrl) }
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = { Text("Manifest URL") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    viewModel.setManifestUrl(url)
                                    editingUrl = false
                                }) { Text("Save") }
                                TextButton(onClick = { editingUrl = false }) { Text("Cancel") }
                            }
                        } else {
                            Text(
                                text = state.manifestUrl,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = { editingUrl = true }) { Text("Change") }
                        }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { pack ->
        ConfirmDialog(
            title = "Remove ${pack.name}?",
            message = "This frees ${formatBytes(pack.bytesOnDisk)}. You can reinstall it later.",
            confirmLabel = "Remove",
            onDismiss = { pendingRemoval = null },
            onConfirm = {
                viewModel.remove(pack)
                pendingRemoval = null
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ProgressCard(
    title: String,
    detail: String,
    fraction: Float?,
    onCancel: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(detail, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun InstalledPackCard(pack: InstalledPack, onRemove: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(pack.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${pack.imageCount} images  ·  ${formatBytes(pack.bytesOnDisk)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (pack.attribution.isNotBlank()) {
                    Text(
                        text = pack.attribution,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove pack")
            }
        }
    }
}

@Composable
private fun AvailablePackCard(
    descriptor: PackDescriptor,
    installed: InstalledPack?,
    busy: Boolean,
    onInstall: () -> Unit,
) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(descriptor.name, style = MaterialTheme.typography.titleSmall)
            if (descriptor.description.isNotBlank()) {
                Text(descriptor.description, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = listOfNotNull(
                    descriptor.imageCount.takeIf { it > 0 }?.let { "$it images" },
                    descriptor.sizeBytes.takeIf { it > 0 }?.let { formatBytes(it) },
                    descriptor.thumbnailPx.takeIf { it > 0 }?.let { "${it}px thumbnails" },
                    descriptor.license.takeIf { it.isNotBlank() },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (descriptor.attribution.isNotBlank()) {
                Text(
                    text = descriptor.attribution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                installed != null && installed.version >= descriptor.version ->
                    OutlinedButton(onClick = {}, enabled = false) { Text("Installed") }

                installed != null ->
                    Button(onClick = onInstall, enabled = !busy) { Text("Update") }

                else -> Button(onClick = onInstall, enabled = !busy) { Text("Install") }
            }
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f kB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
