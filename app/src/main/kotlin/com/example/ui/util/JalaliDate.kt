package com.example.ui.util

import java.util.Locale

/**
 * تبدیل تاریخ میلادی به شمسی برای نمایش.
 *
 * تاریخچه با کلید میلادی `yyyy-MM-dd` ذخیره می‌شود چون مرتب‌سازی متنی‌اش با
 * ترتیب زمانی یکی است؛ این تبدیل فقط برای نشان دادن به کاربر است.
 * الگوریتم حسابی رایج (جلالی ↔ میلادی) که برای سال‌های ۱۳۰۰ تا ۱۴۷۰ با
 * تقویم رسمی یکی است.
 */
object JalaliDate {

    data class Date(val year: Int, val month: Int, val day: Int)

    private val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    fun fromGregorian(gy: Int, gm: Int, gd: Int): Date {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
                ((gy2 + 399) / 400) + gd + gdm[gm - 1]
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
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return Date(jy, jm, jd)
    }

    /** `yyyy-MM-dd` میلادی، یا null اگر قالب درست نباشد. */
    fun fromIsoDay(isoDay: String): Date? {
        val parts = isoDay.trim().split("-")
        if (parts.size != 3) return null
        val (y, m, d) = parts.map { it.toIntOrNull() ?: return null }
        if (m !in 1..12 || d !in 1..31) return null
        return fromGregorian(y, m, d)
    }

    /** «۱۴۰۵/۰۷/۰۵»؛ ورودی نامعتبر همان‌طور که هست برمی‌گردد. */
    fun formatShort(isoDay: String): String {
        val date = fromIsoDay(isoDay) ?: return isoDay
        return Formatters.toPersianDigits(
            String.format(Locale.US, "%04d/%02d/%02d", date.year, date.month, date.day)
        )
    }

    /** «۵ مهر ۱۴۰۵» */
    fun formatLong(isoDay: String): String {
        val date = fromIsoDay(isoDay) ?: return isoDay
        return Formatters.toPersianDigits("${date.day} ${monthNames[date.month - 1]} ${date.year}")
    }
}
