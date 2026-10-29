package com.yaad.reminder.domain.calendar

import java.util.Calendar
import kotlin.math.abs

/**
 * Represents a date-time in the Persian (Jalali / هجری شمسی) calendar.
 */
data class JalaliDateTime(
    val year: Int,
    val month: Int, // 1..12 (فروردین..اسفند)
    val day: Int,   // 1..31
    val hour: Int = 0,
    val minute: Int = 0,
    val dayOfWeek: Int = 0 // Calendar.SUNDAY..Calendar.SATURDAY
) {
    val monthName: String
        get() = JalaliCalendar.monthName(month)

    val weekdayName: String
        get() = JalaliCalendar.weekdayName(dayOfWeek)

    fun formatNumericDateTime(usePersianDigits: Boolean = true): String {
        val raw = String.format(
            "%04d/%02d/%02d %02d:%02d",
            year, month, day, hour, minute
        )
        return if (usePersianDigits) JalaliCalendar.toPersianDigits(raw) else raw
    }

    fun formatDateOnly(usePersianDigits: Boolean = true): String {
        val raw = "$day $monthName $year"
        return if (usePersianDigits) JalaliCalendar.toPersianDigits(raw) else raw
    }

    fun formatFullWithWeekday(usePersianDigits: Boolean = true): String {
        val timePart = String.format("%02d:%02d", hour, minute)
        val raw = "$weekdayName $day $monthName $year · ساعت $timePart"
        return if (usePersianDigits) JalaliCalendar.toPersianDigits(raw) else raw
    }

    fun formatShortDateTime(usePersianDigits: Boolean = true): String {
        val timePart = String.format("%02d:%02d", hour, minute)
        val raw = "$day $monthName $year · $timePart"
        return if (usePersianDigits) JalaliCalendar.toPersianDigits(raw) else raw
    }
}

/**
 * Pure Kotlin Jalali (Persian / Solar Hijri) calendar converter and formatter.
 * Zero external library dependencies; accurate for all modern centuries.
 */
object JalaliCalendar {

    val MONTH_NAMES = listOf(
        "فروردین",
        "اردیبهشت",
        "خرداد",
        "تیر",
        "مرداد",
        "شهریور",
        "مهر",
        "آبان",
        "آذر",
        "دی",
        "بهمن",
        "اسفند"
    )

    private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun monthName(month: Int): String =
        MONTH_NAMES.getOrElse((month - 1).coerceIn(0, 11)) { "فروردین" }

    fun monthNumberFromName(name: String): Int? {
        val clean = name.trim().replace('\u200C', ' ')
        val index = MONTH_NAMES.indexOfFirst { it == clean }
        return if (index >= 0) index + 1 else null
    }

    fun weekdayName(calendarDayOfWeek: Int): String = when (calendarDayOfWeek) {
        Calendar.SATURDAY -> "شنبه"
        Calendar.SUNDAY -> "یکشنبه"
        Calendar.MONDAY -> "دوشنبه"
        Calendar.TUESDAY -> "سه‌شنبه"
        Calendar.WEDNESDAY -> "چهارشنبه"
        Calendar.THURSDAY -> "پنجشنبه"
        Calendar.FRIDAY -> "جمعه"
        else -> ""
    }

    fun maxDaysInMonth(jalaliYear: Int, jalaliMonth: Int): Int {
        return when {
            jalaliMonth in 1..6 -> 31
            jalaliMonth in 7..11 -> 30
            isLeapJalaliYear(jalaliYear) -> 30
            else -> 29
        }
    }

    fun isLeapJalaliYear(jalaliYear: Int): Boolean {
        val r = (jalaliYear + 2346) % 2820
        val mod = ((r % 128) * 31) % 128
        return mod < 31
    }

    /**
     * Converts Epoch Milliseconds (Long) into a [JalaliDateTime].
     */
    fun fromEpochMillis(epochMillis: Long): JalaliDateTime {
        val cal = Calendar.getInstance().apply {
            timeInMillis = epochMillis
        }
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val dow = cal.get(Calendar.DAY_OF_WEEK)

        val (jy, jm, jd) = gregorianToJalali(gy, gm, gd)
        return JalaliDateTime(
            year = jy,
            month = jm,
            day = jd,
            hour = hour,
            minute = minute,
            dayOfWeek = dow
        )
    }

