package dev.naicompanion.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import dev.naicompanion.app.core.prompt.NovelAiModel
import dev.naicompanion.app.data.backup.ImportMode
import dev.naicompanion.app.data.repository.CatalogStatus
import dev.naicompanion.app.ui.common.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenPacks: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val catalogStatus by viewModel.catalogStatus.collectAsStateWithLifecycle()
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImportUri = uri }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection("Prompt syntax") {
                Text(
                    text = "Target model",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NovelAiModel.entries.forEach { model ->
                        FilterChip(
                            selected = settings.model == model,
                            onClick = { viewModel.setModel(model) },
                            label = { Text(model.label) },
                        )
                    }
                }
                Text(
                    text = "V4 and newer take the artist: prefix on artist tags. Negative " +
                        "numeric emphasis such as -1.5::tag:: needs V4.5.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()

                SettingsToggle(
                    title = "Underscores to spaces",
                    subtitle = "Emit \"blue eyes\" rather than \"blue_eyes\"; NovelAI responds " +
                        "noticeably differently to each.",
                    checked = settings.underscoresToSpaces,
                    onCheckedChange = viewModel::setUnderscoresToSpaces,
                )
                SettingsToggle(
                    title = "Artist prefix",
                    subtitle = "Prepend artist: to tags marked as artists.",
                    checked = settings.artistPrefix,
                    enabled = settings.model.supportsArtistPrefix,
                    onCheckedChange = viewModel::setArtistPrefix,
                )
                SettingsToggle(
                    title = "One tag per line",
                    subtitle = "Separate with a newline instead of a space after each comma.",
                    checked = settings.multilineSeparator,
                    onCheckedChange = viewModel::setMultilineSeparator,
                )
            }

            SettingsSection("Workflow") {
                SettingsToggle(
                    title = "Open NovelAI after copying",
                    subtitle = "Copies the prompt, then launches novelai.net/image so you can " +
                        "paste straight in.",
                    checked = settings.copyOpensNovelAi,
                    onCheckedChange = viewModel::setCopyOpensNovelAi,
                )
                Text(
                    text = "Grid columns: ${settings.browserColumns}",
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (2..5).forEach { columns ->
                        FilterChip(
                            selected = settings.browserColumns == columns,
                            onClick = { viewModel.setBrowserColumns(columns) },
                            label = { Text("$columns") },
                        )
                    }
                }
            }

            SettingsSection("Online enrichment") {
                Text(
                    text = "Generation stays offline (copy → paste into NovelAI). These toggles " +
                        "only fetch tag metadata and SFW artist preview samples.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsToggle(
                    title = "Live Danbooru tag search",
                    subtitle = "Merge validated autocomplete results with the offline catalog.",
                    checked = settings.onlineTagSearch,
                    onCheckedChange = viewModel::setOnlineTagSearch,
                )
                SettingsToggle(
                    title = "Online artist previews",
                    subtitle = "Load HuggingFace sample images on demand and cache them on device.",
                    checked = settings.onlineArtistPreviews,
                    onCheckedChange = viewModel::setOnlineArtistPreviews,
                )
                SettingsToggle(
                    title = "Allow NSFW rating tags",
                    subtitle = "When off (default), rating:* suggestions other than general are hidden.",
                    checked = settings.allowNsfwTags,
                    onCheckedChange = viewModel::setAllowNsfwTags,
                )
            }

            SettingsSection("Backup") {
                Text(
                    text = "Exports every prompt, combo and favorite as versioned JSON. You " +
                        "choose where the file goes, so it survives reinstalling the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch(viewModel.suggestedBackupFileName()) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Export") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Import") }
                }
            }

            SettingsSection("Preview packs") {
                Text(
                    text = "Artist preview images are downloaded separately to keep the app small.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenPacks) { Text("Manage packs") }
            }

            SettingsSection("About") {
                Text("Nv ${viewModel.appVersion}")
                Text(
                    text = when (val status = catalogStatus) {
                        is CatalogStatus.Ready ->
                            "Tag catalog: ${status.tagCount} tags from " +
                                status.sources.joinToString(", ").ifEmpty { "no sources" }

                        is CatalogStatus.Unavailable -> "Tag catalog unavailable: ${status.message}"
                        CatalogStatus.Loading -> "Tag catalog: checking..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Nv composes NovelAI prompts on-device. It never calls a generation " +
                        "API — copy the prompt and paste it into NovelAI yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    pendingImportUri?.let { uri ->
        ImportModeDialog(
            onDismiss = { pendingImportUri = null },
            onPick = { mode ->
                viewModel.import(uri, mode)
                pendingImportUri = null
            },
        )
    }
}

@Composable
private fun ImportModeDialog(onDismiss: () -> Unit, onPick: (ImportMode) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Merge keeps what you already have and adds the file's contents.")
                Text("Replace deletes your current prompts, combos and favorites first.")
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onPick(ImportMode.MERGE) }) {
                Text("Merge")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { onPick(ImportMode.REPLACE) }) {
                Text("Replace")
            }
        },
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
