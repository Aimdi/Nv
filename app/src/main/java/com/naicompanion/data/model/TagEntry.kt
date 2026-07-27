package com.naicompanion.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One weighted tag inside a combo under construction.
 *
 * [bracketCount] > 0 renders N pairs of `{ }` (x1.05 emphasis each),
 * < 0 renders N pairs of `[ ]` (/1.05). [numericWeight], when non-null,
 * takes precedence and renders the `x.x::tag::` form (V4.5 also allows
 * negative values). Order within the combo matters: earlier tags have
 * higher priority in NovelAI.
 */
@Serializable
data class TagEntry(
    val id: String = UUID.randomUUID().toString(),
    val tag: String,
    val bracketCount: Int = 0,
    val numericWeight: Float? = null,
    val enabled: Boolean = true,
)
