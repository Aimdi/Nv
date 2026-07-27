package com.nai.promptcompanion.ui.tags

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nai.promptcompanion.CatalogSeedState
import com.nai.promptcompanion.data.catalog.ArtistTagEntity
import com.nai.promptcompanion.data.imagepack.ImagePackState
import com.nai.promptcompanion.data.repo.CatalogSort
import com.nai.promptcompanion.novelai.NovelaiSyntax
import com.nai.promptcompanion.util.copyToClipboard
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: TagsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    val query by viewModel.query.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val swipeMode by viewModel.swipeMode.collectAsState()
    val favorites by viewModel.favoriteTags.collectAsState()
    val seedState by viewModel.seedState.collectAsState()
    val packState by viewModel.packState.collectAsState()

    var detailFor by remember { mutableStateOf<ArtistTagEntity?>(null) }
    var showPackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            label = { Text("Search artist tags") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Uniqueness sort only makes sense for datasets that carry uniqueness ranks
            // (e.g. ThetaCursed's explorer); nai-v3 data has none.
            val hasUniqueness = remember(items) { items.any { it.uniqueness > 0 } }
            CatalogSort.entries
                .filter { it != CatalogSort.UNIQUENESS || hasUniqueness }
                .forEach { mode ->
                    FilterChip(
                        selected = sort == mode,
                        onClick = { viewModel.sort.value = mode },
                        label = { Text(mode.label) },
                    )
                }
            FilterChip(
                selected = favoritesOnly,
                onClick = { viewModel.favoritesOnly.value = !favoritesOnly },
                label = { Text("★") },
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.swipeMode.value = !swipeMode }) {
                Icon(
                    Icons.Default.ViewCarousel,
                    contentDescription = "Swipe mode",
                    tint = if (swipeMode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showPackDialog = true }) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = "Image pack",
                    tint = when (packState) {
                        is ImagePackState.Installed -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        when (val seed = seedState) {
            is CatalogSeedState.Seeding -> {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "Preparing tag catalog…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is CatalogSeedState.Failed -> Text(
                "Catalog failed to load: ${seed.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
            )
            is CatalogSeedState.Ready -> Unit
        }

        if (packState is ImagePackState.NotInstalled || packState is ImagePackState.Failed) {
            Spacer(Modifier.height(8.dp))
            PackBanner(
                failed = (packState as? ImagePackState.Failed)?.message,
                onClick = { showPackDialog = true },
            )
        }
        (packState as? ImagePackState.Downloading)?.let { dl ->
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Downloading preview pack…", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { dl.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${dl.filesDone}/${dl.fileCount} files · ${formatBytes(dl.downloadedBytes)}" +
                            if (dl.totalBytes > 0) " / ${formatBytes(dl.totalBytes)}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (swipeMode) {
            SwipeBrowser(
                items = items,
                favorites = favorites,
                viewModel = viewModel,
                onCopy = { tag ->
                    copyToClipboard(context, NovelaiSyntax.normalizeTagText(tag), "artist tag")
                    viewModel.copyTagEvent(tag)
                },
            )
        } else {
            TagGrid(
                items = items,
                favorites = favorites,
                viewModel = viewModel,
                onOpen = { detailFor = it },
                onCopy = { tag ->
                    copyToClipboard(context, NovelaiSyntax.normalizeTagText(tag), "artist tag")
                    viewModel.copyTagEvent(tag)
                },
            )
        }
    }

    detailFor?.let { artist ->
        ArtistDetailSheet(
            artist = artist,
            isFavorite = artist.tag in favorites,
            viewModel = viewModel,
            onDismiss = { detailFor = null },
            onCopy = {
                copyToClipboard(context, NovelaiSyntax.normalizeTagText(artist.tag), "artist tag")
                viewModel.copyTagEvent(artist.tag)
            },
        )
    }

    if (showPackDialog) {
        PackDialog(
            packState = packState,
            viewModel = viewModel,
            onDismiss = { showPackDialog = false },
        )
    }
}

