package dev.naicompanion.app.ui.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.naicompanion.app.core.prompt.NovelAiPromptRenderer
import dev.naicompanion.app.core.prompt.RenderOptions
import dev.naicompanion.app.core.prompt.TagEntry
import dev.naicompanion.app.core.prompt.TagKind
import dev.naicompanion.app.core.prompt.formatMultiplier
import dev.naicompanion.app.core.prompt.formatWeight
import dev.naicompanion.app.ui.theme.PromptPreviewTextStyle
import kotlin.math.roundToInt

/**
 * Per-tag emphasis editor.
 *
 * The two emphasis mechanisms are presented separately because they compose in NovelAI: bracket
 * nesting is a coarse x1.05 stepper, the numeric slider is the precise `1.5::tag::` form.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagEditorSheet(
    entry: TagEntry,
    renderOptions: RenderOptions,
    onDismiss: () -> Unit,
    onTagTextChange: (String) -> Unit,
    onKindChange: (TagKind) -> Unit,
    onBracketStep: (Int) -> Unit,
    onBracketSet: (Int) -> Unit,
    onWeightChange: (Double?) -> Unit,
    onEnabledToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = entry.tag,
                onValueChange = onTagTextChange,
                label = { Text("Tag") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            PreviewLine(entry = entry, renderOptions = renderOptions)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Category")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagKind.entries.forEach { kind ->
                        FilterChip(
                            selected = entry.kind == kind,
                            onClick = { onKindChange(kind) },
                            label = { Text(kind.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Bracket emphasis  ·  x1.05 per step")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = { onBracketStep(-1) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Weaken")
                    }
                    Text(
                        text = bracketSummary(entry.bracketCount),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onBracketStep(1) }) {
                        Icon(Icons.Default.Add, contentDescription = "Strengthen")
                    }
                    if (entry.bracketCount != 0) {
                        OutlinedButton(onClick = { onBracketSet(0) }) { Text("Reset") }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("Numeric emphasis")
                    if (entry.numericWeight == null) {
                        AssistChip(
                            onClick = { onWeightChange(1.2) },
                            label = { Text("Add") },
                        )
                    } else {
                        AssistChip(
                            onClick = { onWeightChange(null) },
                            label = { Text("Remove") },
                        )
                    }
                }

                val weight = entry.numericWeight
                if (weight != null) {
                    Text(
                        text = "${formatWeight(weight)}::${entry.tag}::",
                        style = PromptPreviewTextStyle,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Slider(
                        value = weight.toFloat(),
                        onValueChange = { onWeightChange(roundToStep(it)) },
                        valueRange = TagEntry.MIN_NUMERIC_WEIGHT.toFloat()..
                            TagEntry.MAX_NUMERIC_WEIGHT.toFloat(),
                        // 0.05 increments across the -5..5 range.
                        steps = 199,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.7, 0.9, 1.1, 1.3, 1.5, 2.0).forEach { preset ->
                            AssistChip(
                                onClick = { onWeightChange(preset) },
                                label = { Text(formatWeight(preset)) },
                            )
                        }
                    }
                    if (weight < 0 && !renderOptions.model.supportsNegativeWeights) {
                        Text(
                            text = "Negative emphasis needs NAI Diffusion V4.5 or newer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Included in prompt", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Keep the tag but leave it out of the output",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = entry.enabled, onCheckedChange = { onEnabledToggle() })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  Remove")
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun PreviewLine(entry: TagEntry, renderOptions: RenderOptions) {
    val rendered = NovelAiPromptRenderer.renderEntry(entry, renderOptions) ?: "(empty)"
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = rendered, style = PromptPreviewTextStyle)
            Text(
                text = "Attention ${formatMultiplier(entry.effectiveWeight)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun bracketSummary(count: Int): String = when {
    count > 0 -> "${"{".repeat(count)} +$count ${"}".repeat(count)}"
    count < 0 -> "${"[".repeat(-count)} $count ${"]".repeat(-count)}"
    else -> "none"
}

/** Snaps the slider to 0.05 so the emitted weight stays readable. */
private fun roundToStep(value: Float): Double = (value * 20f).roundToInt() / 20.0
