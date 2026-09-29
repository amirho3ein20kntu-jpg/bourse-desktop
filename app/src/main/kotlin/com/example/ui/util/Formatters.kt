package com.example.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * قرارداد واحد پول در کل اپ:
 *
 *  - **ذخیره‌سازی و محاسبات: همیشه ریال.** فایل اکسل کارگزاری ریالی است و
 *    عیناً همان‌طور ذخیره می‌شود؛ موتور PCMR هم ریالی کار می‌کند.
 *  - **نمایش به کاربر: همیشه تومان.** `formatToman` و `formatCompactToman`
 *    ورودی ریالی می‌گیرند و خودشان بر ۱۰ تقسیم می‌کنند.
 *
 * `formatRial` عمداً در هیچ صفحه‌ای استفاده نمی‌شود. قاطی کردن دو واحد در یک
 * اپ مالی دقیقاً همان چیزی است که خطای ده‌برابری می‌سازد.
 */
object Formatters {

    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun toPersianDigits(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            if (ch in '0'..'9') {
                sb.append(persianDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private val numberFormat: DecimalFormat by lazy {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }
        DecimalFormat("#,###", symbols)
    }

    private val decimalFormat: DecimalFormat by lazy {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }
        DecimalFormat("#,##0.0", symbols)
    }

    fun formatNumber(amount: Double): String {
        return toPersianDigits(numberFormat.format(amount))
    }

    fun formatNumber(amount: Long): String {
        return toPersianDigits(numberFormat.format(amount))
    }

    fun formatDecimal(value: Double): String {
        return toPersianDigits(decimalFormat.format(value))
    }

    /**
     * Formats Rial value with Persian commas.
     */
    fun formatRial(amountRial: Double): String {
        return "${toPersianDigits(numberFormat.format(amountRial))} ریال"
    }

    /**
     * Converts Rial to Toman (divides by 10) and formats.
     */
    fun formatToman(amountRial: Double): String {
        val toman = amountRial / 10.0
        return "${toPersianDigits(numberFormat.format(toman))} تومان"
    }

    fun formatTomanDirect(amountToman: Double): String {
        return "${toPersianDigits(numberFormat.format(amountToman))} تومان"
    }

    /**
     * Human-readable compact format for large portfolio numbers:
     * e.g. "۱.۲۵ میلیارد تومان" or "۴۵.۲ میلیون تومان".
     */
    fun formatCompactToman(amountRial: Double): String {
        // عدد منفی هم باید خلاصه شود. قبلاً هیچ‌کدام از شرط‌های `>=` برای زیان
        // برقرار نمی‌شد و زیان به‌شکل کامل («-۴۵,۰۰۰,۰۰۰ تومان») در کارتی با
        // یک خط جا چاپ می‌شد، در حالی که سودِ هم‌اندازه «۴.۵ میلیون» بود.
        if (amountRial < 0.0) return "-" + formatCompactToman(-amountRial)
        val toman = amountRial / 10.0
        return when {
            toman >= 1_000_000_000 -> {
                val b = toman / 1_000_000_000.0
                "${toPersianDigits(decimalFormat.format(b))} میلیارد تومان"
            }
            toman >= 1_000_000 -> {
                val m = toman / 1_000_000.0
                "${toPersianDigits(decimalFormat.format(m))} میلیون تومان"
            }
            toman >= 1_000 -> {
                val k = toman / 1_000.0
                "${toPersianDigits(decimalFormat.format(k))} هزار تومان"
            }
            else -> {
                "${toPersianDigits(numberFormat.format(toman))} تومان"
            }
        }
    }

    fun formatPercent(percent: Double): String {
        return "${toPersianDigits(decimalFormat.format(percent))}٪"
    }
}
