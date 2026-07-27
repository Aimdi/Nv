package dev.naicompanion.app

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import dev.naicompanion.app.core.prompt.NovelAiPromptParser
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomingTextTest {

    @Test
    fun `text shared from another app is picked up`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "1girl, {{smile}}")
        }
        assertThat(IncomingText.from(intent)).isEqualTo("1girl, {{smile}}")
    }

    @Test
    fun `text selected elsewhere and sent via process text is picked up`() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            putExtra(Intent.EXTRA_PROCESS_TEXT, "artist:wlop" as CharSequence)
        }
        assertThat(IncomingText.from(intent)).isEqualTo("artist:wlop")
    }

    @Test
    fun `a normal launch carries no text`() {
        assertThat(IncomingText.from(Intent(Intent.ACTION_MAIN))).isNull()
        assertThat(IncomingText.from(null)).isNull()
    }

    @Test
    fun `blank shared text is ignored`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "   ")
        }
        assertThat(IncomingText.from(intent)).isNull()
    }

    @Test
    fun `a shared prompt parses into builder tags`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "artist:wlop, 1girl, 1.5::ocean::")
        }
        val parsed = NovelAiPromptParser.parse(IncomingText.from(intent)!!)
        assertThat(parsed.map { it.tag }).containsExactly("wlop", "1girl", "ocean").inOrder()
    }
}