    /**
     * Converts a Jalali date and clock time into Epoch Milliseconds (Long) for [android.app.AlarmManager].
     */
    fun toEpochMillis(
        jalaliYear: Int,
        jalaliMonth: Int,
        jalaliDay: Int,
        hour: Int = 9,
        minute: Int = 0
    ): Long {
        val safeMonth = jalaliMonth.coerceIn(1, 12)
        val safeDay = jalaliDay.coerceIn(1, maxDaysInMonth(jalaliYear, safeMonth))
        val (gy, gm, gd) = jalaliToGregorian(jalaliYear, safeMonth, safeDay)

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, gy)
            set(Calendar.MONTH, gm - 1)
            set(Calendar.DAY_OF_MONTH, gd)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Standard Gregorian (gy, gm 1..12, gd 1..31) to Jalali (jy, jm 1..12, jd 1..31) conversion.
     */
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
            ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return Triple(jy, jm, jd)
    }

    /**
     * Standard Jalali (jy, jm 1..12, jd 1..31) to Gregorian (gy, gm 1..12, gd 1..31) conversion.
     */
    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val jyAdj = jy + 1595
        var days = -355668 + (365 * jyAdj) + ((jyAdj / 33) * 8) + (((jyAdj % 33) + 3) / 4) + jd +
            if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            gy += 100 * (--days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val isLeapG = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)
        val salA = intArrayOf(0, 31, if (isLeapG) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 1
        while (gm <= 12 && gd > salA[gm]) {
            gd -= salA[gm]
            gm++
        }
        return Triple(gy, gm, gd)
    }

    /**
     * Formats an epoch timestamp into a concise Shamsi date & time relative to [nowMillis].
     */
    fun formatShamsiTimelineLabel(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val target = fromEpochMillis(epochMillis)
        val now = fromEpochMillis(nowMillis)
        val timePart = toPersianDigits(String.format("%02d:%02d", target.hour, target.minute))
        val shamsiDatePart = toPersianDigits("${target.day} ${target.monthName} ${target.year}")

        val isSameDay = target.year == now.year && target.month == now.month && target.day == now.day
        if (isSameDay) {
            return "امروز ($shamsiDatePart) · ساعت $timePart"
        }

        val tomorrow = fromEpochMillis(nowMillis + 24 * 60 * 60 * 1000L)
        val isTomorrow = target.year == tomorrow.year &&
            target.month == tomorrow.month &&
            target.day == tomorrow.day
        if (isTomorrow) {
            return "فردا ($shamsiDatePart) · ساعت $timePart"
        }

        return "${target.weekdayName} $shamsiDatePart · ساعت $timePart"
    }

    /**
     * Returns a human-readable Persian countdown string relative to [nowMillis].
     */
    fun formatRemainingDuration(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diffMillis = targetMillis - nowMillis
        if (diffMillis <= 0L) {
            val pastMinutes = (abs(diffMillis) / 60_000L).toInt()
            return when {
                pastMinutes < 1 -> "هم‌اکنون"
                pastMinutes < 60 -> toPersianDigits("$pastMinutes دقیقه پیش")
                pastMinutes < 24 * 60 -> toPersianDigits("${pastMinutes / 60} ساعت پیش")
                else -> toPersianDigits("${pastMinutes / (24 * 60)} روز پیش")
            }
        }

        val totalMinutes = ((diffMillis + 59_999L) / 60_000L).toInt()
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60

        val raw = when {
            days > 0 && hours > 0 -> "$days روز و $hours ساعت دیگر"
            days > 0 -> "$days روز دیگر"
            hours > 0 && minutes > 0 -> "$hours ساعت و $minutes دقیقه دیگر"
            hours > 0 -> "$hours ساعت دیگر"
            minutes > 1 -> "$minutes دقیقه دیگر"
            else -> "کمتر از ۱ دقیقه دیگر"
        }
        return toPersianDigits(raw)
    }

    fun toPersianDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(PERSIAN_DIGITS[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
