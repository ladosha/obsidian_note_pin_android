package com.vladmas.notepinobsidian

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * All-day reminders stay in the notification bar for the rest of their day. Each one alerts once at the all-day
 * time, then stays pinned until midnight, when the app removes it; its Remove button takes it away sooner. The pins
 * showing are kept here, so they come back after a restart or an app update until their day is over.
 */
object AllDayPins {
    private const val PREFS = "allday"
    private const val KEY_SHOWING = "showing"
    const val ACTION_REMOVE = "com.vladmas.notepinobsidian.action.REMOVE_ALL_DAY"
    const val ACTION_DAY_END = "com.vladmas.notepinobsidian.action.DAY_END"

    /** Shows the item's [occurrence] pinned, with an alert, and keeps it until its day ends. */
    fun pin(context: Context, item: Item, occurrence: LocalDateTime) {
        save(context, showing(context).put(item.key, Schedule.format(occurrence)))
        Notifier.showAllDay(context, item, alert = true)
        scheduleDayEnd(context)
    }

    /** The Remove button: takes the pin away for the rest of the day. */
    fun remove(context: Context, key: String) {
        val showing = showing(context)
        showing.remove(key)
        save(context, showing)
        Notifier.cancelReminder(context, key)
        scheduleDayEnd(context)
    }

    /**
     * Removes the pins whose day is over, or whose reminder was removed or changed in Obsidian, and puts back
     * the rest if the system cleared them, without an alert.
     */
    fun sync(context: Context) {
        val now = LocalDateTime.now()
        val items = Store.allItems(context).associateBy { it.key }
        val showing = showing(context)
        val active = Notifier.activeReminderKeys(context)
        for (key in showing.keys().asSequence().toList()) {
            val occurrence = Schedule.parse(showing.getString(key))
            val item = items[key]
            if (occurrence == null || item == null || item.type != ReminderType.ALLDAY ||
                item.nextFire(occurrence) != occurrence || !now.isBefore(Schedule.endOfDay(occurrence))
            ) {
                showing.remove(key)
                Notifier.cancelReminder(context, key)
            } else if (key !in active) {
                Notifier.showAllDay(context, item, alert = false)
            }
        }
        save(context, showing)
        scheduleDayEnd(context)
    }

    /** One alarm at the next midnight while any pin is showing, to remove the pins whose day ends then. */
    private fun scheduleDayEnd(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val operation = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AllDayReceiver::class.java).setAction(ACTION_DAY_END),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val showing = showing(context)
        val ends = showing.keys().asSequence()
            .mapNotNull { Schedule.parse(showing.getString(it)) }
            .map(Schedule::endOfDay)
            .minOrNull()
        if (ends == null) {
            alarms.cancel(operation)
            return
        }
        val at = ends.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    /** Item key → the occurrence ("YYYY-MM-DDTHH:mm") pinned for it. */
    private fun showing(context: Context) =
        runCatching { JSONObject(prefs(context).getString(KEY_SHOWING, "{}")!!) }.getOrElse { JSONObject() }

    private fun save(context: Context, showing: JSONObject) {
        prefs(context).edit().putString(KEY_SHOWING, showing.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The Remove button on an all-day reminder, and the midnight alarm that removes the pins whose day is over. */
class AllDayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AllDayPins.ACTION_REMOVE -> intent.getStringExtra(Alarms.EXTRA_KEY)?.let { AllDayPins.remove(context, it) }
            AllDayPins.ACTION_DAY_END -> AllDayPins.sync(context)
        }
    }
}
