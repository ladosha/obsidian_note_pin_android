package com.vladmas.notepinobsidian

import android.content.Context
import android.content.Intent
import android.net.Uri

object About {
    const val DEVELOPER_NAME = "vladmas"
    const val DEVELOPER_SITE = "https://vladmas.com"
    /** The Ko-fi page, for support. */
    const val KOFI_URL = "https://ko-fi.com/vladmas"
}

/** Links into Obsidian, handled by the plugin's obsidian://vladmas-note-pin action. */
object Obsidian {
    private const val ACTION = "vladmas-note-pin"

    fun pluginUri(vault: String?, params: Map<String, String>): Uri =
        Uri.Builder().scheme("obsidian").authority(ACTION).apply {
            vault?.let { appendQueryParameter("vault", it) }
            params.forEach { (key, value) -> appendQueryParameter(key, value) }
        }.build()

    /** Opens the note through the plugin, which finds it by ID, so renames don't break the link. */
    fun openNoteIntent(item: Item): Intent =
        Intent(Intent.ACTION_VIEW, pluginUri(item.vault, mapOf("op" to "open", "id" to item.id, "path" to item.path)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isInstalled(context: Context): Boolean =
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("obsidian://open")), 0).isNotEmpty()
}
