package com.naicompanion.data

import com.naicompanion.data.backup.BackupCombo
import com.naicompanion.data.backup.BackupFile
import com.naicompanion.data.backup.BackupPrompt
import com.naicompanion.data.model.TagEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `backup file round trips through json`() {
        val backup = BackupFile(
            exportedAt = 1_700_000_000_000,
            prompts = listOf(
                BackupPrompt(
                    title = "portrait",
                    body = "1girl, {masterpiece}",
                    isFavorite = true,
                    createdAt = 1,
                    updatedAt = 2,
                )
            ),
            combos = listOf(
                BackupCombo(
                    title = "base",
                    entries = listOf(
                        TagEntry(id = "a", tag = "1girl"),
                        TagEntry(id = "b", tag = "smile", numericWeight = 1.5f, enabled = false),
                    ),
                    createdAt = 3,
                    updatedAt = 4,
                )
            ),
            favoriteArtists = listOf("mizuki_hitoshi"),
        )

        val decoded = json.decodeFromString<BackupFile>(json.encodeToString(backup))

        assertEquals(BackupFile.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(backup, decoded)
    }

    @Test
    fun `import tolerates unknown fields for forward compatibility`() {
        val futureJson = """
            {
              "schemaVersion": 1,
              "app": "nai-prompt-companion",
              "exportedAt": 123,
              "someFutureField": {"nested": true},
              "prompts": [],
              "combos": [],
              "favoriteArtists": []
            }
        """.trimIndent()
        val decoded = json.decodeFromString<BackupFile>(futureJson)
        assertEquals(1, decoded.schemaVersion)
        assertTrue(decoded.prompts.isEmpty())
    }

    @Test
    fun `tag entry serialization preserves ordering fields`() {
        val entries = listOf(
            TagEntry(id = "1", tag = "first", bracketCount = 2),
            TagEntry(id = "2", tag = "second", bracketCount = -1),
        )
        val decoded = json.decodeFromString<List<TagEntry>>(json.encodeToString(entries))
        assertEquals(entries, decoded)
    }
}
