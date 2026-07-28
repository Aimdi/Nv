package dev.naicompanion.app.ui.builder

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.naicompanion.app.core.prompt.NovelAiModel
import dev.naicompanion.app.core.prompt.TagKind
import dev.naicompanion.app.data.catalog.CatalogDatabase
import dev.naicompanion.app.data.remote.DanbooruApi
import dev.naicompanion.app.data.remote.DanbooruRepository
import dev.naicompanion.app.data.repository.CatalogRepository
import dev.naicompanion.app.data.repository.ComboRepository
import dev.naicompanion.app.data.repository.DraftRepository
import dev.naicompanion.app.data.repository.PromptRepository
import dev.naicompanion.app.data.settings.SettingsRepository
import dev.naicompanion.app.data.user.UserDatabase
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuilderViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var userDatabase: UserDatabase
    private lateinit var catalogDatabase: CatalogDatabase
    private lateinit var draftRepository: DraftRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var comboRepository: ComboRepository
    private lateinit var promptRepository: PromptRepository

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStoreDir: File
    private lateinit var mockWebServer: MockWebServer
    private lateinit var danbooruRepository: DanbooruRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()

        // Room defaults to its own thread pools, which the test scheduler cannot see; pointing it
        // at the test dispatcher makes advanceUntilIdle() actually wait for database work.
        val executor = dispatcher.asExecutor()
        userDatabase = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            // The test dispatcher is also Dispatchers.Main here, so Room's main-thread guard has
            // to be relaxed for its executors to be allowed to run at all.
            .allowMainThreadQueries()
            .setQueryExecutor(executor)
            .setTransactionExecutor(executor)
            .build()
        catalogDatabase = Room.databaseBuilder(
            context,
            CatalogDatabase::class.java,
            "catalog-under-test.db",
        )
            .createFromAsset(CatalogDatabase.ASSET_PATH)
            .allowMainThreadQueries()
            .setQueryExecutor(executor)
            .setTransactionExecutor(executor)
            .build()

        // Each test gets its own DataStore files; the production property delegates are
        // process-global and would carry a draft from one test into the next.
        dataStoreScope = CoroutineScope(dispatcher + SupervisorJob())
        dataStoreDir = Files.createTempDirectory("builder-vm-test").toFile()
        draftRepository = DraftRepository(preferenceStore("draft"))
        settingsRepository = SettingsRepository(preferenceStore("settings"))

        comboRepository = ComboRepository(userDatabase.comboDao())
        promptRepository = PromptRepository(userDatabase.promptDao())

        mockWebServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse().setBody("[]")
            }
            server.start()
        }
        val json = Json { ignoreUnknownKeys = true }
        danbooruRepository = DanbooruRepository(
            Retrofit.Builder()
                .baseUrl(mockWebServer.url("/"))
                .client(OkHttpClient())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(DanbooruApi::class.java),
        )
    }

    private fun preferenceStore(name: String) = PreferenceDataStoreFactory.create(
        scope = dataStoreScope,
        produceFile = { File(dataStoreDir, "$name.preferences_pb") },
    )

    @After
    fun tearDown() {
        userDatabase.close()
        catalogDatabase.close()
        context.getDatabasePath("catalog-under-test.db").delete()
        dataStoreScope.cancel()
        dataStoreDir.deleteRecursively()
        mockWebServer.shutdown()
        Dispatchers.resetMain()
    }

    private fun newViewModel() = BuilderViewModel(
        draftRepository = draftRepository,
        settingsRepository = settingsRepository,
        catalogRepository = CatalogRepository(catalogDatabase.catalogDao()),
        comboRepository = comboRepository,
        promptRepository = promptRepository,
        danbooruRepository = danbooruRepository,
    )

    /** uiState is a WhileSubscribed flow, so tests need a live collector to see updates. */
    private fun TestScope.collecting(viewModel: BuilderViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    @Test
    fun `adding tags renders them in order`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.addTag("1girl")
        viewModel.addTag("solo")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.rendered).isEqualTo("1girl, solo")
        assertThat(viewModel.uiState.value.enabledCount).isEqualTo(2)
    }

    @Test
    fun `typed input containing commas expands into separate tags`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.addFromInput("1girl, {{smile}}, 1.5::artist:wlop::")
        advanceUntilIdle()

        val entries = viewModel.uiState.value.entries
        assertThat(entries.map { it.tag }).containsExactly("1girl", "smile", "wlop").inOrder()
        assertThat(entries[1].bracketCount).isEqualTo(2)
        assertThat(entries[2].kind).isEqualTo(TagKind.ARTIST)
        assertThat(entries[2].numericWeight).isEqualTo(1.5)
    }

    @Test
    fun `duplicate tags are refused`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.addTag("1girl")
        viewModel.addTag("1GIRL")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.entries).hasSize(1)
    }

    @Test
    fun `emphasis controls change the rendered output`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.addTag("smile")
        advanceUntilIdle()
        val id = viewModel.uiState.value.entries.first().id

        viewModel.stepBracketCount(id, 2)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rendered).isEqualTo("{{smile}}")

        viewModel.setNumericWeight(id, 1.3)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rendered).isEqualTo("1.3::{{smile}}::")

        viewModel.setBracketCount(id, 0)
        viewModel.setNumericWeight(id, null)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rendered).isEqualTo("smile")
    }

    @Test
    fun `bracket steps are clamped`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.addTag("smile")
        advanceUntilIdle()
        val id = viewModel.uiState.value.entries.first().id

        repeat(30) { viewModel.stepBracketCount(id, 1) }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.entries.first().bracketCount).isEqualTo(10)

        repeat(60) { viewModel.stepBracketCount(id, -1) }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.entries.first().bracketCount).isEqualTo(-10)
    }

    @Test
    fun `disabling a tag keeps it but drops it from the output`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.addTag("1girl")
        viewModel.addTag("solo")
        advanceUntilIdle()
        val id = viewModel.uiState.value.entries.last().id

        viewModel.toggleEnabled(id)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.entries).hasSize(2)
        assertThat(viewModel.uiState.value.rendered).isEqualTo("1girl")
    }

    @Test
    fun `moving a tag reorders the output`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.addFromInput("a, b, c")
        advanceUntilIdle()

        viewModel.move(2, 0)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.rendered).isEqualTo("c, a, b")
    }

    @Test
    fun `moving outside the list is ignored`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.addFromInput("a, b")
        advanceUntilIdle()

        viewModel.move(0, 5)
        viewModel.move(-1, 0)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.rendered).isEqualTo("a, b")
    }

    @Test
    fun `the render follows the model setting`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.addTag("wlop", TagKind.ARTIST)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rendered).isEqualTo("artist:wlop")

        settingsRepository.setModel(NovelAiModel.V3)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rendered).isEqualTo("wlop")
    }

    @Test
    fun `warnings surface duplicates from a pasted prompt`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.loadFromText("1girl, solo, 1girl")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.warnings).isNotEmpty()
    }

    @Test
    fun `the draft is restored into a fresh view model`() = runTest(dispatcher) {
        val first = newViewModel()
        collecting(first)
        advanceUntilIdle()

        first.addFromInput("1girl, {{smile}}")
        first.setComboName("Work in progress")
        // The draft is written on a debounce, so let the timer run out.
        advanceUntilIdle()

        assertThat(draftRepository.draft.first().map { it.tag })
            .containsExactly("1girl", "smile").inOrder()

        val second = newViewModel()
        collecting(second)
        advanceUntilIdle()

        assertThat(second.uiState.value.rendered).isEqualTo("1girl, {{smile}}")
        assertThat(second.uiState.value.comboName).isEqualTo("Work in progress")
    }

    @Test
    fun `saving a combo stores it and loading it back restores the tags`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.addFromInput("1.4::artist:wlop::, 1girl, [blurry]")
        advanceUntilIdle()
        viewModel.saveCombo("Signature")
        advanceUntilIdle()

        val saved = comboRepository.observeAll().first().single()
        assertThat(saved.name).isEqualTo("Signature")

        viewModel.clear()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isEmpty).isTrue()

        viewModel.loadCombo(saved.id)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rendered)
            .isEqualTo("1.4::artist:wlop::, 1girl, [blurry]")
    }

    @Test
    fun `saving an already saved combo updates it rather than duplicating`() =
        runTest(dispatcher) {
            val viewModel = newViewModel()
            collecting(viewModel)
            advanceUntilIdle()

            viewModel.addTag("1girl")
            advanceUntilIdle()
            viewModel.saveCombo("Look")
            advanceUntilIdle()

            viewModel.addTag("solo")
            advanceUntilIdle()
            viewModel.saveCombo("Look")
            advanceUntilIdle()

            val combos = comboRepository.observeAll().first()
            assertThat(combos).hasSize(1)
            assertThat(combos.single().entries.map { it.tag })
                .containsExactly("1girl", "solo").inOrder()
        }

    @Test
    fun `saving to the library stores the rendered text`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.addFromInput("1girl, {{smile}}")
        advanceUntilIdle()
        viewModel.saveAsPrompt("My prompt")
        advanceUntilIdle()

        val prompt = promptRepository.observeAll().first().single()
        assertThat(prompt.title).isEqualTo("My prompt")
        assertThat(prompt.body).isEqualTo("1girl, {{smile}}")
    }

    @Test
    fun `catalog suggestions come from the bundled tag catalog`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        backgroundScope.launch { viewModel.suggestions.collect {} }
        advanceUntilIdle()

        viewModel.onSearchQueryChange("wlop")
        advanceUntilIdle()

        assertThat(viewModel.suggestions.value.map { it.name }).contains("wlop")
    }

    @Test
    fun `picking a catalog suggestion adds it as an artist tag`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        backgroundScope.launch { viewModel.suggestions.collect {} }
        advanceUntilIdle()

        viewModel.onSearchQueryChange("wlop")
        advanceUntilIdle()
        viewModel.addSuggestion(viewModel.suggestions.value.first { it.name == "wlop" })
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.rendered).isEqualTo("artist:wlop")
        assertThat(viewModel.searchQuery.value).isEmpty()
    }

    @Test
    fun `clearing empties the builder`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.addFromInput("a, b, c")
        advanceUntilIdle()
        viewModel.clear()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isEmpty).isTrue()
        assertThat(viewModel.uiState.value.rendered).isEmpty()
    }
}
