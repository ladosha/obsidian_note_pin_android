package com.vladmas.notepinobsidian

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Brings notifications and alarms in line with what the vaults sent. */
object Reminders {
    fun refresh(context: Context) {
        Notifier.syncPins(context)
        Alarms.reschedule(context)
        AllDayPins.sync(context)
    }
}

/**
 * One alarm per time or all-day reminder, for its next occurrence. When it fires, [AlarmReceiver] shows the
 * notification and schedules the following occurrence. A one-off reminder that came due while the phone was
 * off is shown late, up to [CATCH_UP] after its time.
 */
object Alarms {
    private const val PREFS = "alarms"
    private const val KEY_SCHEDULED = "scheduled"
    private const val KEY_FIRED = "fired"
    const val EXTRA_KEY = "key"
    const val EXTRA_OCCURRENCE = "occurrence"
    private val CATCH_UP: Duration = Duration.ofHours(24)
    /** Shown later than this after its time, a time reminder says "Missed reminder" and doesn't count as shown on time. */
    val LATE_AFTER: Duration = Duration.ofMinutes(2)

    /** Whether showing [item]'s [occurrence] at [shownAt] is late: after [LATE_AFTER], or for an all-day reminder, after its day. */
    fun isLate(item: Item, occurrence: LocalDateTime, shownAt: LocalDateTime): Boolean =
        if (item.type == ReminderType.ALLDAY) !shownAt.isBefore(Schedule.endOfDay(occurrence))
        else Duration.between(occurrence, shownAt) > LATE_AFTER
    private val FORGET_FIRED: Duration = Duration.ofDays(30)

    fun reschedule(context: Context) {
        val now = LocalDateTime.now()
        val scheduled = mutableSetOf<String>()
        for (item in Store.allItems(context)) {
            val occurrence = nextToShow(context, item, now) ?: continue
            schedule(context, item, occurrence)
            scheduled += item.key
        }
        val prefs = prefs(context)
        val previous = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        (previous - scheduled).forEach { cancel(context, it) }
        prefs.edit().putStringSet(KEY_SCHEDULED, scheduled).apply()
    }

    fun wasShown(context: Context, key: String, occurrence: String) = fired(context).has("$key@$occurrence")

    fun markShown(context: Context, key: String, occurrence: String) {
        val fired = fired(context).put("$key@$occurrence", System.currentTimeMillis())
        val cutoff = System.currentTimeMillis() - FORGET_FIRED.toMillis()
        fired.keys().asSequence().toList().filter { fired.optLong(it) < cutoff }.forEach { fired.remove(it) }
        prefs(context).edit().putString(KEY_FIRED, fired.toString()).apply()
    }

    /**
     * The vault's one-off reminders that were shown on time, as [{"id": …, "at": …}] with `at` as the plugin sent
     * it. The plugin records them in the notes, so Obsidian can tell them apart from missed ones.
     */
    fun shownOnTime(context: Context, vault: String): JSONArray {
        val fired = fired(context)
        val shown = JSONArray()
        for (item in Store.allItems(context)) {
            if (item.vault != vault || item.repeat != Repeat.NONE || item.type == ReminderType.STATUSBAR) continue
            val at = item.at ?: continue
            val occurrence = Schedule.parse(at, item.allDayTime) ?: continue
            val shownAt = fired.optLong("${item.key}@${Schedule.format(occurrence)}", 0L).takeIf { it > 0 } ?: continue
            val shownTime = Instant.ofEpochMilli(shownAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
            if (!isLate(item, occurrence, shownTime)) shown.put(JSONObject().put("id", item.id).put("at", at))
        }
        return shown
    }

    /** The occurrence to wait for, or null if there's nothing left to show. */
    private fun nextToShow(context: Context, item: Item, now: LocalDateTime): LocalDateTime? {
        var occurrence = item.nextFire(now) ?: return null
        if (wasShown(context, item.key, Schedule.format(occurrence))) {
            if (item.repeat == Repeat.NONE) return null
            occurrence = item.nextFire(occurrence.plusMinutes(1)) ?: return null
        }
        // nextFire only returns a past time for one-off reminders.
        if (occurrence.isBefore(now) && Duration.between(occurrence, now) > CATCH_UP) return null
        // Another device already showed it on time, so it isn't shown here too, except that an all-day reminder is
        // still pinned for the rest of its day.
        if (item.repeat == Repeat.NONE && item.shown &&
            (item.type != ReminderType.ALLDAY || !now.isBefore(Schedule.endOfDay(occurrence)))
        ) {
            return null
        }
        return occurrence
    }

    private fun schedule(context: Context, item: Item, occurrence: LocalDateTime) {
        val at = occurrence.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            .coerceAtLeast(System.currentTimeMillis() + 1_000)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val operation = pendingIntent(context, item.key, Schedule.format(occurrence), create = true) ?: return
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } else {
            // Without the exact-alarm permission Android may deliver it a few minutes late.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    private fun cancel(context: Context, key: String) {
        val operation = pendingIntent(context, key, null, create = false) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(operation)
        operation.cancel()
    }

    /** One pending intent per reminder: the data URI makes them distinct, the extras say which occurrence. */
    private fun pendingIntent(context: Context, key: String, occurrence: String?, create: Boolean): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setData(Uri.parse("vladmas-note-pin-alarm:${Uri.encode(key)}"))
            .putExtra(EXTRA_KEY, key)
            .putExtra(EXTRA_OCCURRENCE, occurrence)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    private fun fired(context: Context) =
        runCatching { JSONObject(prefs(context).getString(KEY_FIRED, "{}")!!) }.getOrElse { JSONObject() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(Alarms.EXTRA_KEY) ?: return
        val occurrence = intent.getStringExtra(Alarms.EXTRA_OCCURRENCE) ?: return
        val item = Store.allItems(context).find { it.key == key }
        val time = Schedule.parse(occurrence)
        if (item != null && time != null && !Alarms.wasShown(context, key, occurrence)) {
            Alarms.markShown(context, key, occurrence)
            val now = LocalDateTime.now()
            if (item.type == ReminderType.ALLDAY && now.isBefore(Schedule.endOfDay(time))) {
                AllDayPins.pin(context, item, time)
            } else {
                Notifier.showReminder(context, item, time, Alarms.isLate(item, time, now))
            }
        }
        Alarms.reschedule(context)
    }
}

/**
 * Reboots clear alarms and notifications, app updates clear notifications, and clock changes move alarms. A new
 * phone language renames the notification channels, which Android shows in the app's notification settings.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.createChannels(context)
        Reminders.refresh(context)
    }
}

/** The Remove button on the test notification, which may not be swipeable. */
class RemoveTestNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.removeTest(context)
    }
}
