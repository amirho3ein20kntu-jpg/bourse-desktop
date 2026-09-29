package com.example.domain.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlanchandParserTest {

    /**
     * ساختار جدول برگرفته از متن واقعی صفحه gold-price در ۲۸ سپتامبر ۲۰۲۶
     * (نسخه markdown که WebFetch برگرداند)، بازسازی‌شده به HTML جدولی معمولی.
     * چون محیط توسعه به alanchand.com دسترسی ندارد، این HTML واقعی سایت نیست؛
     * فقط فرضی معقول از ساختار <table><tr><td> است.
     */
    private val goldPageHtml = """
        <html><head><script>var x = 1; document.write('<tr><td>نویز</td></tr>');</script></head>
        <body>
        <nav><a href="/">ربع سکه</a> <a href="/">نیم سکه</a></nav>
        <div class="update-time">آخرین بروز رسانی : ۱۱:۱۵ دوشنبه ۶ مهر ۱۴۰۵</div>
        <table>
        <thead><tr><th></th><th>قیمت (تومان) + تغییرات</th><th>قیمت واقعی</th><th>مقدار حباب(درصد)</th></tr></thead>
        <tbody>
        <tr><td>آبشده(مثقال طلا)</td><td>۱۰۴,۱۸۶,۰۰۰ تومان۰.۱۰%</td><td>-</td><td>-</td></tr>
        <tr><td>گرم طلای 18 عیار</td><td>۲۴,۰۴۳,۱۲۰ <span>تومان</span>۰.۱۰%</td><td>۲۴,۱۱۵,۰۰۰ تومان</td><td>-۷۱,۸۸۰(-۰.۳۰%)</td></tr>
        <tr><td>سکه امامی (طرح جدید)</td><td>۲۴۲,۵۰۰,۰۰۰ تومان۰.۸۳%</td><td>۲۳۵,۶۶۵,۰۰۰ تومان</td><td>۶,۸۳۵,۰۰۰(۲.۹۰%)</td></tr>
        <tr><td>سکه بهار آزادی</td><td>۲۳۶,۰۰۰,۰۰۰ تومان۰.۴۳%</td><td>-</td><td>-</td></tr>
        <tr><td>نیم سکه</td><td>۱۲۴,۰۰۰,۰۰۰ تومان۱.۲۲%</td><td>۱۱۷,۹۶۴,۰۰۰ تومان</td><td>۶,۰۳۶,۰۰۰(۵.۱۲%)</td></tr>
        <tr><td>ربع سکه</td><td>۶۶,۰۰۰,۰۰۰ تومان۱.۵۴%</td><td>۵۹,۱۰۳,۰۰۰ تومان</td><td>۶,۸۹۷,۰۰۰(۱۱.۶۷%)</td></tr>
        <tr><td>سکه گرمی</td><td>۳۵,۰۰۰,۰۰۰ تومان۲.۹۴%</td><td>-</td><td>-</td></tr>
        </tbody>
        </table>
        </body></html>
    """.trimIndent()

    private val currencyPageHtml = """
        <html><body>
        <div>۱۲:۴۰ دوشنبه ۶ مهر ۱۴۰۵</div>
        <table>
        <tr><th>نام ارز</th><th>قیمت خرید</th><th>قیمت فروش</th><th>یک دلار چند؟</th></tr>
        <tr><td>دلار آمریکا</td><td>۲۴۰,۰۰۰</td><td>۲۴۲,۴۰۰</td><td>-</td></tr>
        <tr><td>یورو</td><td>۲۶۰,۰۰۰</td><td>۲۶۲,۰۰۰</td><td>-</td></tr>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun parseGoldPage_readsGram18AndAllThreeCoins() {
        val result = AlanchandParser.parseGoldPage(goldPageHtml)

        assertEquals(24_043_120.0, result.gram18Toman)
        assertEquals(242_500_000.0, result.fullCoinToman)
        assertEquals(124_000_000.0, result.halfCoinToman)
        assertEquals(66_000_000.0, result.quarterCoinToman)
    }

    @Test
    fun parseGoldPage_extractsUpdateText() {
        val result = AlanchandParser.parseGoldPage(goldPageHtml)
        assertEquals("۱۱:۱۵ دوشنبه ۶ مهر ۱۴۰۵", result.updateText)
    }

    @Test
    fun parseGoldPage_ignoresNavLinksOutsideTheTable() {
        // اگر پارسر روی متن ساده صفحه (نه فقط جدول) دنبال «ربع سکه» می‌گشت،
        // لینک ناوبری بالای صفحه قبل از ردیف واقعی جدول پیدا می‌شد.
        val result = AlanchandParser.parseGoldPage(goldPageHtml)
        assertEquals(66_000_000.0, result.quarterCoinToman)
    }

    @Test
    fun parseCurrencyPage_readsUsdSellPrice() {
        val result = AlanchandParser.parseCurrencyPage(currencyPageHtml)
        assertEquals(242_400.0, result.usdSellToman)
    }

    @Test
    fun parseCurrencyPage_doesNotMatchTheHeaderRow() {
        val headerOnly = """
            <table><tr><th>نام ارز</th><th>قیمت خرید</th><th>قیمت فروش</th></tr></table>
        """.trimIndent()
        val result = AlanchandParser.parseCurrencyPage(headerOnly)
        assertNull(result.usdSellToman)
    }

    @Test
    fun parseGoldPage_missingRowsStayNull() {
        val partial = """
            <table>
            <tr><td>گرم طلای 18 عیار</td><td>۲۴,۰۰۰,۰۰۰ تومان۰.۱۰%</td></tr>
            </table>
        """.trimIndent()
        val result = AlanchandParser.parseGoldPage(partial)
        assertEquals(24_000_000.0, result.gram18Toman)
        assertNull(result.fullCoinToman)
        assertNull(result.halfCoinToman)
        assertNull(result.quarterCoinToman)
    }

    @Test
    fun parseGoldPage_rejectsHalfCoinIfRatioToFullCoinIsImplausible() {
        // فرض غلط: نیم سکه را با قیمت گرم طلا اشتباه پارس کند (نسبت غیرمنطقی به تمام سکه)
        val corrupted = """
            <table>
            <tr><td>سکه امامی (طرح جدید)</td><td>۲۴۲,۵۰۰,۰۰۰ تومان</td></tr>
            <tr><td>نیم سکه</td><td>۲۴,۰۰۰,۰۰۰ تومان</td></tr>
            </table>
        """.trimIndent()
        val result = AlanchandParser.parseGoldPage(corrupted)
        assertEquals(242_500_000.0, result.fullCoinToman)
        assertNull(result.halfCoinToman)
    }

    @Test
    fun extractLeadingPrice_handlesPersianDigitsAndArabicComma() {
        assertEquals(66_000_000.0, AlanchandParser.extractLeadingPrice("۶۶,۰۰۰,۰۰۰ تومان۱.۵۴%"))
        assertEquals(66_000_000.0, AlanchandParser.extractLeadingPrice("۶۶،۰۰۰،۰۰۰ تومان"))
    }

    @Test
    fun extractLeadingPrice_returnsNullForNonNumericText() {
        assertNull(AlanchandParser.extractLeadingPrice("-"))
    }
}
