package com.vladmas.notepinobsidian

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** What the vaults sent, and which vaults are allowed to send. Everything stays in the app's private storage. */
object Store {
    private const val SNAPSHOTS_FILE = "snapshots.json"
    private const val PAIRING_PREFS = "pairing"

    fun snapshots(context: Context): List<Snapshot> {
        val all = readAll(context)
        return all.keys().asSequence().mapNotNull { runCatching { Snapshot.from(all.getJSONObject(it)) }.getOrNull() }.toList()
    }

    fun allItems(context: Context): List<Item> = snapshots(context).flatMap { it.items }

    /** Stores [message], the plugin's snapshot of [vault], replacing what the vault sent before. */
    fun saveSnapshot(context: Context, vault: String, message: JSONObject) {
        message.put("receivedAt", System.currentTimeMillis())
        writeAll(context, readAll(context).put(vault, message))
    }

    fun token(context: Context, vault: String): String? = pairing(context).getString(vault, null)

    fun allow(context: Context, vault: String, token: String) {
        pairing(context).edit().putString(vault, token).apply()
    }

    /** Forgets the vault: its token and everything it sent. */
    fun disconnect(context: Context, vault: String) {
        pairing(context).edit().remove(vault).apply()
        val all = readAll(context)
        all.remove(vault)
        writeAll(context, all)
    }

    fun connectedVaults(context: Context): List<String> = pairing(context).all.keys.sorted()

    private fun pairing(context: Context) = context.getSharedPreferences(PAIRING_PREFS, Context.MODE_PRIVATE)

    private fun file(context: Context) = AtomicFile(File(context.filesDir, SNAPSHOTS_FILE))

    private fun readAll(context: Context): JSONObject =
        runCatching { JSONObject(String(file(context).readFully(), Charsets.UTF_8)) }.getOrElse { JSONObject() }

    private fun writeAll(context: Context, all: JSONObject) {
        val file = file(context)
        val stream = file.startWrite()
        try {
            stream.write(all.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}

/** The app's own settings, kept on this device. */
object AppSettings {
    private const val PREFS = "settings"
    private const val SETUP_DONE = "setup_done"
    // The key predates reminders being included; kept so the setting survives updates.
    private const val SEPARATE_NOTIFICATIONS = "separate_pins"

    fun setupDone(context: Context) = prefs(context).getBoolean(SETUP_DONE, false)

    fun setSetupDone(context: Context) {
        prefs(context).edit().putBoolean(SETUP_DONE, true).apply()
    }

    /** Whether each note's notification gets a group of its own, so Android doesn't bundle them into one card. */
    fun separateNotifications(context: Context) = prefs(context).getBoolean(SEPARATE_NOTIFICATIONS, false)

    fun setSeparateNotifications(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(SEPARATE_NOTIFICATIONS, value).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
