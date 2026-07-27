package app.promptcompanion.nai.data.export

import com.google.common.truth.Truth.assertThat
import app.promptcompanion.nai.data.db.entity.ComboEntity
import app.promptcompanion.nai.data.db.entity.PromptEntity
import app.promptcompanion.nai.domain.TagEntry
import org.junit.Test

class BackupExporterTest {

    @Test
    fun exportImport_roundTrip() {
        val prompts = listOf(
            PromptEntity(
                title = "Sample",
                body = "1girl, {artist:test}",
                isFavorite = true,
                notes = "note",
                createdAt = 10,
                updatedAt = 20,
            ),
        )
        val combos = listOf(
            ComboEntity(
                title = "Combo",
                entriesJson = TagEntryJson.encode(
                    listOf(TagEntry(tag = "1girl"), TagEntry(tag = "artist:foo", numericWeight = 1.1)),
                ),
                isFavorite = false,
                createdAt = 11,
                updatedAt = 21,
            ),
        )
        val json = BackupExporter.export(prompts, combos, favoriteArtistIds = listOf(1L, 2L))
        val payload = BackupExporter.import(json)
        assertThat(payload.schemaVersion).isEqualTo(1)
        assertThat(payload.prompts).hasSize(1)
        assertThat(payload.prompts[0].title).isEqualTo("Sample")
        assertThat(payload.prompts[0].body).isEqualTo("1girl, {artist:test}")
        assertThat(payload.combos).hasSize(1)
        assertThat(payload.favoriteArtistIds).containsExactly(1L, 2L).inOrder()
        val decoded = TagEntryJson.decode(payload.combos[0].entriesJson)
        assertThat(decoded).hasSize(2)
        assertThat(decoded[1].numericWeight).isEqualTo(1.1)
    }

    @Test
    fun tagEntryJson_preservesFields() {
        val entries = listOf(
            TagEntry(
                id = "abc",
                tag = "artist:x",
                bracketCount = 2,
                numericWeight = null,
                enabled = false,
                forceArtistPrefix = true,
            ),
        )
        val round = TagEntryJson.decode(TagEntryJson.encode(entries))
        assertThat(round).hasSize(1)
        assertThat(round[0].id).isEqualTo("abc")
        assertThat(round[0].bracketCount).isEqualTo(2)
        assertThat(round[0].enabled).isFalse()
        assertThat(round[0].forceArtistPrefix).isTrue()
    }
}
