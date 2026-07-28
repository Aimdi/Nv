package dev.naicompanion.app.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.getSystemService

/** The mobile NovelAI experience is the website/PWA, so "copy then switch tabs" is the workflow. */
const val NOVELAI_IMAGE_URL = "https://novelai.net/image"

object ClipboardBridge {

    /**
     * Copies [text] and reports whether the caller should show its own confirmation. Android 13
     * and newer display a system copy confirmation, so an in-app snackbar would be a duplicate.
     */
    fun copy(context: Context, text: String, label: String = "NovelAI prompt"): Boolean {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return true
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }

    fun paste(context: Context): String? {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }
}

object ShareBridge {

    fun shareText(context: Context, text: String, title: String = "Share prompt") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    /** Opens the NovelAI image generator; the prompt is already on the clipboard, ready to paste. */
    fun openNovelAi(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(NOVELAI_IMAGE_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
            }
    }
}
