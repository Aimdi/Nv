package app.promptcompanion.nai.data.export

import app.promptcompanion.nai.data.db.entity.ComboEntity
import app.promptcompanion.nai.data.db.entity.PromptEntity
import app.promptcompanion.nai.domain.TagEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned JSON backup/export schema for user data (prompts + combos + favorites metadata).
 */
object BackupExporter {
    const val SCHEMA_VERSION = 1

    fun export(
        prompts: List<PromptEntity>,
        combos: List<ComboEntity>,
        favoriteArtistIds: List<Long> = emptyList(),
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("app", "prompt-companion")

        val promptsJson = JSONArray()
        prompts.forEach { p ->
            promptsJson.put(
                JSONObject()
                    .put("title", p.title)
                    .put("body", p.body)
                    .put("isFavorite", p.isFavorite)
                    .put("notes", p.notes)
                    .put("createdAt", p.createdAt)
                    .put("updatedAt", p.updatedAt),
            )
        }
        root.put("prompts", promptsJson)

        val combosJson = JSONArray()
        combos.forEach { c ->
            combosJson.put(
                JSONObject()
                    .put("title", c.title)
                    .put("entries", JSONArray(c.entriesJson))
                    .put("isFavorite", c.isFavorite)
                    .put("createdAt", c.createdAt)
                    .put("updatedAt", c.updatedAt),
            )
        }
        root.put("combos", combosJson)

        val favs = JSONArray()
        favoriteArtistIds.forEach { favs.put(it) }
        root.put("favoriteArtistIds", favs)

        return root.toString(2)
    }

    data class BackupPayload(
        val schemaVersion: Int,
        val prompts: List<PromptEntity>,
        val combos: List<ComboEntity>,
        val favoriteArtistIds: List<Long>,
    )

    fun import(json: String): BackupPayload {
        val root = JSONObject(json)
        val version = root.optInt("schemaVersion", 1)
        require(version <= SCHEMA_VERSION) { "Unsupported schema version $version" }

        val prompts = mutableListOf<PromptEntity>()
        val promptsArr = root.optJSONArray("prompts") ?: JSONArray()
        for (i in 0 until promptsArr.length()) {
            val o = promptsArr.getJSONObject(i)
            prompts += PromptEntity(
                title = o.getString("title"),
                body = o.getString("body"),
                isFavorite = o.optBoolean("isFavorite", false),
                notes = o.optString("notes", ""),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            )
        }

        val combos = mutableListOf<ComboEntity>()
        val combosArr = root.optJSONArray("combos") ?: JSONArray()
        for (i in 0 until combosArr.length()) {
            val o = combosArr.getJSONObject(i)
            val entries = o.opt("entries")
            val entriesJson = when (entries) {
                is JSONArray -> entries.toString()
                is String -> entries
                else -> "[]"
            }
            combos += ComboEntity(
                title = o.getString("title"),
                entriesJson = entriesJson,
                isFavorite = o.optBoolean("isFavorite", false),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            )
        }

        val favIds = mutableListOf<Long>()
        val favArr = root.optJSONArray("favoriteArtistIds") ?: JSONArray()
        for (i in 0 until favArr.length()) {
            favIds += favArr.getLong(i)
        }

        return BackupPayload(version, prompts, combos, favIds)
    }
}

object TagEntryJson {
    fun encode(entries: List<TagEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("tag", e.tag)
                    .put("bracketCount", e.bracketCount)
                    .put("numericWeight", e.numericWeight)
                    .put("enabled", e.enabled)
                    .put("forceArtistPrefix", e.forceArtistPrefix),
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<TagEntry> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        val out = ArrayList<TagEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val weight = if (o.isNull("numericWeight")) null else o.getDouble("numericWeight")
            out += TagEntry(
                id = o.optString("id", java.util.UUID.randomUUID().toString()),
                tag = o.getString("tag"),
                bracketCount = o.optInt("bracketCount", 0),
                numericWeight = weight,
                enabled = o.optBoolean("enabled", true),
                forceArtistPrefix = o.optBoolean("forceArtistPrefix", false),
            )
        }
        return out
    }
}
