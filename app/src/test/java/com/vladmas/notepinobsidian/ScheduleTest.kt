package com.vladmas.notepinobsidian

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ScheduleTest {
    /** Test data the plugin's tests use too; its repository has identical copies in test/data. */
    private fun data(name: String) = JSONArray(javaClass.getResource("/$name")!!.readText())

    /** The same cases the plugin's tests use, so both sides work out the same next dates. */
    @Test
    fun sharedCases() {
        val cases = data("recurrence-cases.json")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val next = Schedule.next(
                Schedule.parse(case.getString("start"))!!,
                Repeat.of(case.getString("repeat")),
                Schedule.parse(case.getString("now"))!!,
                case.text("until")?.let(Schedule::parseDate),
            )
            assertEquals(case.getString("name"), case.text("next"), next?.let(Schedule::format))
        }
    }

    /** Every month start the Hong Kong Observatory publishes for 1901–2100, as the plugin's tests check too. */
    @Test
    fun lunarTableMatchesTheObservatory() {
        val starts = data("chinese-months.json")
        var newYear = 0
        for (i in 0 until starts.length()) {
            val start = starts.getJSONArray(i)
            val date = LocalDate.parse(start.getString(0))
            val lunar = LunarDay(start.getInt(1), start.getBoolean(2), 1)
            assertEquals(start.getString(0), lunar, Lunar.of(date))
            if (lunar.month == 1 && !lunar.leap) newYear = date.year
            assertEquals("${start.getString(0)} back", date, Lunar.gregorian(newYear, lunar))
        }
        assertNull(Lunar.of(LocalDate.of(1901, 2, 18)))
        assertNull(Lunar.of(LocalDate.of(2100, 2, 9)))
    }

    @Test
    fun repeatNamesFromThePlugin() {
        assertEquals(Repeat.MONTHLY_LAST_WEEKDAY, Repeat.of("monthly-last-weekday"))
        assertEquals(Repeat.YEARLY_LUNAR, Repeat.of("yearly-lunar"))
        assertEquals(Repeat.NONE, Repeat.of("fortnightly"))
    }

    @Test
    fun dateAloneTakesTheDefaultTime() {
        assertEquals("2026-10-27T08:30", Schedule.format(Schedule.parse("2026-10-27", "08:30")!!))
    }

    @Test
    fun anAllDayReminderLastsUntilMidnight() {
        assertEquals("2026-11-15T00:00", Schedule.format(Schedule.endOfDay(Schedule.parse("2026-11-14T09:00")!!)))
        assertEquals("2027-01-01T00:00", Schedule.format(Schedule.endOfDay(Schedule.parse("2026-12-31T23:59")!!)))
    }

    @Test
    fun impossibleDatesAreRejected() {
        assertNull(Schedule.parse("2026-02-30T09:00"))
        assertNull(Schedule.parse("tomorrow"))
    }
}
