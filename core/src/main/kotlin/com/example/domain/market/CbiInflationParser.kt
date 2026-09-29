package com.example.domain.market

/**
 * استخراج نرخ تورم نقطه‌به‌نقطه (منتهی به هر ماه) از صفحه بانک مرکزی
 * (`cbi.ir/Inflation/Inflation_FA.aspx`).
 *
 * برخلاف [AlanchandParser]، این صفحه ASP.NET WebForms است و جدول را برای
 * **سال انتخاب‌شده در `<select>`** برمی‌گرداند (پیش‌فرض: سال جاری، بدون نیاز
 * به postback). این پارسر HTML واقعی صفحه را می‌خواند — نمونه‌اش را Amir
 * در ۲۸ سپتامبر ۲۰۲۶ فرستاد، چون سایت از محیط توسعه در دسترس نیست.
 *
 * مقداری که این صفحه می‌دهد **نرخ تورم سالانه (۱۲ ماهه منتهی به آن ماه)**
 * است، نه افزایش قیمت همان یک ماه — پس زنجیره‌کردن ماهانه لازم نیست؛ همان
 * عدد مستقیماً قابل نمایش و مقایسه است.
 */
object CbiInflationParser {

    data class InflationRow(
        val jalaliYear: Int,
        /** ۱ = فروردین ... ۱۲ = اسفند */
        val jalaliMonth: Int,
        /** نرخ تورم نقطه‌به‌نقطه به درصد، مثلاً ۶۸.۴ */
        val ratePercent: Double
    )

    data class ParsedPage(
        val year: Int?,
        val rows: List<InflationRow>
    )

    private val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    fun parsePage(html: String): ParsedPage {
        val year = extractSelectedYear(html)
        val rows = mutableListOf<InflationRow>()

        if (year != null) {
            for (row in AlanchandParser.extractTableRows(html)) {
                if (row.size < 2) continue
                val monthIndex = monthIndexOf(row[0]) ?: continue
                val rate = extractSignedNumber(row[1]) ?: continue
                rows.add(InflationRow(year, monthIndex, rate))
            }
        }

        return ParsedPage(year, rows)
    }

    private fun monthIndexOf(cellText: String): Int? {
        val normalized = cellText.trim()
        val idx = MONTH_NAMES.indexOfFirst { normalized == it || normalized.contains(it) }
        return if (idx >= 0) idx + 1 else null
    }

    private val SELECTED_YEAR_REGEX =
        Regex("""<option\s+selected=["']selected["']\s+value=["'](\d{4})["']""")

    internal fun extractSelectedYear(html: String): Int? {
        val match = SELECTED_YEAR_REGEX.find(html)
            ?: Regex("""<option\s+value=["'](\d{4})["']\s+selected=["']selected["']""").find(html)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

    private fun toAsciiDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val idx = PERSIAN_DIGITS.indexOf(ch)
            sb.append(if (idx >= 0) ('0' + idx) else ch)
        }
        return sb.toString()
    }

    private val SIGNED_NUMBER_REGEX = Regex("""-?[0-9]+(?:\.[0-9]+)?""")

    /** نرخ تورم می‌تواند منفی باشد (تورم‌زدایی نادر)، پس علامت منفی هم پذیرفته می‌شود. */
    internal fun extractSignedNumber(cellText: String): Double? {
        val normalized = toAsciiDigits(cellText.trim())
        return SIGNED_NUMBER_REGEX.find(normalized)?.value?.toDoubleOrNull()
    }
}
