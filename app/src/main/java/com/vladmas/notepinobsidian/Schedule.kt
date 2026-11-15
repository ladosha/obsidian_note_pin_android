package com.vladmas.notepinobsidian

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * How a reminder repeats, as the plugin stores it in reminder-repeat ("monthly-weekday" is MONTHLY_WEEKDAY). The
 * weekday and lunar rules take their day from the reminder's own date: MONTHLY_WEEKDAY on 15 November 2026, the
 * third Sunday, repeats on every third Sunday.
 */
enum class Repeat {
    NONE, DAILY, WEEKLY, BIWEEKLY, MONTHLY, MONTHLY_WEEKDAY, MONTHLY_LAST_WEEKDAY, YEARLY, YEARLY_LUNAR;

    companion object {
        fun of(text: String?) = entries.firstOrNull { it.name.equals(text?.replace('-', '_'), ignoreCase = true) } ?: NONE
    }
}

/**
 * Reminder times are wall-clock times ("2026-10-27T09:00"), not instants: a daily 09:00 reminder stays at
 * 09:00 across daylight-saving changes and time zones. The Obsidian plugin has the same rules in TypeScript
 * (recurrence.ts); both are checked against the same cases, app/src/test/resources/recurrence-cases.json here.
 */
object Schedule {
    private val wallTime = Regex("""^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?""")
    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    /** Parses "YYYY-MM-DD" or "YYYY-MM-DDTHH:mm"; a date alone takes [defaultTime] ("HH:mm"). */
    fun parse(text: String, defaultTime: String = "00:00"): LocalDateTime? {
        val match = wallTime.find(text.trim()) ?: return null
        val (year, month, day) = match.destructured
        val (hour, minute) = if (match.groupValues[4].isNotEmpty()) {
            match.groupValues[4].toInt() to match.groupValues[5].toInt()
        } else {
            clock(defaultTime)
        }
        return runCatching { LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour, minute) }.getOrNull()
    }

    fun format(time: LocalDateTime): String = time.format(format)

    /** Parses "YYYY-MM-DD", such as a repeat's last day. */
    fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim().take(10)) }.getOrNull()

    /** Which of its month's weekdays [date] is: 3 for the third Sunday; 5 for a fifth, which is always the last. */
    fun weekInMonth(date: LocalDate): Int = (date.dayOfMonth + 6) / 7

    /** Midnight at the end of [time]'s day, when an all-day reminder stops being current. */
    fun endOfDay(time: LocalDateTime): LocalDateTime = time.toLocalDate().plusDays(1).atStartOfDay()

    /** "HH:mm" as hour and minute; anything unreadable is 09:00. */
    fun clock(text: String): Pair<Int, Int> {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").find(text.trim()) ?: return 9 to 0
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        return if (hour < 24 && minute < 60) hour to minute else 9 to 0
    }

    /**
     * The first occurrence at or after [now]. A one-off reminder returns its own time, even if it's past. A repeating
     * one returns null once it has ended: [until] is the last day it may fire on.
     */
    fun next(start: LocalDateTime, repeat: Repeat, now: LocalDateTime, until: LocalDate? = null): LocalDateTime? {
        if (repeat == Repeat.NONE) return start
        val next = if (!start.isBefore(now)) start else repeatAfter(start, repeat, now) ?: return null
        return next.takeIf { until == null || !it.toLocalDate().isAfter(until) }
    }

    /** The first occurrence at or after [now], for a repeating reminder that started before it. */
    private fun repeatAfter(start: LocalDateTime, repeat: Repeat, now: LocalDateTime): LocalDateTime? = when (repeat) {
        Repeat.NONE -> start
        Repeat.DAILY, Repeat.WEEKLY, Repeat.BIWEEKLY -> {
            val step = when (repeat) {
                Repeat.DAILY -> 1L
                Repeat.WEEKLY -> 7L
                else -> 14L
            }
            var steps = ChronoUnit.DAYS.between(start, now) / step
            var candidate = start.plusDays(steps * step)
            while (candidate.isBefore(now)) candidate = start.plusDays(++steps * step)
            candidate
        }
        Repeat.MONTHLY, Repeat.YEARLY -> {
            // plusMonths keeps the original day where it can and falls back to the month's last day,
            // because it always counts from the start (Jan 31 → Feb 28 → Mar 31).
            val monthsPerStep = if (repeat == Repeat.MONTHLY) 1L else 12L
            var steps = maxOf(0L, ChronoUnit.MONTHS.between(start, now) / monthsPerStep - 1)
            var candidate = start.plusMonths(steps * monthsPerStep)
            while (candidate.isBefore(now)) candidate = start.plusMonths(++steps * monthsPerStep)
            candidate
        }
        Repeat.MONTHLY_WEEKDAY, Repeat.MONTHLY_LAST_WEEKDAY -> {
            val nth = weekInMonth(start.toLocalDate())
            // A fifth weekday becomes the last one, since most months don't have a fifth.
            val last = repeat == Repeat.MONTHLY_LAST_WEEKDAY || nth == 5
            // Start a month early: this month's may still be ahead.
            generateSequence(YearMonth.from(now).minusMonths(1)) { it.plusMonths(1) }
                .map { month ->
                    val date = if (last) {
                        month.atEndOfMonth().with(TemporalAdjusters.previousOrSame(start.dayOfWeek))
                    } else {
                        month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(nth, start.dayOfWeek))
                    }
                    date.atTime(start.toLocalTime())
                }
                .first { !it.isBefore(now) }
        }
        Repeat.YEARLY_LUNAR -> {
            val lunar = Lunar.of(start.toLocalDate())
            // Lunar years start in late January or February, so the lunar year that started the Gregorian year
            // before may still be running.
            lunar?.let {
                (now.year - 1..now.year + 1).firstNotNullOfOrNull { year ->
                    Lunar.gregorian(year, lunar)?.atTime(start.toLocalTime())?.takeIf { !it.isBefore(now) }
                }
            }
        }
    }
}
