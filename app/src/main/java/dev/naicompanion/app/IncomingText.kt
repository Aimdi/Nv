package dev.naicompanion.app

import android.content.Intent

/**
 * Text handed to the app from elsewhere: the share sheet, or the text-selection "process text"
 * action. Either way it lands in the builder as a parsed set of chips.
 */
object IncomingText {

    fun from(intent: Intent?): String? {
        if (intent == null) return null
        val raw = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

            else -> null
        }
        return raw?.takeIf { it.isNotBlank() }
    }
}
