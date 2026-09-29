package com.example.domain.market

/**
 * استخراج قیمت طلا، سکه و دلار آزاد از صفحات HTML سایت آلان‌چند
 * (`alanchand.com/gold-price` و `alanchand.com/currencies-price`).
 *
 * **هیچ کلید API رسمی‌ای اینجا استفاده نمی‌شود** — این صفحات عمومی و بدون
 * لاگین هستند. چون سایت خودش هیچ API مستندی برای این صفحه‌ها ندارد،
 * پارسر روی ساختار عمومی جدول HTML (`<tr>`/`<td>`) کار می‌کند، نه روی
 * کلاس‌های CSS خاص که هر بازطراحی سایت می‌تواند عوض کند.
 *
 * محیط توسعه به alanchand.com دسترسی شبکه ندارد (پراکسی سازمانی مسدودش
 * کرده)، پس این پارسر روی HTML واقعی سایت آزموده نشده — فقط روی نمونه‌های
 * دستی ساخته‌شده از متن صفحه. اگر ساختار واقعی صفحه فرق داشته باشد،
 * `parseGoldPage`/`parseCurrencyPage` مقدار `null` برمی‌گردانند (نه عدد
 * غلط) و UI باید «دریافت نشد» نشان دهد.
 */
object AlanchandParser {

    // ---------------------------------------------------------------------
    // نتیجه‌ها
    // ---------------------------------------------------------------------

    data class GoldQuotes(
        val gram18Toman: Double?,
        val quarterCoinToman: Double?,
        val halfCoinToman: Double?,
        val fullCoinToman: Double?,
        val updateText: String?
    )

    data class CurrencyQuote(
        val usdSellToman: Double?,
        val updateText: String?
    )

    // ---------------------------------------------------------------------
    // API عمومی
    // ---------------------------------------------------------------------

    fun parseGoldPage(html: String): GoldQuotes {
        var gram18: Double? = null
        var full: Double? = null
        var half: Double? = null
        var quarter: Double? = null

        for (row in extractTableRows(html)) {
            if (row.size < 2) continue
            val label = normalizeForMatch(row[0])
            if (label.isEmpty()) continue
            val price = extractLeadingPrice(row[1]) ?: continue

            when {
                gram18 == null && label.contains("گرمطلای18عیار") -> gram18 = price
                full == null && label.startsWith("سکهامامی") -> full = price
                half == null && label.startsWith("نیمسکه") -> half = price
                quarter == null && label.startsWith("ربعسکه") -> quarter = price
            }
        }

        val sane = sanityFilterAgainstFullCoin(gram18, quarter, half, full)
        return GoldQuotes(
            gram18Toman = sane.gram18,
            quarterCoinToman = sane.quarter,
            halfCoinToman = sane.half,
            fullCoinToman = sane.full,
            updateText = extractUpdateText(html)
        )
    }

    fun parseCurrencyPage(html: String): CurrencyQuote {
        var usdSell: Double? = null

        for (row in extractTableRows(html)) {
            if (row.size < 3) continue
            val label = normalizeForMatch(row[0])
            if (label.contains("دلارامریکا") || label.contains("دلارآمریکا".let(::normalizeForMatch))) {
                usdSell = extractLeadingPrice(row[2])
                if (usdSell != null) break
            }
        }

        return CurrencyQuote(
            usdSellToman = usdSell,
            updateText = extractUpdateText(html)
        )
    }

    // ---------------------------------------------------------------------
    // استخراج جدول
    // ---------------------------------------------------------------------

    private val ROW_REGEX = Regex("<tr[\\s\\S]*?</tr>", RegexOption.IGNORE_CASE)
    private val CELL_REGEX = Regex("<t[dh][^>]*>([\\s\\S]*?)</t[dh]>", RegexOption.IGNORE_CASE)
    private val TAG_REGEX = Regex("<[^>]+>")
    private val WHITESPACE_REGEX = Regex("\\s+")

    /** هر ردیف جدول را به لیست متن سلول‌هایش (بدون تگ) می‌شکند. */
    internal fun extractTableRows(html: String): List<List<String>> {
        val noScripts = html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")

        return ROW_REGEX.findAll(noScripts).map { rowMatch ->
            CELL_REGEX.findAll(rowMatch.value).map { cellMatch ->
                cleanCellText(cellMatch.groupValues[1])
            }.toList()
        }.filter { it.isNotEmpty() }.toList()
    }

