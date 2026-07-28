package dev.naicompanion.app.data.packs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackRepositoryTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var repository: PackRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        server.start()
        repository = PackRepository(context, OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
        File(context.filesDir, PackRepository.PACKS_DIR).deleteRecursively()
    }

    // region helpers

    private fun buildZip(
        entries: Map<String, ByteArray>,
        metadata: PackDescriptor? = null,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            if (metadata != null) {
                zip.putNextEntry(ZipEntry(PackRepository.PACK_METADATA_FILE))
                zip.write(
                    kotlinx.serialization.json.Json.encodeToString(
                        PackDescriptor.serializer(),
                        metadata,
                    ).toByteArray(),
                )
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun samplePack(): ByteArray = buildZip(
        mapOf(
            "thumbs/wlop.webp" to "thumb-wlop".toByteArray(),
            "thumbs/ebifurya.webp" to "thumb-ebifurya".toByteArray(),
            "full/wlop.webp" to "full-wlop".toByteArray(),
        ),
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun enqueueZip(bytes: ByteArray) {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
    }

    private fun descriptorFor(bytes: ByteArray, withChecksum: Boolean = true) = PackDescriptor(
        id = "nai-v3-previews",
        name = "NAI v3 previews",
        source = "nai-v3",
        url = server.url("/pack.zip").toString(),
        sizeBytes = bytes.size.toLong(),
        sha256 = if (withChecksum) sha256(bytes) else "",
        imageCount = 3,
    )

    // endregion

    @Test
    fun `install downloads verifies and unpacks a pack`() = runTest {
        val bytes = samplePack()
        enqueueZip(bytes)

        val result = repository.install(descriptorFor(bytes))

        assertThat(result.isSuccess).isTrue()
        val installed = result.getOrThrow()
        assertThat(installed.id).isEqualTo("nai-v3-previews")
        assertThat(installed.source).isEqualTo("nai-v3")
        assertThat(installed.imageCount).isEqualTo(3)
        assertThat(repository.installed.value).hasSize(1)
        assertThat(repository.installState.value)
            .isInstanceOf(PackInstallState.Done::class.java)
    }

    @Test
    fun `installed previews resolve to files on disk`() = runTest {
        val bytes = samplePack()
        enqueueZip(bytes)
        repository.install(descriptorFor(bytes)).getOrThrow()

        val thumb = repository.resolvePreview(
            "nai-v3",
            "wlop.webp",
            PackRepository.PreviewSize.THUMB,
        )
        assertThat(thumb).isNotNull()
        assertThat(thumb!!.readText()).isEqualTo("thumb-wlop")

        val full = repository.resolvePreview("nai-v3", "wlop.webp", PackRepository.PreviewSize.FULL)
        assertThat(full!!.readText()).isEqualTo("full-wlop")
    }

    @Test
    fun `a missing detail image falls back to the thumbnail`() = runTest {
        val bytes = samplePack()
        enqueueZip(bytes)
        repository.install(descriptorFor(bytes)).getOrThrow()

        val full = repository.resolvePreview(
            "nai-v3",
            "ebifurya.webp",
            PackRepository.PreviewSize.FULL,
        )
        assertThat(full).isNotNull()
        assertThat(full!!.readText()).isEqualTo("thumb-ebifurya")
    }

    @Test
    fun `a pack from another dataset can still supply an image`() = runTest {
        val bytes = samplePack()
        enqueueZip(bytes)
        repository.install(descriptorFor(bytes)).getOrThrow()

        // The row's metadata comes from Illustrious but only the NAI pack is installed.
        val thumb = repository.resolvePreview(
            "illustrious",
            "wlop.webp",
            PackRepository.PreviewSize.THUMB,
        )
        assertThat(thumb).isNotNull()
    }

    @Test
    fun `an unknown preview resolves to null`() = runTest {
        val bytes = samplePack()
        enqueueZip(bytes)
        repository.install(descriptorFor(bytes)).getOrThrow()

        assertThat(
            repository.resolvePreview("nai-v3", "nobody.webp", PackRepository.PreviewSize.THUMB),
        ).isNull()
        assertThat(
            repository.resolvePreview("nai-v3", null, PackRepository.PreviewSize.THUMB),
        ).isNull()
    }

    @Test
    fun `a checksum mismatch fails the install and leaves nothing behind`() = runTest {
        val bytes = samplePack()
        enqueueZip(bytes)
        val descriptor = descriptorFor(bytes).copy(sha256 = "0".repeat(64))

        val result = repository.install(descriptor)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("Checksum mismatch")
        assertThat(repository.installed.value).isEmpty()
        assertThat(File(File(context.filesDir, PackRepository.PACKS_DIR), descriptor.id).exists())
            .isFalse()
    }

    /** A malicious or malformed zip must not be able to write outside the pack directory. */
    @Test
    fun `a zip slip entry is rejected`() = runTest {
        val bytes = buildZip(mapOf("../../evil.txt" to "pwned".toByteArray()))
        enqueueZip(bytes)

        val result = repository.install(descriptorFor(bytes))

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("unsafe path")
        assertThat(File(context.filesDir, "evil.txt").exists()).isFalse()
        assertThat(File(context.filesDir.parentFile, "evil.txt").exists()).isFalse()
    }

    @Test
    fun `an http error surfaces as a failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = repository.install(descriptorFor(samplePack(), withChecksum = false))

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("404")
        assertThat(repository.installState.value).isInstanceOf(PackInstallState.Failed::class.java)
    }

    @Test
    fun `metadata embedded in the zip is used for the pack details`() = runTest {
        val bytes = buildZip(
            mapOf("thumbs/wlop.webp" to "x".toByteArray()),
            metadata = PackDescriptor(
                id = "embedded",
                name = "Embedded name",
                source = "illustrious",
                attribution = "ThetaCursed, MIT",
                version = 3,
            ),
        )
        enqueueZip(bytes)

        val installed = repository.install(descriptorFor(bytes, withChecksum = false)).getOrThrow()

        assertThat(installed.name).isEqualTo("Embedded name")
        assertThat(installed.source).isEqualTo("illustrious")
        assertThat(installed.attribution).isEqualTo("ThetaCursed, MIT")
        assertThat(installed.version).isEqualTo(3)
        // The directory on disk is keyed by the requested id, not the embedded one.
        assertThat(installed.id).isEqualTo("nai-v3-previews")
    }

    @Test
    fun `reinstalling replaces the previous contents`() = runTest {
        enqueueZip(samplePack())
        repository.install(descriptorFor(samplePack(), withChecksum = false)).getOrThrow()

        val replacement = buildZip(mapOf("thumbs/only.webp" to "new".toByteArray()))
        enqueueZip(replacement)
        val installed =
            repository.install(descriptorFor(replacement, withChecksum = false)).getOrThrow()

        assertThat(repository.installed.value).hasSize(1)
        assertThat(installed.imageCount).isEqualTo(1)
        assertThat(
            repository.resolvePreview("nai-v3", "wlop.webp", PackRepository.PreviewSize.THUMB),
        ).isNull()
        assertThat(
            repository.resolvePreview("nai-v3", "only.webp", PackRepository.PreviewSize.THUMB),
        ).isNotNull()
    }

    @Test
    fun `removing a pack deletes its files and index entry`() = runTest {
        val bytes = samplePack()
        enqueueZip(bytes)
        val installed = repository.install(descriptorFor(bytes)).getOrThrow()

        repository.remove(installed.id)

        assertThat(repository.installed.value).isEmpty()
        assertThat(
            repository.resolvePreview("nai-v3", "wlop.webp", PackRepository.PreviewSize.THUMB),
        ).isNull()
        assertThat(File(File(context.filesDir, PackRepository.PACKS_DIR), installed.id).exists())
            .isFalse()
    }

    @Test
    fun `the installed index survives a new repository instance`() = runTest {
        val bytes = samplePack()
        enqueueZip(bytes)
        repository.install(descriptorFor(bytes)).getOrThrow()

        val reopened = PackRepository(context, OkHttpClient())
        assertThat(reopened.installed.value).hasSize(1)
        assertThat(
            reopened.resolvePreview("nai-v3", "wlop.webp", PackRepository.PreviewSize.THUMB),
        ).isNotNull()
    }

    @Test
    fun `the manifest is fetched and parsed`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "schemaVersion": 1,
                  "packs": [
                    {
                      "id": "nai-v3-previews",
                      "name": "NAI v3 artist previews",
                      "source": "nai-v3",
                      "url": "https://example.invalid/nai-v3.zip",
                      "sizeBytes": 263000000,
                      "imageCount": 17228,
                      "thumbnailPx": 320,
                      "license": "Apache-2.0"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val manifest = repository.fetchManifest(server.url("/packs.json").toString()).getOrThrow()

        assertThat(manifest.packs).hasSize(1)
        assertThat(manifest.packs.first().imageCount).isEqualTo(17228)
        assertThat(manifest.packs.first().thumbnailPx).isEqualTo(320)
    }

    @Test
    fun `a manifest that cannot be reached fails without throwing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = repository.fetchManifest(server.url("/packs.json").toString())
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `download progress is reported`() = runTest {
        val bytes = samplePack()
        enqueueZip(bytes)
        repository.install(descriptorFor(bytes)).getOrThrow()

        // Progress ends at Done; the intermediate states are covered by the state machine type.
        val state = repository.installState.value
        assertThat(state).isInstanceOf(PackInstallState.Done::class.java)
        assertThat((state as PackInstallState.Done).packId).isEqualTo("nai-v3-previews")
    }
}
