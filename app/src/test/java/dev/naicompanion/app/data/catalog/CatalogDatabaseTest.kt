package dev.naicompanion.app.data.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.naicompanion.app.data.repository.CatalogRepository
import dev.naicompanion.app.data.repository.CatalogStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real prebuilt catalog asset through Room on the JVM.
 *
 * This is the test that matters most for the catalog: a prebuilt database is rejected at runtime
 * if its identity hash, DDL or indices drift from what Room's annotation processor expects, and
 * that failure would otherwise only show up on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogDatabaseTest {

    private lateinit var database: CatalogDatabase
    private lateinit var dao: CatalogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = CatalogDatabase.build(context)
        dao = database.catalogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `room opens the bundled asset and it is populated`() = runTest {
        assertThat(dao.count()).isGreaterThan(10_000)
    }

    @Test
    fun `every row carries the fields the browser renders`() = runTest {
        val rows = dao.search(CatalogQueryBuilder.build(CatalogQuery(limit = 200)))
        assertThat(rows).isNotEmpty()
        rows.forEach { row ->
            assertThat(row.name).isNotEmpty()
            assertThat(row.displayName).isNotEmpty()
            assertThat(row.displayName).doesNotContain("_")
            assertThat(row.kind).isEqualTo("ARTIST")
            assertThat(row.preview).isNotNull()
            assertThat(row.sources).startsWith("|")
            assertThat(row.sources).endsWith("|")
        }
    }

    @Test
    fun `default sort returns the most used artists first`() = runTest {
        val rows = dao.search(CatalogQueryBuilder.build(CatalogQuery(limit = 50)))
        val counts = rows.map { it.postCount }
        assertThat(counts).isInOrder(compareByDescending<Int> { it })
    }

    @Test
    fun `full token search finds an exact artist`() = runTest {
        val rows = dao.search(CatalogQueryBuilder.build(CatalogQuery(text = "wlop")))
        assertThat(rows.map { it.name }).contains("wlop")
        assertThat(rows.first().name).isEqualTo("wlop")
    }

    /** Regression test: FTS4 ignores the prefix operator on a quoted phrase. */
    @Test
    fun `partial token search matches as you type`() = runTest {
        listOf("w", "wl", "wlo", "wlop").forEach { partial ->
            val rows = dao.search(CatalogQueryBuilder.build(CatalogQuery(text = partial)))
            assertThat(rows.map { it.name }).contains("wlop")
        }
    }

    @Test
    fun `mid word search still matches via the like fallback`() = runTest {
        val rows = dao.search(CatalogQueryBuilder.build(CatalogQuery(text = "kazunori")))
        assertThat(rows.map { it.name }).contains("haruyama_kazunori")
    }

    @Test
    fun `multi token search matches across a qualified tag`() = runTest {
        val rows = dao.search(CatalogQueryBuilder.build(CatalogQuery(text = "sunset bea")))
        assertThat(rows.map { it.name }).contains("hammer_(sunset_beach)")
    }

    @Test
    fun `search accepts punctuation without breaking fts syntax`() = runTest {
        listOf("hammer (sunset", "\"quoted\"", "a*b", "^caret", "NEAR", "OR", "-dash", "50%")
            .forEach { input ->
                // The assertion is that these do not throw; FTS syntax errors surface as crashes.
                dao.search(CatalogQueryBuilder.build(CatalogQuery(text = input)))
            }
    }

    /**
     * An unescaped `%` would turn the LIKE fallback into "match everything". The catalog happens
     * to contain a tag with a literal percent sign, so a correct implementation returns just that.
     */
    @Test
    fun `like wildcards in a query are treated literally`() = runTest {
        val total = dao.count()
        val percent = dao.search(CatalogQueryBuilder.build(CatalogQuery(text = "%")))
        assertThat(percent).isNotEmpty()
        assertThat(percent.size).isLessThan(total)
        percent.forEach { assertThat(it.name).contains("%") }

        val underscore = dao.search(CatalogQueryBuilder.build(CatalogQuery(text = "_", limit = 20)))
        underscore.forEach { assertThat(it.name).contains("_") }
    }

    @Test
    fun `source filter narrows to a single dataset`() = runTest {
        val illustriousOnly = dao.search(
            CatalogQueryBuilder.build(CatalogQuery(sources = setOf("illustrious"), limit = 500)),
        )
        assertThat(illustriousOnly).isNotEmpty()
        illustriousOnly.forEach { assertThat(it.sources).contains("|illustrious|") }

        val naiOnly = dao.search(
            CatalogQueryBuilder.build(CatalogQuery(sources = setOf("nai-v3"), limit = 500)),
        )
        assertThat(naiOnly).isNotEmpty()
        naiOnly.forEach { assertThat(it.sources).contains("|nai-v3|") }
    }

    @Test
    fun `source filter does not match a partial source name`() = runTest {
        val rows = dao.search(
            CatalogQueryBuilder.build(CatalogQuery(sources = setOf("nai"), limit = 10)),
        )
        assertThat(rows).isEmpty()
    }

    @Test
    fun `sort by name is alphabetical`() = runTest {
        val rows = dao.search(
            CatalogQueryBuilder.build(CatalogQuery(sort = CatalogSort.NAME_ASC, limit = 100)),
        )
        val names = rows.map { it.displayName.lowercase() }
        assertThat(names).isInOrder()
    }

    @Test
    fun `sort by uniqueness puts scored rows before unscored ones`() = runTest {
        val rows = dao.search(
            CatalogQueryBuilder.build(
                CatalogQuery(sort = CatalogSort.UNIQUENESS_DESC, limit = 200),
            ),
        )
        val firstNull = rows.indexOfFirst { it.uniqueness == null }
        if (firstNull >= 0) {
            assertThat(rows.drop(firstNull).all { it.uniqueness == null }).isTrue()
        }
        val scored = rows.mapNotNull { it.uniqueness }
        assertThat(scored).isInOrder(compareByDescending<Double> { it })
    }

    @Test
    fun `min post count filter is applied`() = runTest {
        val rows = dao.search(
            CatalogQueryBuilder.build(CatalogQuery(minPostCount = 1_000, limit = 500)),
        )
        assertThat(rows).isNotEmpty()
        rows.forEach { assertThat(it.postCount).isAtLeast(1_000) }
    }

    @Test
    fun `limit is respected`() = runTest {
        val rows = dao.search(CatalogQueryBuilder.build(CatalogQuery(limit = 7)))
        assertThat(rows).hasSize(7)
    }

    @Test
    fun `lookup by name works for the builder suggestion path`() = runTest {
        assertThat(dao.findByName("wlop")).isNotNull()
        assertThat(dao.findByName("definitely-not-an-artist-tag")).isNull()
    }

    @Test
    fun `repository reports the catalog as ready with its sources`() = runTest {
        val repository = CatalogRepository(dao)
        repository.refresh()
        val status = repository.status.value
        assertThat(status).isInstanceOf(CatalogStatus.Ready::class.java)
        status as CatalogStatus.Ready
        assertThat(status.tagCount).isGreaterThan(10_000)
        assertThat(status.sources).containsExactly("illustrious", "nai-v3")
    }

    @Test
    fun `repository search flow emits results`() = runTest {
        val repository = CatalogRepository(dao)
        val results = repository.observe(CatalogQuery(text = "wlop")).first()
        assertThat(results.map { it.name }).contains("wlop")
    }
}
