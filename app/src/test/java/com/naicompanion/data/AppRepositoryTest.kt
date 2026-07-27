package com.naicompanion.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.naicompanion.data.db.AppDatabase
import com.naicompanion.data.model.TagEntry
import com.naicompanion.render.PromptRenderer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real Room database (FTS4 triggers, docid joins, seeding from
 * the bundled 15,000-artist asset) on Android SQLite via Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `seeding imports full catalog and fts prefix search works`() = runBlocking {
        repository.ensureCatalogSeeded()

        assertEquals(15_000, db.artistDao().count())

        val results = repository.artists("mizuki", favoritesOnly = false, sortByName = false).first()
        assertTrue(results.any { it.artist.name == "mizuki_hitoshi" })

        // multi-token input ANDs prefixes
        val multi = repository.artists("mizuki hit", false, false).first()
        assertTrue(multi.any { it.artist.name == "mizuki_hitoshi" })

        // suggestions for the builder autocomplete
        val suggestions = repository.suggestions("mizuki")
        assertTrue(suggestions.any { it.artist.name == "mizuki_hitoshi" })

        // blank query falls back to the full list ordered by post count
        val all = repository.artists("", false, false).first()
        assertEquals(15_000, all.size)
        assertTrue(all.zipWithNext().all { (a, b) -> a.artist.postCount >= b.artist.postCount })

        // seeding twice is a no-op
        repository.ensureCatalogSeeded()
        assertEquals(15_000, db.artistDao().count())
    }

    @Test
    fun `artist favorites toggle and filter`() = runBlocking {
        repository.ensureCatalogSeeded()
        val artist = db.artistDao().findByName("mizuki_hitoshi")!!

        repository.toggleArtistFavorite(artist.id, currentlyFavorite = false)
        var item = repository.artists("mizuki hitoshi", false, false).first()
            .first { it.artist.id == artist.id }
        assertTrue(item.isFavorite)

        val favorites = repository.artists("", favoritesOnly = true, sortByName = false).first()
        assertEquals(listOf(artist.id), favorites.map { it.artist.id })

        repository.toggleArtistFavorite(artist.id, currentlyFavorite = true)
        item = repository.artists("mizuki hitoshi", false, false).first()
            .first { it.artist.id == artist.id }
        assertFalse(item.isFavorite)
    }

    @Test
    fun `prompt fts search and favorites`() = runBlocking {
        repository.savePrompt(null, "summer portrait", "1girl, {masterpiece}, beach")
        repository.savePrompt(null, "winter scene", "1girl, snow, [bad anatomy]")
        repository.savePrompt(null, "portrait sketch", "monochrome, sketch")

        val beach = repository.prompts("beach", false).first()
        assertEquals(listOf("summer portrait"), beach.map { it.title })

        val portraits = repository.prompts("portrait", false).first()
        assertEquals(2, portraits.size)

        val all = repository.prompts("", false).first()
        repository.togglePromptFavorite(all.first { it.title == "portrait sketch" }.id, false)
        val favs = repository.prompts("", true).first()
        assertEquals(listOf("portrait sketch"), favs.map { it.title })

        val favSearch = repository.prompts("portrait", true).first()
        assertEquals(listOf("portrait sketch"), favSearch.map { it.title })
    }

    @Test
    fun `combo entries round trip with order and weights`() = runBlocking {
        val entries = listOf(
            TagEntry(tag = "1girl"),
            TagEntry(tag = "artist:mizuki_hitoshi", bracketCount = 1),
            TagEntry(tag = "bad_hands", numericWeight = 0.5f, enabled = false),
        )
        val id = repository.saveCombo("test combo", entries)
        assertTrue(id > 0)

        val saved = repository.combos("", false).first().single()
        val decoded = repository.decodeEntries(saved.entriesJson)
        assertEquals(entries.map { it.tag }, decoded.map { it.tag })
        assertEquals(entries.map { it.bracketCount }, decoded.map { it.bracketCount })
        assertEquals(entries.map { it.numericWeight }, decoded.map { it.numericWeight })
        assertEquals(entries.map { it.enabled }, decoded.map { it.enabled })

        assertEquals(
            "1girl, {artist:mizuki hitoshi}",
            PromptRenderer.renderCombo(decoded),
        )
    }

    @Test
    fun `export then import restores data and dedupes on second import`() = runBlocking {
        repository.ensureCatalogSeeded()
        repository.savePrompt(null, "p1", "body one")
        repository.saveCombo("c1", listOf(TagEntry(tag = "1girl")))
        val artist = db.artistDao().findByName("mizuki_hitoshi")!!
        repository.toggleArtistFavorite(artist.id, false)

        val backupText = repository.exportBackup()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db2 = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo2 = AppRepository(context, db2)
        try {
            repo2.ensureCatalogSeeded()

            val result = repo2.importBackup(backupText)
            assertEquals(1, result.promptsImported)
            assertEquals(1, result.combosImported)
            assertEquals(1, result.favoritesImported)

            assertEquals("p1", repo2.prompts("", false).first().single().title)
            assertEquals("c1", repo2.combos("", false).first().single().title)
            val favs = repo2.artists("", true, false).first()
            assertEquals(listOf(artist.id), favs.map { it.artist.id })

            val second = repo2.importBackup(backupText)
            assertEquals(0, second.promptsImported)
            assertEquals(1, second.promptsSkipped)
            assertEquals(0, second.combosImported)
            assertEquals(1, second.combosSkipped)
        } finally {
            db2.close()
        }
    }

    @Test
    fun `fts query builder sanitizes and prefixes`() {
        assertNull(AppRepository.buildFtsQuery("   "))
        assertNull(AppRepository.buildFtsQuery("!!!"))
        assertEquals("mizuki*", AppRepository.buildFtsQuery("mizuki"))
        assertEquals("mizuki* hitoshi*", AppRepository.buildFtsQuery("mizuki hitoshi"))
        assertEquals("mizuki*", AppRepository.buildFtsQuery("mizuki_\""))
    }
}
