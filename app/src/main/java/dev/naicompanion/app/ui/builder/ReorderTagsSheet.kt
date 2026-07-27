package dev.naicompanion.app.ui.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.naicompanion.app.core.prompt.TagEntry
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Drag-to-reorder surface.
 *
 * Reordering lives in its own sheet rather than on the chip flow because prompt order is a
 * deliberate, occasional edit, and a vertical list with real drag handles is far easier to hit
 * accurately than reflowing chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderTagsSheet(
    entries: List<TagEntry>,
    onMove: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onMove(from.index, to.index)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "Drag to reorder",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Tags nearer the top carry more weight in the generated image.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(entries, key = { it.id }) { entry ->
                    ReorderableItem(reorderableState, key = entry.id) { isDragging ->
                        Card(
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isDragging) 8.dp else 0.dp,
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(
                                    text = entry.tag,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (entry.enabled) {
                                        null
                                    } else {
                                        TextDecoration.LineThrough
                                    },
                                    modifier = Modifier.align(Alignment.CenterStart),
                                )
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Reorder ${entry.tag}",
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .draggableHandle(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
