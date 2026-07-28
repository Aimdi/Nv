package dev.naicompanion.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.naicompanion.app.core.prompt.NovelAiModel
import dev.naicompanion.app.data.catalog.CatalogSort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var scope: CoroutineScope
    private lateinit var directory: File
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(dispatcher + SupervisorJob())
        directory = Files.createTempDirectory("settings-test").toFile()
        repository = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(directory, "settings.preferences_pb") },
            ),
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun `defaults target the newest model with underscores converted`() = runTest(dispatcher) {
        val settings = repository.settings.first()
        assertThat(settings.model).isEqualTo(NovelAiModel.V4_5)
        assertThat(settings.underscoresToSpaces).isTrue()
        assertThat(settings.artistPrefix).isTrue()
        assertThat(settings.browserColumns).isEqualTo(3)
        assertThat(settings.onlineTagSearch).isTrue()
        assertThat(settings.onlineArtistPreviews).isTrue()
        assertThat(settings.allowNsfwTags).isFalse()
    }

    @Test
    fun `changes round trip`() = runTest(dispatcher) {
        repository.setModel(NovelAiModel.V3)
        repository.setUnderscoresToSpaces(false)
        repository.setMultilineSeparator(true)
        repository.setBrowserSort(CatalogSort.NAME_ASC)
        repository.setCopyOpensNovelAi(true)
        repository.setOnlineTagSearch(false)
        repository.setAllowNsfwTags(true)

        val settings = repository.settings.first()
        assertThat(settings.model).isEqualTo(NovelAiModel.V3)
        assertThat(settings.underscoresToSpaces).isFalse()
        assertThat(settings.multilineSeparator).isTrue()
        assertThat(settings.browserSort).isEqualTo(CatalogSort.NAME_ASC)
        assertThat(settings.copyOpensNovelAi).isTrue()
        assertThat(settings.onlineTagSearch).isFalse()
        assertThat(settings.allowNsfwTags).isTrue()
    }

    @Test
    fun `render options follow the settings`() = runTest(dispatcher) {
        repository.setMultilineSeparator(true)
        repository.setModel(NovelAiModel.V4)

        val options = repository.settings.first().renderOptions
        assertThat(options.separator).isEqualTo(",\n")
        assertThat(options.model).isEqualTo(NovelAiModel.V4)
        assertThat(options.model.supportsArtistPrefix).isTrue()
        assertThat(options.model.supportsNegativeWeights).isFalse()
    }

    @Test
    fun `grid columns are clamped to what fits on a phone`() = runTest(dispatcher) {
        repository.setBrowserColumns(99)
        assertThat(repository.settings.first().browserColumns).isEqualTo(5)
        repository.setBrowserColumns(0)
        assertThat(repository.settings.first().browserColumns).isEqualTo(2)
    }

    @Test
    fun `a blank pack url falls back to the default`() = runTest(dispatcher) {
        repository.setPackManifestUrl("   ")
        assertThat(repository.settings.first().packManifestUrl)
            .isEqualTo(AppSettings.DEFAULT_PACK_MANIFEST_URL)

        repository.setPackManifestUrl(" https://example.invalid/packs.json ")
        assertThat(repository.settings.first().packManifestUrl)
            .isEqualTo("https://example.invalid/packs.json")
    }

    @Test
    fun `an unrecognised stored value falls back rather than crashing`() = runTest(dispatcher) {
        // Simulates a downgrade after a newer build wrote an enum value this one does not know.
        repository.setBrowserSort(CatalogSort.RANDOM)
        assertThat(repository.settings.first().browserSort).isEqualTo(CatalogSort.RANDOM)
        assertThat(NovelAiModel.fromStorage("SOMETHING_NEW")).isEqualTo(NovelAiModel.V4_5)
    }
}