@Composable
private fun PackBanner(failed: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Image, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Preview images not installed", style = MaterialTheme.typography.titleSmall)
                Text(
                    failed?.let { "Last attempt failed: $it" }
                        ?: "Download the optional WebP thumbnail pack for artist previews (~150–350 MB).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClick) { Text("Get") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagGrid(
    items: List<ArtistTagEntity>,
    favorites: Set<String>,
    viewModel: TagsViewModel,
    onOpen: (ArtistTagEntity) -> Unit,
    onCopy: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(items, key = { it.tag }) { artist ->
            var menuOpen by remember(artist.tag) { mutableStateOf(false) }
            Box {
                Card(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .combinedClickable(
                            onClick = { onOpen(artist) },
                            onLongClick = { menuOpen = true },
                        ),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        ArtistImage(
                            model = viewModel.thumbnailModel(artist.tag),
                            tag = artist.tag,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (artist.tag in favorites) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Favorite",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0xCC000000))
                                    )
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(
                                NovelaiSyntax.normalizeTagText(artist.tag),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Add to combo") },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                        onClick = {
                            menuOpen = false
                            viewModel.addToCombo(artist.tag)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy tag") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = {
                            menuOpen = false
                            onCopy(artist.tag)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(if (artist.tag in favorites) "Unfavorite" else "Favorite")
                        },
                        leadingIcon = {
                            Icon(
                                if (artist.tag in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                                null,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            viewModel.toggleFavorite(artist.tag)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistImage(
    model: Any?,
    tag: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = tag,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        // Placeholder: artist initials on a branded background until the pack is installed.
        val initials = tag.replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase(Locale.US) }
        Box(
            modifier
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeBrowser(
    items: List<ArtistTagEntity>,
    favorites: Set<String>,
    viewModel: TagsViewModel,
    onCopy: (String) -> Unit,
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tags to browse")
        }
        return
    }
    val pagerState = rememberPagerState(pageCount = { items.size })
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        pageSpacing = 12.dp,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) { page ->
        val artist = items.getOrNull(page) ?: return@HorizontalPager
        Card(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                ArtistImage(
                    model = viewModel.detailModel(artist.tag),
                    tag = artist.tag,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))
                        )
                        .padding(16.dp),
                ) {
                    Text(
                        NovelaiSyntax.normalizeTagText(artist.tag),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text(
                        "${artist.postCount} posts · source: ${artist.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xCCFFFFFF),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.addToCombo(artist.tag) }) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                        OutlinedButton(onClick = { viewModel.toggleFavorite(artist.tag) }) {
                            Icon(
                                if (artist.tag in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                            )
                        }
                        OutlinedButton(onClick = { onCopy(artist.tag) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistDetailSheet(
    artist: ArtistTagEntity,
    isFavorite: Boolean,
    viewModel: TagsViewModel,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            ArtistImage(
                model = viewModel.detailModel(artist.tag),
                tag = artist.tag,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                NovelaiSyntax.normalizeTagText(artist.tag),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                buildString {
                    append("${artist.postCount} posts on Danbooru · source: ${artist.source}")
                    if (artist.uniqueness > 0) append(" · uniqueness #${artist.uniqueness}")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    viewModel.addToCombo(artist.tag)
                    onDismiss()
                }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add to combo")
                }
                OutlinedButton(onClick = {
                    viewModel.addToCombo(artist.tag, withWeight = 1.3)
                    onDismiss()
                }) { Text("Add ×1.3") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Copy tag")
                }
                OutlinedButton(onClick = { viewModel.toggleFavorite(artist.tag) }) {
                    Icon(
                        if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isFavorite) "Unfavorite" else "Favorite")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PackDialog(
    packState: ImagePackState,
    viewModel: TagsViewModel,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        url = viewModel.savedPackUrl()?.takeIf { it.isNotBlank() } ?: viewModel.defaultPackUrl()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preview image pack") },
        text = {
            Column {
                when (val state = packState) {
                    is ImagePackState.Installed ->
                        Text("Installed (v${state.version}, ${state.fileCount} files). Previews are served from app-private storage — immune to browser-style storage eviction.")
                    is ImagePackState.Downloading ->
                        Text("Downloading… ${state.filesDone}/${state.fileCount} files")
                    is ImagePackState.Failed ->
                        Text("Last download failed: ${state.message}")
                    ImagePackState.NotInstalled ->
                        Text("Paste the URL of a pack manifest.json (hosted e.g. as a GitHub Release asset). The pack downloads once into app-private storage and works fully offline afterwards.")
                }
                if (packState !is ImagePackState.Installed) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = url ?: "",
                        onValueChange = { url = it },
                        label = { Text("manifest.json URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            when (packState) {
                is ImagePackState.Installed ->
                    TextButton(onClick = {
                        viewModel.removePack()
                        onDismiss()
                    }) { Text("Remove pack", color = MaterialTheme.colorScheme.error) }
                is ImagePackState.Downloading ->
                    TextButton(onClick = { viewModel.cancelDownload() }) { Text("Cancel download") }
                else ->
                    TextButton(
                        onClick = { url?.let(viewModel::downloadPack) },
                        enabled = !url.isNullOrBlank(),
                    ) { Text("Download") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "?"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) String.format(Locale.US, "%.1f MB", mb)
    else String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}
