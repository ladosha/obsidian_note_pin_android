package com.vladmas.notepinobsidian

import android.graphics.Color
import org.json.JSONObject
import java.time.LocalDateTime

enum class ReminderType {
    TIME, ALLDAY, STATUSBAR;

    companion object {
        fun of(text: String?) = when (text) {
            "allday" -> ALLDAY
            "statusbar" -> STATUSBAR
            else -> TIME
        }
    }
}

/** One note from a vault's snapshot: a time or all-day reminder, or a note pinned to the status bar. */
data class Item(
    val vault: String,
    /** The note's vmnp-id, or its path when it has none. */
    val id: String,
    val path: String,
    val title: String,
    val preview: String,
    val subtitle: String?,
    val color: String?,
    val type: ReminderType,
    /** "YYYY-MM-DDTHH:mm" for a time reminder, "YYYY-MM-DD" for an all-day one, null for a pin. */
    val at: String?,
    val repeat: Repeat,
    /** The last day a repeating reminder fires on, "YYYY-MM-DD"; null while it repeats forever. */
    val until: String? = null,
    /** When all-day reminders fire, from the vault's plugin settings. */
    val allDayTime: String,
    /** A device already showed this one-off reminder on time (the note's reminder-shown), so it isn't shown late here. */
    val shown: Boolean = false,
) {
    /** Identifies the item across vaults. */
    val key get() = "$vault|$id"

    val body get() = subtitle ?: preview

    /** The first occurrence at or after [now]; null for pins and for repeats that have ended. One-off reminders may be in the past. */
    fun nextFire(now: LocalDateTime): LocalDateTime? {
        if (type == ReminderType.STATUSBAR || at == null) return null
        val start = Schedule.parse(at, allDayTime) ?: return null
        return Schedule.next(start, repeat, now, until?.let(Schedule::parseDate))
    }

    companion object {
        fun from(vault: String, allDayTime: String, json: JSONObject) = Item(
            vault = vault,
            id = json.getString("id"),
            path = json.optString("path"),
            title = json.optString("title"),
            preview = json.text("preview").orEmpty(),
            subtitle = json.text("subtitle"),
            color = json.text("color"),
            type = ReminderType.of(json.text("type")),
            at = json.text("at"),
            repeat = Repeat.of(json.text("repeat")),
            until = json.text("until"),
            allDayTime = allDayTime,
            shown = json.optBoolean("shown", false),
        )
    }
}

/** What a vault last sent, as stored: the plugin's message plus when it arrived. */
data class Snapshot(val vault: String, val receivedAt: Long, val items: List<Item>) {
    companion object {
        fun from(json: JSONObject): Snapshot {
            val vault = json.getString("vault")
            val allDayTime = json.optJSONObject("settings")?.text("allDayTime") ?: "09:00"
            val array = json.optJSONArray("items")
            val items = (0 until (array?.length() ?: 0)).map { Item.from(vault, allDayTime, array!!.getJSONObject(it)) }
            return Snapshot(vault, json.optLong("receivedAt"), items)
        }
    }
}

/** optString turns JSON null into the text "null"; this gives null for it and for "". */
fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name).ifEmpty { null }

/** The plugin's palette (constants.ts), so notes look the same in Obsidian and in notifications. */
object NoteColors {
    private val named = mapOf(
        "red" to 0xFFE5484D,
        "orange" to 0xFFF2832B,
        "yellow" to 0xFFF2C94C,
        "green" to 0xFF4CAF6E,
        "blue" to 0xFF3D8EF0,
        "purple" to 0xFF9B6CF0,
        "gray" to 0xFF8E8E93,
        "grey" to 0xFF8E8E93,
        "black" to 0xFF3A3A3C,
        "white" to 0xFFE8E8E8,
    )

    /** A palette name, or any #rrggbb value, as an ARGB color. */
    fun of(name: String?): Int? {
        val key = name?.trim()?.lowercase() ?: return null
        return named[key]?.toInt() ?: runCatching { Color.parseColor(key) }.getOrNull()
    }
}
