package com.vladmas.notepinobsidian

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject
import java.util.Base64
import java.util.zip.CRC32

/**
 * Messages from the Obsidian plugin: vladmas-note-pin://sync?v=1&vault=…&req=…&token=…&ack=1&data=…, where data
 * is the vault's snapshot as base64url JSON. The reply goes back by opening
 * obsidian://vladmas-note-pin?op=ack&req=…&len=…&crc=…, so the plugin can check the message arrived intact. The
 * reply's `shown` lists the vault's one-off reminders that were shown on time (base64url JSON, see
 * [Alarms.shownOnTime]).
 */
object Sync {
    /** The link scheme the plugin opens; the same string as the plugin's ID and its obsidian:// action. */
    private const val SCHEME = "vladmas-note-pin"

    class Message(val vault: String, val req: String, val token: String, val data: String, val wantsReply: Boolean)

    fun parse(link: Uri): Message? {
        if (link.scheme != SCHEME || link.host != "sync") return null
        val vault = link.getQueryParameter("vault") ?: return null
        return Message(
            vault = vault,
            req = link.getQueryParameter("req").orEmpty(),
            token = link.getQueryParameter("token").orEmpty(),
            data = link.getQueryParameter("data").orEmpty(),
            wantsReply = link.getQueryParameter("ack") == "1",
        )
    }

    /** A vault may send once the user has allowed it; the token proves it's the same Obsidian. */
    fun isAllowed(context: Context, message: Message) =
        message.token.isNotEmpty() && Store.token(context, message.vault) == message.token

    /** Stores and applies the snapshot; returns the reply for the plugin. */
    fun receive(context: Context, message: Message): Map<String, String> {
        val crc = CRC32().apply { update(message.data.toByteArray(Charsets.US_ASCII)) }.value
        val reply = mutableMapOf(
            "op" to "ack",
            "req" to message.req,
            "len" to message.data.length.toString(),
            "crc" to "%08x".format(crc),
        )
        try {
            val snapshot = JSONObject(String(Base64.getUrlDecoder().decode(message.data), Charsets.UTF_8))
            val count = snapshot.optJSONArray("items")?.length() ?: 0
            if (snapshot.optString("kind") == "snapshot") {
                Store.saveSnapshot(context, message.vault, snapshot)
                Reminders.refresh(context)
                val shown = Alarms.shownOnTime(context, message.vault)
                if (shown.length() > 0) {
                    reply["shown"] = Base64.getUrlEncoder().withoutPadding().encodeToString(shown.toString().toByteArray(Charsets.UTF_8))
                }
                if (count > 0 && !Notifier.notificationsAllowed(context)) {
                    // A code, which the plugin shows as a sentence in Obsidian's language.
                    reply["warning"] = "notifications-off"
                }
            }
            reply["items"] = count.toString()
        } catch (e: Exception) {
            reply["error"] = "${e.javaClass.simpleName}: ${e.message}".take(200)
        }
        return reply
    }

    fun declined(message: Message) = mapOf("op" to "ack", "req" to message.req, "error" to "not-allowed")

    fun reply(activity: Activity, message: Message, params: Map<String, String>) {
        if (!message.wantsReply) return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Obsidian.pluginUri(message.vault, params)))
        } catch (e: ActivityNotFoundException) {
            // Obsidian was uninstalled since it sent the message; nothing to reply to.
        }
    }
}

/**
 * Receives the plugin's links. It has no UI (Theme.NoDisplay) and finishes in onCreate, so Android goes
 * straight back to Obsidian; a vault that isn't allowed yet goes to [PairActivity] first.
 */
class SyncActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent.data
        val message = link?.let(Sync::parse)
        when {
            message == null -> Unit
            Sync.isAllowed(this, message) -> Sync.reply(this, message, Sync.receive(this, message))
            else -> startActivity(Intent(this, PairActivity::class.java).setData(link))
        }
        finish()
    }
}
