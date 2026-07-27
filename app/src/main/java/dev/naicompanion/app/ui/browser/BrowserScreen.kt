package dev.naicompanion.app.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.naicompanion.app.core.prompt.TagKind
import dev.naicompanion.app.data.catalog.CatalogSort
import dev.naicompanion.app.data.repository.CatalogStatus
import dev.naicompanion.app.ui.builder.formatPostCount
import dev.naicompanion.app.ui.common.EmptyState
import dev.naicompanion.app.ui.util.ClipboardBridge

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    snackbarHostState: SnackbarHostState,
    onAddTag: (String, TagKind) -> Unit,
    onOpenPacks: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val swipeMode by viewModel.swipeMode.collectAsStateWithLifecycle()

    var detail by remember { mutableStateOf<BrowseItem?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    if (swipeMode) {
        SwipeModeScreen(
            items = state.items,
            onExit = { viewModel.setSwipeMode(false) },
            onFavorite = viewModel::toggleFavorite,
            onAdd = { item ->
                onAddTag(item.artist.name, item.kind)
                viewModel.notifyAdded(item)
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browse tags") },
                actions = {
                    IconButton(
                        onClick = { viewModel.setSwipeMode(true) },
                        enabled = state.items.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Style, contentDescription = "Swipe mode")
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            CatalogSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label) },
                                    onClick = {
                                        viewModel.setSort(sort)
                                        sortMenuOpen = false
                                    },
                                    trailingIcon = {
                                        if (state.filters.sort == sort) {
                                            Icon(Icons.Default.Star, contentDescription = null)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenPacks) {
                        Icon(Icons.Default.Download, contentDescription = "Preview packs")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.filters.text,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search artists and tags") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.filters.favoritesOnly,
                    onClick = viewModel::toggleFavoritesOnly,
                    label = { Text("Favorites") },
                )
                FilterChip(
                    selected = state.filters.onlyWithPreview,
                    onClick = viewModel::toggleOnlyWithPreview,
                    label = { Text("Has preview") },
                )
                state.availableSources.forEach { source ->
                    FilterChip(
                        selected = source in state.filters.sources,
                        onClick = { viewModel.toggleSource(source) },
                        label = { Text(sourceLabel(source)) },
                    )
                }
            }

            when {
                state.status is CatalogStatus.Unavailable -> {
                    EmptyState(
                        title = "Tag catalog unavailable",
                        message = (state.status as CatalogStatus.Unavailable).message,
                    )
                }

                state.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.items.isEmpty() -> {
                    EmptyState(
                        title = "No matching tags",
                        message = "Try a shorter search, or clear the filters above.",
                    )
                }

                else -> {
                    if (!state.anyPackInstalled) {
                        PackHint(onOpenPacks = onOpenPacks)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(state.columns),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.items, key = { it.artist.id }) { item ->
                            TagCard(
                                item = item,
                                onClick = { detail = item },
                                onLongClick = {
                                    onAddTag(item.artist.name, item.kind)
                                    viewModel.notifyAdded(item)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    detail?.let { item ->
        TagDetailSheet(
            item = item,
            fullImage = viewModel.detailImage(item),
            onDismiss = { detail = null },
            onAdd = {
                onAddTag(item.artist.name, item.kind)
                viewModel.notifyAdded(item)
            },
            onToggleFavorite = { viewModel.toggleFavorite(item) },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TagCard(
    item: BrowseItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
        ) {
            if (item.thumbnail != null) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(item.thumbnail)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.artist.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.ImageNotSupported,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Scrim so the overlaid name stays legible on light previews.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.66f),
                        ),
                    ),
            )

            Text(
                text = item.artist.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            )

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp),
            ) {
                Icon(
                    imageVector = if (item.isFavorite) {
                        Icons.Default.Star
                    } else {
                        Icons.Outlined.StarBorder
                    },
                    contentDescription = "Toggle favorite",
                    tint = if (item.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.8f)
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun PackHint(onOpenPacks: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("No preview images yet", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Search and favorites work offline right now. Install a preview pack " +
                        "to see what each artist tag looks like.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onOpenPacks) { Text("Packs") }
        }
    }
}

fun sourceLabel(source: String): String = when (source) {
    "nai-v3" -> "NAI v3"
    "nai-v4" -> "NAI v4"
    "nai-v4-5" -> "NAI v4.5"
    "illustrious" -> "Illustrious"
    "danbooru" -> "Danbooru"
    else -> source.replaceFirstChar { it.uppercase() }
}
