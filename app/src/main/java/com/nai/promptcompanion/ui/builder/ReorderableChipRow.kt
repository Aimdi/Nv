package com.nai.promptcompanion.ui.builder

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nai.promptcompanion.novelai.NovelaiSyntax
import com.nai.promptcompanion.novelai.TagEntry
import kotlin.math.abs

/**
 * Prompt order matters in NovelAI (earlier tags have more influence), so the
 * chip row supports long-press drag-to-reorder in addition to tap-to-edit.
 */
@Composable
fun ReorderableChipRow(
    entries: List<TagEntry>,
    onMove: (from: Int, to: Int) -> Unit,
    onChipClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Text(
            text = "No tags yet. Add tags below or long-press a tag in the browser.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val spacingPx = with(LocalDensity.current) { 8.dp.toPx() }

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(entries, key = { _, e -> e.id }) { index, entry ->
            val isDragging = index == draggingIndex
            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationX = if (isDragging) dragOffset else 0f }
                    .shadow(if (isDragging) 6.dp else 0.dp)
                    .pointerInput(entry.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                draggingIndex = -1
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.x

                                val items = listState.layoutInfo.visibleItemsInfo
                                val dragged = items.firstOrNull { it.index == draggingIndex }
                                    ?: return@detectDragGesturesAfterLongPress
                                val draggedCenter = dragged.offset + dragged.size / 2f + dragOffset

                                // Swap when the dragged chip's center crosses a neighbor's center.
                                val right = items.firstOrNull {
                                    it.index == draggingIndex + 1
                                }
                                if (right != null && draggedCenter > right.offset + right.size / 2f) {
                                    onMove(draggingIndex, right.index)
                                    dragOffset -= (right.size + spacingPx)
                                    draggingIndex = right.index
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    return@detectDragGesturesAfterLongPress
                                }
                                val left = items.firstOrNull {
                                    it.index == draggingIndex - 1
                                }
                                if (left != null && draggedCenter < left.offset + left.size / 2f) {
                                    onMove(draggingIndex, left.index)
                                    dragOffset += (left.size + spacingPx)
                                    draggingIndex = left.index
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    return@detectDragGesturesAfterLongPress
                                }

                                // Auto-scroll near the row edges while dragging.
                                val viewportStart = listState.layoutInfo.viewportStartOffset
                                val viewportEnd = listState.layoutInfo.viewportEndOffset
                                if (dragged.offset + dragOffset < viewportStart + 40) {
                                    listState.dispatchRawDelta(-12f)
                                } else if (dragged.offset + dragOffset + dragged.size > viewportEnd - 40) {
                                    listState.dispatchRawDelta(12f)
                                }
                            },
                        )
                    },
            ) {
                WeightedChip(
                    entry = entry,
                    onClick = { if (draggingIndex == -1) onChipClick(index) },
                )
            }
        }
    }
}

@Composable
private fun WeightedChip(entry: TagEntry, onClick: () -> Unit) {
    val label = buildString {
        append(NovelaiSyntax.normalizeTagText(entry.tag))
        val weight = entry.effectiveWeight
        if (abs(weight - 1.0) > 0.0001) {
            append("  ×")
            append(NovelaiSyntax.formatWeight(weight))
        }
    }
    InputChip(
        selected = entry.enabled,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        },
    )
}
