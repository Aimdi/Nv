package com.nai.promptcompanion.data.catalog

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray

/**
 * Seeds the artist-tag catalog from the bundled `assets/seed/artists.json`
 * (the `deus-ex-machina/novelai-anime-v3-artist-comparison` dataset, Apache-2.0:
 * 15,000 Danbooru artist tags with post counts).
 *
 * The parser is deliberately tolerant: it accepts an array of objects with
 * `name`/`post_count` (optionally `works`/`uniqueness`, matching ThetaCursed's
 * Illustrious/NoobAI explorer records), or a plain array of tag strings.
 */
class CatalogSeeder(
    private val context: Context,
    private val db: CatalogDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfEmpty(): Int = withContext(Dispatchers.IO) {
        val dao = db.artistTagDao()
        if (dao.count() > 0) return@withContext dao.count()

        val raw = context.assets.open("seed/artists.json").bufferedReader().use { it.readText() }
        val root = json.parseToJsonElement(raw)
        val entities = parseEntries(root).distinctBy { it.tag }
        val fts = entities.map {
            ArtistTagFtsEntity(
                tag = it.tag,
                searchText = it.tag + " " + it.tag.replace('_', ' '),
            )
        }

        // Chunked single-transaction inserts keep first-run seeding fast
        // (~15k rows in a second or two on a mid-range phone).
        entities.chunked(CHUNK_SIZE).forEach { chunk ->
            db.withTransaction {
                dao.insertAll(chunk)
            }
        }
        fts.chunked(CHUNK_SIZE).forEach { chunk ->
            db.withTransaction {
                dao.insertFtsAll(chunk)
            }
        }
        entities.size
    }

    private fun parseEntries(root: JsonElement): List<ArtistTagEntity> {
        val array = (root as? JsonArray)
            ?: (root as? JsonObject)?.get("artists")?.jsonArray
            ?: return emptyList()
        return array.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.content.trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { ArtistTagEntity(tag = it) }

                is JsonObject -> {
                    val name = (element["name"] as? JsonPrimitive)?.content
                        ?: (element["tag"] as? JsonPrimitive)?.content
                        ?: return@mapNotNull null
                    val postCount = element["post_count"].asInt()
                        ?: element["postCount"].asInt()
                        ?: element["works"].asInt()
                        ?: 0
                    val uniqueness = element["uniqueness"].asInt() ?: 0
                    val source = (element["source"] as? JsonPrimitive)?.content ?: "nai-v3"
                    ArtistTagEntity(
                        tag = name.trim(),
                        postCount = postCount,
                        uniqueness = uniqueness,
                        source = source,
                    )
                }

                else -> null
            }
        }
    }

    private fun JsonElement?.asInt(): Int? =
        (this as? JsonPrimitive)?.intOrNull

    companion object {
        private const val CHUNK_SIZE = 1_000
    }
}
