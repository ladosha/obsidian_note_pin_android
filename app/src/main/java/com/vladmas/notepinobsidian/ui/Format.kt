package com.vladmas.notepinobsidian.ui

import android.content.Context
import com.vladmas.notepinobsidian.Item
import com.vladmas.notepinobsidian.Lunar
import com.vladmas.notepinobsidian.R
import com.vladmas.notepinobsidian.ReminderType
import com.vladmas.notepinobsidian.Repeat
import com.vladmas.notepinobsidian.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Dates in the app's language and the phone's format. */
object Format {
    // Made on each use: a formatter keeps the language it was made in, and the app's language can change while it runs.
    private val weekday get() = DateTimeFormatter.ofPattern("EEE")
    private val date get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val time get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    /**
     * "Mon, Oct 27, 2026, 9:00 AM · every week · until Tue, Dec 15, 2026", "All day · Mon, Oct 27, 2026",
     * "Pinned to the status bar", or for a repeat that has ended "Repeated until Tue, Dec 15, 2026".
     */
    fun schedule(context: Context, item: Item, next: LocalDateTime?): String {
        if (item.type == ReminderType.STATUSBAR) return context.getString(R.string.schedule_pinned)
        val until = item.until?.let(Schedule::parseDate)?.takeIf { item.repeat != Repeat.NONE }
        val start = item.at?.let { Schedule.parse(it, item.allDayTime) }
        if (next == null || start == null) {
            return if (until != null) context.getString(R.string.schedule_repeat_ended, day(until)) else context.getString(R.string.schedule_reminder)
        }
        val main = if (item.type == ReminderType.ALLDAY) {
            context.getString(R.string.schedule_all_day, day(next.toLocalDate()))
        } else {
            "${day(next.toLocalDate())}, ${next.format(time)}"
        }
        val repeat = repeatLabel(context, item.repeat, start)
        val end = if (repeat != null && until != null) context.getString(R.string.repeat_until, day(until)) else null
        return listOfNotNull(main, repeat, end).joinToString(" · ")
    }

    /**
     * How a reminder starting at [start] repeats, with the day it repeats on: "every week on Sunday", "every month on
     * the 15th", "every month on the third Sunday", "every year on November 15", "every year by the Chinese lunar
     * calendar (month 8, day 15)"; null for a one-off. The plugin words them the same (i18n.ts).
     */
    private fun repeatLabel(context: Context, repeat: Repeat, start: LocalDateTime): String? {
        val resources = context.resources
        // Sunday first, as in the string arrays.
        val weekday = start.dayOfWeek.value % 7
        return when (repeat) {
            Repeat.NONE -> null
            Repeat.DAILY -> context.getString(R.string.repeat_daily)
            Repeat.WEEKLY -> context.getString(R.string.repeat_weekly_on, resources.getStringArray(R.array.weekdays_on)[weekday])
            Repeat.BIWEEKLY -> context.getString(R.string.repeat_biweekly_on, resources.getStringArray(R.array.weekdays_on)[weekday])
            Repeat.MONTHLY -> resources.getStringArray(R.array.repeat_monthly_on)[start.dayOfMonth - 1]
            Repeat.MONTHLY_WEEKDAY, Repeat.MONTHLY_LAST_WEEKDAY -> {
                val nth = if (repeat == Repeat.MONTHLY_LAST_WEEKDAY) 5 else Schedule.weekInMonth(start.toLocalDate())
                // Rows first to fourth, then last; each row Sunday to Saturday.
                resources.getStringArray(R.array.repeat_monthly_weekday)[(nth - 1) * 7 + weekday]
            }
            Repeat.YEARLY -> yearlyOn(context, start)
            Repeat.YEARLY_LUNAR -> Lunar.of(start.toLocalDate())?.let { lunar ->
                val date = if (lunar.leap) R.string.lunar_leap_date else R.string.lunar_date
                context.getString(R.string.repeat_yearly_lunar, context.getString(date, lunar.month, lunar.day))
            } ?: yearlyOn(context, start)
        }
    }

    /** "every year on November 15"; 29 February says it falls on the 28th in other years. */
    private fun yearlyOn(context: Context, start: LocalDateTime): String =
        if (start.monthValue == 2 && start.dayOfMonth == 29) {
            context.getString(R.string.repeat_yearly_feb29)
        } else {
            val month = context.resources.getStringArray(R.array.months_on)[start.monthValue - 1]
            context.getString(R.string.repeat_yearly_on, start.dayOfMonth, month)
        }

    /** "Mon, Oct 27, 2026". */
    private fun day(day: LocalDate): String = "${day.format(weekday)}, ${day.format(date)}"

    fun dateTime(millis: Long): String {
        val at = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        return "${at.format(date)}, ${at.format(time)}"
    }
}
