package dev.naicompanion.app.data.user

import dev.naicompanion.app.core.prompt.Combo
import dev.naicompanion.app.core.prompt.TagEntry
import dev.naicompanion.app.core.prompt.TagKind

fun ComboTagEntity.toTagEntry(): TagEntry = TagEntry(
    id = if (id == 0L) java.util.UUID.randomUUID().toString() else "combo-tag-$id",
    tag = tag,
    kind = TagKind.fromStorage(kind),
    bracketCount = bracketCount,
    numericWeight = numericWeight,
    enabled = enabled,
)

fun TagEntry.toEntity(comboId: Long, position: Int): ComboTagEntity = ComboTagEntity(
    comboId = comboId,
    position = position,
    tag = tag,
    kind = kind.name,
    bracketCount = bracketCount,
    numericWeight = numericWeight,
    enabled = enabled,
)

fun ComboWithTags.toCombo(): Combo = Combo(
    id = combo.id,
    name = combo.name,
    entries = tags.sortedBy { it.position }.map { it.toTagEntry() },
    isFavorite = combo.isFavorite,
    createdAt = combo.createdAt,
    updatedAt = combo.updatedAt,
)
