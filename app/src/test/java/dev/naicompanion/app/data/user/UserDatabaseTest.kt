package dev.naicompanion.app.data.user

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.naicompanion.app.core.prompt.TagEntry
import dev.naicompanion.app.core.prompt.TagKind
import dev.naicompanion.app.data.repository.ComboRepository
import dev.naicompanion.app.data.repository.FavoriteTagRepository
import dev.naicompanion.app.data.repository.PromptRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserDatabaseTest {

    private lateinit var database: UserDatabase
    private lateinit var prompts: PromptRepository
    private lateinit var combos: ComboRepository
    private lateinit var favorites: FavoriteTagRepository

    private var clock = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prompts = PromptRepository(database.promptDao()) { clock }
        combos = ComboRepository(database.comboDao()) { clock }
        favorites = FavoriteTagRepository(database.favoriteTagDao()) { clock }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `prompts round trip`() = runTest {
        val id = prompts.create(title = "Sunset girl", body = "1girl, sunset, {{smile}}")
        val stored = prompts.findById(id)
        assertThat(stored).isNotNull()
        assertThat(stored!!.title).isEqualTo("Sunset girl")
        assertThat(stored.body).isEqualTo("1girl, sunset, {{smile}}")
        assertThat(stored.createdAt).isEqualTo(1_000L)
    }

    @Test
    fun `a blank title falls back to the leading tags`() = runTest {
        val id = prompts.create(title = "", body = "1girl, solo, smile, extra, more")
        assertThat(prompts.findById(id)!!.title).isEqualTo("1girl, solo, smile")
    }

    @Test
    fun `favorites sort ahead of everything else`() = runTest {
        prompts.create(title = "Older", body = "a")
        clock = 2_000L
        val newer = prompts.create(title = "Newer", body = "b")
        clock = 3_000L
        val starred = prompts.create(title = "Starred", body = "c")
        prompts.setFavorite(starred, true)

        val all = prompts.observeAll().first()
        assertThat(all.first().title).isEqualTo("Starred")
        assertThat(all.map { it.id }).contains(newer)
    }

    @Test
    fun `prompt search matches a whole word via fts`() = runTest {
        prompts.create(title = "Beach scene", body = "1girl, ocean, sand")
        prompts.create(title = "Forest scene", body = "1girl, trees")

        val results = prompts.search("ocean").first()
        assertThat(results.map { it.title }).containsExactly("Beach scene")
    }

    @Test
    fun `prompt search matches a partial word via the like fallback`() = runTest {
        prompts.create(title = "Beach scene", body = "1girl, ocean, sand")
        val results = prompts.search("oce").first()
        assertThat(results.map { it.title }).containsExactly("Beach scene")
    }

    @Test
    fun `prompt search matches the title as well as the body`() = runTest {
        prompts.create(title = "Cyberpunk alley", body = "1girl")
        assertThat(prompts.search("cyberpunk").first()).hasSize(1)
    }

    @Test
    fun `an empty search returns everything`() = runTest {
        prompts.create(title = "One", body = "a")
        prompts.create(title = "Two", body = "b")
        assertThat(prompts.search("   ").first()).hasSize(2)
    }

    @Test
    fun `deleting a prompt removes it from search`() = runTest {
        val id = prompts.create(title = "Temporary", body = "throwaway")
        prompts.delete(id)
        assertThat(prompts.search("throwaway").first()).isEmpty()
        assertThat(prompts.findById(id)).isNull()
    }

    @Test
    fun `combos preserve tag order weights and kinds`() = runTest {
        val entries = listOf(
            TagEntry(tag = "wlop", kind = TagKind.ARTIST, numericWeight = 1.4),
            TagEntry(tag = "1girl"),
            TagEntry(tag = "detailed_background", bracketCount = 2),
            TagEntry(tag = "blurry", bracketCount = -1, enabled = false),
        )
        val id = combos.save("My look", entries)

        val loaded = combos.findById(id)!!
        assertThat(loaded.name).isEqualTo("My look")
        assertThat(loaded.entries.map { it.tag })
            .containsExactly("wlop", "1girl", "detailed_background", "blurry").inOrder()
        assertThat(loaded.entries[0].kind).isEqualTo(TagKind.ARTIST)
        assertThat(loaded.entries[0].numericWeight).isEqualTo(1.4)
        assertThat(loaded.entries[2].bracketCount).isEqualTo(2)
        assertThat(loaded.entries[3].enabled).isFalse()
    }

    @Test
    fun `saving over a combo replaces its tags rather than appending`() = runTest {
        val id = combos.save("Look", listOf(TagEntry(tag = "a"), TagEntry(tag = "b")))
        combos.save("Look v2", listOf(TagEntry(tag = "c")), id)

        val loaded = combos.findById(id)!!
        assertThat(loaded.name).isEqualTo("Look v2")
        assertThat(loaded.entries.map { it.tag }).containsExactly("c")
        assertThat(combos.observeAll().first()).hasSize(1)
    }

    @Test
    fun `reordering a combo persists the new order`() = runTest {
        val id = combos.save("Look", listOf(TagEntry(tag = "a"), TagEntry(tag = "b")))
        val reversed = combos.findById(id)!!.entries.reversed()
        combos.save("Look", reversed, id)
        assertThat(combos.findById(id)!!.entries.map { it.tag })
            .containsExactly("b", "a").inOrder()
    }

    @Test
    fun `deleting a combo cascades to its tags`() = runTest {
        val id = combos.save("Look", listOf(TagEntry(tag = "a"), TagEntry(tag = "b")))
        combos.delete(id)
        assertThat(combos.findById(id)).isNull()
        val remaining = database.query("SELECT COUNT(*) FROM combo_tags", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assertThat(remaining).isEqualTo(0)
    }

    @Test
    fun `saving a combo keeps its original creation time`() = runTest {
        val id = combos.save("Look", listOf(TagEntry(tag = "a")))
        clock = 5_000L
        combos.save("Look", listOf(TagEntry(tag = "b")), id)
        val loaded = combos.findById(id)!!
        assertThat(loaded.createdAt).isEqualTo(1_000L)
        assertThat(loaded.updatedAt).isEqualTo(5_000L)
    }

    @Test
    fun `favorite tags toggle on and off`() = runTest {
        assertThat(favorites.toggle("wlop", TagKind.ARTIST)).isTrue()
        assertThat(favorites.favoriteNames.first()).containsExactly("wlop")
        assertThat(favorites.toggle("wlop", TagKind.ARTIST)).isFalse()
        assertThat(favorites.favoriteNames.first()).isEmpty()
    }

    @Test
    fun `favoriting the same tag twice does not duplicate it`() = runTest {
        favorites.add("wlop", TagKind.ARTIST)
        favorites.add("wlop", TagKind.ARTIST)
        assertThat(favorites.observeAll().first()).hasSize(1)
    }
}
