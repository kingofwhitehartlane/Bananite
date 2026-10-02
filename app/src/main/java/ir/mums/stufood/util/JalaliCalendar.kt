package ir.mums.stufood.util

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class GregorianDate(val year: Int, val month: Int, val day: Int)

data class JalaliDate(val year: Int, val month: Int, val day: Int) : Comparable<JalaliDate> {
    override fun compareTo(other: JalaliDate): Int =
        compareValuesBy(this, other, JalaliDate::year, JalaliDate::month, JalaliDate::day)

    /** "1405/07/09" — the exact format the site expects. */
    fun format(): String = String.format(Locale.US, "%04d/%02d/%02d", year, month, day)
    override fun toString(): String = format()
}

/** Pure-arithmetic Gregorian <-> Jalali (Solar Hijri) conversion. No external deps. */
object JalaliCalendar {

    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    fun today(): JalaliDate {
        val d = LocalDate.now(ZoneId.of("Asia/Tehran"))
        return gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
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
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return JalaliDate(jy, jm, jd)
    }

    fun jalaliToGregorian(year: Int, month: Int, day: Int): GregorianDate {
        val jy = year + 1595
        var days = -355668 + 365 * jy + (jy / 33) * 8 + ((jy % 33) + 3) / 4 + day +
            (if (month < 7) (month - 1) * 31 else (month - 7) * 30 + 186)
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days -= 1
            gy += 100 * (days / 36524)
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
        val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        val salA = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > salA[gm]) {
            gd -= salA[gm]
            gm++
        }
        return GregorianDate(gy, gm, gd)
    }

    fun isLeap(year: Int): Boolean {
        val g = jalaliToGregorian(year, 12, 30)
        return gregorianToJalali(g.year, g.month, g.day) == JalaliDate(year, 12, 30)
    }

    fun daysInMonth(year: Int, month: Int): Int = when {
        month <= 6 -> 31
        month <= 11 -> 30
        else -> if (isLeap(year)) 30 else 29
    }

    /** Accepts Latin/Persian/Arabic digits and "/" or "-" separators. Null if invalid. */
    fun parse(text: String): JalaliDate? {
        val sb = StringBuilder()
        for (c in text.trim()) {
            sb.append(
                when (c) {
                    in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                    in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    else -> c
                }
            )
        }
        val m = Regex("(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})").find(sb.toString()) ?: return null
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        if (mo !in 1..12 || d !in 1..daysInMonth(y, mo)) return null
        return JalaliDate(y, mo, d)
    }
}
