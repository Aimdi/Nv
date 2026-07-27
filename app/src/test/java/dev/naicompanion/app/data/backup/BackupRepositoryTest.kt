package dev.naicompanion.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.naicompanion.app.core.prompt.NovelAiModel
import dev.naicompanion.app.core.prompt.TagEntry
import dev.naicompanion.app.core.prompt.TagKind
import dev.naicompanion.app.data.repository.ComboRepository
import dev.naicompanion.app.data.repository.FavoriteTagRepository
import dev.naicompanion.app.data.repository.PromptRepository
import dev.naicompanion.app.data.settings.AppSettings
import dev.naicompanion.app.data.user.UserDatabase
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
class BackupRepositoryTest {

    private lateinit var database: UserDatabase
    private lateinit var backup: BackupRepository
    private lateinit var prompts: PromptRepository
    private lateinit var combos: ComboRepository
    private lateinit var favorites: FavoriteTagRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backup = BackupRepository(
            context = context,
            promptDao = database.promptDao(),
            comboDao = database.comboDao(),
            favoriteTagDao = database.favoriteTagDao(),
            appVersion = "0.1.0-test",
            now = { 42L },
        )
        prompts = PromptRepository(database.promptDao()) { 42L }
        combos = ComboRepository(database.comboDao()) { 42L }
        favorites = FavoriteTagRepository(database.favoriteTagDao()) { 42L }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seed() {
        prompts.create(title = "Sunset", body = "1girl, sunset", notes = "keep", folder = "scenes")
        prompts.create(title = "Portrait", body = "1girl, closeup")
        combos.save(
            "Signature look",
            listOf(
                TagEntry(tag = "wlop", kind = TagKind.ARTIST, numericWeight = 1.4),
                TagEntry(tag = "detailed_background", bracketCount = 2),
                TagEntry(tag = "blurry", bracketCount = -1, enabled = false),
            ),
        )
        favorites.add("wlop", TagKind.ARTIST)
        favorites.add("ebifurya", TagKind.ARTIST)
    }

    @Test
    fun `export then replace-import restores everything exactly`() = runTest {
        seed()
        val exported = backup.buildBackup(AppSettings(model = NovelAiModel.V4))
        val encoded = backup.encode(exported)

        // Wipe and restore from the encoded text, exactly as the file path would.
        val summary = backup.apply(backup.decode(encoded), ImportMode.REPLACE)
        assertThat(summary.prompts).isEqualTo(2)
        assertThat(summary.combos).isEqualTo(1)
        assertThat(summary.favoriteTags).isEqualTo(2)

        val restoredPrompts = prompts.observeAll().first()
        assertThat(restoredPrompts).hasSize(2)
        assertThat(restoredPrompts.map { it.title }).containsExactly("Sunset", "Portrait")
        assertThat(restoredPrompts.first { it.title == "Sunset" }.folder).isEqualTo("scenes")
        assertThat(restoredPrompts.first { it.title == "Sunset" }.notes).isEqualTo("keep")

        val restoredCombos = combos.observeAll().first()
        assertThat(restoredCombos).hasSize(1)
        val entries = restoredCombos.first().entries
        assertThat(entries.map { it.tag })
            .containsExactly("wlop", "detailed_background", "blurry").inOrder()
        assertThat(entries[0].kind).isEqualTo(TagKind.ARTIST)
        assertThat(entries[0].numericWeight).isEqualTo(1.4)
        assertThat(entries[1].bracketCount).isEqualTo(2)
        assertThat(entries[2].enabled).isFalse()

        assertThat(favorites.favoriteNames.first()).containsExactly("wlop", "ebifurya")
    }

    @Test
    fun `merge import adds to the existing data`() = runTest {
        seed()
        val encoded = backup.encode(backup.buildBackup(null))
        backup.apply(backup.decode(encoded), ImportMode.MERGE)

        assertThat(prompts.observeAll().first()).hasSize(4)
        assertThat(combos.observeAll().first()).hasSize(2)
        // Favourites are keyed by name, so re-importing them is idempotent.
        assertThat(favorites.favoriteNames.first()).hasSize(2)
    }

    @Test
    fun `replace import clears data that is not in the file`() = runTest {
        seed()
        val empty = BackupFile(schemaVersion = 1)
        val summary = backup.apply(empty, ImportMode.REPLACE)

        assertThat(summary.total).isEqualTo(0)
        assertThat(prompts.observeAll().first()).isEmpty()
        assertThat(combos.observeAll().first()).isEmpty()
        assertThat(favorites.favoriteNames.first()).isEmpty()
    }

    @Test
    fun `the encoded file carries schema and app metadata`() = runTest {
        seed()
        val encoded = backup.encode(backup.buildBackup(AppSettings()))
        assertThat(encoded).contains("\"schemaVersion\": 1")
        assertThat(encoded).contains("dev.naicompanion.app")
        assertThat(encoded).contains("\"appVersion\": \"0.1.0-test\"")
        assertThat(encoded).contains("\"exportedAt\": 42")
    }

    @Test
    fun `settings survive the round trip`() = runTest {
        val settings = AppSettings(
            model = NovelAiModel.V3,
            underscoresToSpaces = false,
            multilineSeparator = true,
            browserColumns = 4,
        )
        val decoded = backup.decode(backup.encode(backup.buildBackup(settings)))
        assertThat(decoded.settings?.model).isEqualTo("V3")
        assertThat(decoded.settings?.underscoresToSpaces).isFalse()
        assertThat(decoded.settings?.multilineSeparator).isTrue()
        assertThat(decoded.settings?.browserColumns).isEqualTo(4)
    }

    @Test
    fun `a file from a newer schema version is rejected with a clear message`() {
        val future = """{"schemaVersion": 99, "prompts": []}"""
        val error = runCatching { backup.decode(future) }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("newer version")
    }

    @Test
    fun `unknown fields from a future build are ignored`() {
        val decoded = backup.decode(
            """
            {
              "schemaVersion": 1,
              "somethingNew": {"a": 1},
              "prompts": [{"title": "T", "body": "B", "unexpected": true}]
            }
            """.trimIndent(),
        )
        assertThat(decoded.prompts).hasSize(1)
        assertThat(decoded.prompts.first().title).isEqualTo("T")
    }

    @Test
    fun `a minimal hand written file imports`() = runTest {
        val summary = backup.apply(
            backup.decode(
                """
                {
                  "schemaVersion": 1,
                  "prompts": [{"title": "Hand made", "body": "1girl"}],
                  "combos": [{"name": "C", "tags": [{"tag": "wlop", "kind": "ARTIST"}]}],
                  "favoriteTags": [{"name": "wlop"}]
                }
                """.trimIndent(),
            ),
            ImportMode.MERGE,
        )
        assertThat(summary.total).isEqualTo(3)
        val combo = combos.observeAll().first().first()
        assertThat(combo.entries.first().kind).isEqualTo(TagKind.ARTIST)
        // Timestamps default to import time rather than zero.
        assertThat(prompts.observeAll().first().first().createdAt).isEqualTo(42L)
    }

    @Test
    fun `malformed json fails without corrupting existing data`() = runTest {
        seed()
        val error = runCatching { backup.decode("not json at all") }.exceptionOrNull()
        assertThat(error).isNotNull()
        assertThat(prompts.observeAll().first()).hasSize(2)
    }

    @Test
    fun `the suggested file name is timestamped json`() {
        val name = backup.suggestedFileName()
        assertThat(name).startsWith("nai-companion-backup-")
        assertThat(name).endsWith(".json")
    }
}
