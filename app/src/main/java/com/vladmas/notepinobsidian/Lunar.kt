package com.vladmas.notepinobsidian

import java.time.LocalDate

/** A day in the Chinese calendar: its month (1–12), whether that is the year's leap month, and its day (1–30). */
data class LunarDay(val month: Int, val leap: Boolean, val day: Int)

/**
 * The Chinese lunisolar calendar, for reminders that repeat every lunar year (a lunar birthday, the Mid-Autumn
 * Festival). The month lengths and leap months come from the Hong Kong Observatory's Gregorian-Lunar Calendar
 * Conversion Tables for 1901–2100. ICU's astronomical calculation (android.icu.util.ChineseCalendar) is a day off for
 * 12 months of that period, among them February 2027 and February 2030, so it isn't used. The Obsidian plugin has
 * the same table (lunar.ts); both are checked against every month start the Observatory publishes,
 * app/src/test/resources/chinese-months.json here. The plugin's scripts/build_lunar_table.py rebuilds both.
 */
object Lunar {
    /** The first lunar year in the table; it starts on 19 February 1901. */
    const val FIRST_YEAR = 1901
    private val firstDay = LocalDate.of(1901, 2, 19).toEpochDay()

    /**
     * One entry per lunar year from 1901 to 2099. Bits 0–3: the leap month, which follows the regular month of the
     * same number (0: none). Bit 4 + i: the year's i-th month, counting the leap month, has 30 days rather than 29.
     */
    private val years = intArrayOf(
        0x07520, 0x0ea50, 0x164a5, 0x064b0, 0x0a9b0, 0x15564, 0x056a0, 0x0b590, 0x17522, 0x07520,  // 1901
        0x1b256, 0x0b250, 0x0a4b0, 0x14ab5, 0x02ad0, 0x056b0, 0x0b692, 0x0da90, 0x1d927, 0x0e920,  // 1911
        0x0d250, 0x1a4d5, 0x0a560, 0x02b60, 0x15b54, 0x06d40, 0x0ea90, 0x1e922, 0x0e920, 0x0d266,  // 1921
        0x052b0, 0x0a570, 0x12b65, 0x0b5a0, 0x06d40, 0x0ec93, 0x07490, 0x16937, 0x0a930, 0x052b0,  // 1931
        0x0a5b6, 0x0aad0, 0x056a0, 0x1b554, 0x0ba40, 0x0b490, 0x1a932, 0x0a950, 0x152d7, 0x05360,  // 1941
        0x0aad0, 0x15aa5, 0x05b20, 0x0da50, 0x1d4a3, 0x0d4a0, 0x0a958, 0x0a970, 0x05560, 0x0ab56,  // 1951
        0x0ad50, 0x06d20, 0x0ea54, 0x0ea50, 0x064a0, 0x0c973, 0x0a9b0, 0x155a7, 0x056a0, 0x0b690,  // 1961
        0x17525, 0x0b520, 0x0b250, 0x164b4, 0x0a4b0, 0x14ab8, 0x02ad0, 0x056d0, 0x0b696, 0x0da90,  // 1971
        0x0d920, 0x1d254, 0x0d250, 0x1a4da, 0x0a560, 0x02b60, 0x05b56, 0x06d50, 0x0ea90, 0x1e925,  // 1981
        0x0e920, 0x0d260, 0x0a563, 0x0a570, 0x14d68, 0x035a0, 0x06d50, 0x16c95, 0x07490, 0x06930,  // 1991
        0x152b4, 0x052b0, 0x0a5b0, 0x155a2, 0x056a0, 0x1b557, 0x0ba40, 0x0b490, 0x1a935, 0x0a950,  // 2001
        0x052d0, 0x0aad4, 0x0ab50, 0x15aa9, 0x05d20, 0x0da50, 0x1d4a6, 0x0d4a0, 0x0c950, 0x152e4,  // 2011
        0x05560, 0x0ab50, 0x15b22, 0x06d20, 0x0ea56, 0x07250, 0x064b0, 0x0c975, 0x0cab0, 0x055a0,  // 2021
        0x0ad63, 0x0b690, 0x1752b, 0x0b520, 0x0b250, 0x1a4b6, 0x0a4b0, 0x04ab0, 0x055b5, 0x05ad0,  // 2031
        0x0b6a0, 0x1b522, 0x0d920, 0x1d257, 0x0d250, 0x0a550, 0x14ad5, 0x04b60, 0x05b50, 0x0daa3,  // 2041
        0x0ec90, 0x1e928, 0x0e920, 0x0d260, 0x0a566, 0x0a570, 0x05560, 0x06d54, 0x07550, 0x07490,  // 2051
        0x0e933, 0x06930, 0x152b7, 0x052b0, 0x0a5b0, 0x155a5, 0x056a0, 0x0b650, 0x174a4, 0x0b4a0,  // 2061
        0x1a958, 0x0a950, 0x052d0, 0x0aad6, 0x0ab50, 0x05aa0, 0x0ba54, 0x0da50, 0x0d4a0, 0x1c953,  // 2071
        0x0c960, 0x194e7, 0x05560, 0x0ab50, 0x15b25, 0x06d20, 0x0ea50, 0x0e4a4, 0x068b0, 0x0c978,  // 2081
        0x04ab0, 0x055b0, 0x0ad66, 0x0b6a0, 0x07520, 0x17254, 0x0b450, 0x0a8b0, 0x149b2,  // 2091
    )

    private class Month(val month: Int, val leap: Boolean, val first: Long, val length: Int)

    private val months = ArrayList<Month>()

    /** For each lunar year, the index of its first month in [months]; one extra entry marks the end. */
    private val yearStarts = IntArray(years.size + 1)

    init {
        var first = firstDay
        years.forEachIndexed { index, bits ->
            yearStarts[index] = months.size
            val leapMonth = bits and 0xF
            var month = 0
            for (i in 0 until if (leapMonth != 0) 13 else 12) {
                val leap = leapMonth != 0 && i == leapMonth
                if (!leap) month++
                val length = if (bits and (1 shl (4 + i)) != 0) 30 else 29
                months.add(Month(month, leap, first, length))
                first += length
            }
        }
        yearStarts[years.size] = months.size
    }

    /** The Chinese calendar day of [date], or null outside 19 February 1901 to 8 February 2100. */
    fun of(date: LocalDate): LunarDay? {
        val day = date.toEpochDay()
        val end = months.last()
        if (day < firstDay || day >= end.first + end.length) return null
        var low = 0
        var high = months.size - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (months[middle].first <= day) low = middle else high = middle - 1
        }
        val month = months[low]
        return LunarDay(month.month, month.leap, (day - month.first + 1).toInt())
    }

    /**
     * The Gregorian date of [lunar] in the lunar year that starts in Gregorian [year], or null outside the table.
     * Without that leap month this year, it's the regular month of the same number; day 30 in a 29-day month becomes
     * day 29.
     */
    fun gregorian(year: Int, lunar: LunarDay): LocalDate? {
        val index = year - FIRST_YEAR
        if (index !in years.indices) return null
        val yearMonths = months.subList(yearStarts[index], yearStarts[index + 1])
        val month = yearMonths.firstOrNull { it.month == lunar.month && it.leap == lunar.leap }
            ?: yearMonths.firstOrNull { it.month == lunar.month && !it.leap }
            ?: return null
        return LocalDate.ofEpochDay(month.first + minOf(lunar.day, month.length) - 1)
    }
}