    private fun cleanCellText(raw: String): String {
        val noTags = TAG_REGEX.replace(raw, " ")
        val decoded = decodeHtmlEntities(noTags)
        return WHITESPACE_REGEX.replace(decoded, " ").trim()
    }

    private fun decodeHtmlEntities(text: String): String {
        var result = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("‌", "") // نیم‌فاصله (ZWNJ)
        result = Regex("&#(\\d+);").replace(result) { m ->
            m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        }
        result = Regex("&#x([0-9a-fA-F]+);").replace(result) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
        }
        return result
    }

    // ---------------------------------------------------------------------
    // نرمال‌سازی و استخراج عدد
    // ---------------------------------------------------------------------

    private val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
    private val ARABIC_INDIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    private fun normalizeDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val pIdx = PERSIAN_DIGITS.indexOf(ch)
            val aIdx = ARABIC_INDIC_DIGITS.indexOf(ch)
            sb.append(
                when {
                    pIdx >= 0 -> ('0' + pIdx)
                    aIdx >= 0 -> ('0' + aIdx)
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    /** برای مقایسه برچسب ستون اول: بدون فاصله، بدون نیم‌فاصله، حروف یکدست. */
    private fun normalizeForMatch(text: String): String {
        return normalizeDigits(text)
            .replace(" ", "")
            .replace("‌", "")
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace('آ', 'ا')
            .replace('أ', 'ا')
            .replace('إ', 'ا')
    }

    private val LEADING_NUMBER_REGEX = Regex("[0-9]+(?:[,،][0-9]{3})*(?:\\.[0-9]+)?")

    /** اولین عدد داخل متن سلول (قیمت به تومان)، قبل از هر «تومان»/«$»/درصد تغییرات. */
    internal fun extractLeadingPrice(cellText: String): Double? {
        val normalized = normalizeDigits(cellText)
        val match = LEADING_NUMBER_REGEX.find(normalized) ?: return null
        val cleaned = match.value.replace(",", "").replace("،", "")
        val value = cleaned.toDoubleOrNull() ?: return null
        return value.takeIf { it > 0.0 }
    }

    private val UPDATE_TEXT_REGEX = Regex("آخرین\\s*بروز\\s*رسانی\\s*:?\\s*([^<\\n]{0,60})")

    internal fun extractUpdateText(html: String): String? {
        val stripped = TAG_REGEX.replace(html, "\n")
        val decoded = decodeHtmlEntities(stripped)
        val match = UPDATE_TEXT_REGEX.find(decoded) ?: return null
        val captured = WHITESPACE_REGEX.replace(match.groupValues[1], " ").trim()
        return captured.takeIf { it.isNotEmpty() }
    }

    // ---------------------------------------------------------------------
    // راستی‌آزمایی نسبت به سکه تمام
    // ---------------------------------------------------------------------

    private data class SaneCoinValues(
        val gram18: Double?,
        val quarter: Double?,
        val half: Double?,
        val full: Double?
    )

    /**
     * هر عدد باید نسبت معقولی به قیمت سکه تمام داشته باشد، وگرنه پارس اشتباه
     * بوده (مثلاً ستون جابه‌جا خوانده شده) و بهتر است `null` نمایش داده شود
     * تا عدد گمراه‌کننده.
     */
    private fun sanityFilterAgainstFullCoin(
        gram18: Double?,
        quarter: Double?,
        half: Double?,
        full: Double?
    ): SaneCoinValues {
        if (full == null || full <= 0.0) {
            // بدون مرجع، فقط مثبت بودن را بررسی می‌کنیم
            return SaneCoinValues(
                gram18 = gram18?.takeIf { it > 0.0 },
                quarter = quarter?.takeIf { it > 0.0 },
                half = half?.takeIf { it > 0.0 },
                full = null
            )
        }
        val g = gram18?.takeIf { it > 0.0 && (it / full) in 0.05..0.2 }
        val q = quarter?.takeIf { it > 0.0 && (it / full) in 0.2..0.5 }
        val h = half?.takeIf { it > 0.0 && (it / full) in 0.4..0.8 }
        return SaneCoinValues(g, q, h, full)
    }
}
