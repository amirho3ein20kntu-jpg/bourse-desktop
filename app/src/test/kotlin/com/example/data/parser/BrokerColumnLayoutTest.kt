package com.example.data.parser

import com.example.data.model.AssetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست چیدمان ستون‌های فایل خروجی کارگزاری.
 *
 * سربرگ‌ها عیناً از یک فایل واقعی `Portfolio-export.xlsx` گرفته شده‌اند
 * (۴۱ ستون، A تا AO). مقادیر ریالی‌اند.
 *
 * این تست روی `extractPortfolioItems` کار می‌کند که خالص است و به اندروید
 * وابستگی ندارد؛ خواندن خودِ ZIP جداگانه است.
 */
class BrokerColumnLayoutTest {

    /** سربرگ واقعی کارگزاری — ترتیب و متن دقیقاً مثل فایل خروجی */
    private val header = listOf(
        "نام نماد",                                            // A  0
        "تاریخ آخرین تراکنش و رویداد",                          // B  1
        "تعداد دارایی",                                        // C  2
        "تعداد سهام جایزه",                                    // D  3
        "تعداد حق تقدم",                                       // E  4
        "ارزش فعلی",                                           // F  5
        "آخرین قیمت",                                          // G  6
        "درصد آخرین قیمت",                                     // H  7
        " قیمت پایانی",                                        // I  8
        "درصد قیمت پایانی",                                    // J  9
        "سود و زیان امروز",                                    // K 10
        "سود نقدی مجامع در آخرین دوره",                        // L 11
        "آخرین سود نقدی مجمع",                                 // M 12
        "سود نقدی کل مجامع",                                   // N 13
        "سود و زیان فعلی",                                     // O 14
        "درصد سود و زیان فعلی",                                // P 15
        "قیمت سر به سر بر حسب میانگین خرید در آخرین دوره",      // Q 16
        "قیمت سر به سر  بر حسب میانگین خرید در آخرین دوره تعدیل نشده توسط سود نقدی مجمع" // R 17
    )

    private fun row(vararg cells: String): List<String> {
        val out = cells.toMutableList()
        while (out.size < header.size) out.add("")
        return out
    }

    /** دو ردیف واقعی (اعداد ریالی، با اعشار — همان‌طور که کارگزاری می‌دهد) */
    private fun realRows() = listOf(
        header,
        row("عیار", "14050615", "801", "0", "0", "512585659.1988", "640701",
            "-2.01", "640547", "-2.03", "285626.82", "0", "0", "0",
            "285626.82", "0.05", "640343.984", "640343.984"),
        row("درخشان", "14050615", "71861", "0", "0", "2942765438.8", "41000",
            "-1.87", "41043", "-1.76", "-7071122.39", "0", "0", "0",
            "-7071122.39", "-0.24", "41500.0", "41500.0")
    )

    @Test
    fun realBrokerLayout_isParsedCorrectly() {
        val items = ExcelAndCsvParser.extractPortfolioItems(realRows(), emptyMap())

        assertEquals(2, items.size)

        val ayar = items.first { it.symbol == "عیار" }
        assertEquals(801L, ayar.quantity)                          // ستون C
        assertEquals(512_585_659.1988, ayar.currentValue, 0.01)     // ستون F (ریال)
        assertEquals(640_701.0, ayar.lastPrice, 0.01)               // ستون G
        assertEquals(640_343.984, ayar.averagePrice, 0.01)          // ستون Q
        assertEquals(AssetCategory.GOLD, ayar.assetCategory)        // «عیار» صندوق طلاست

        val derakhshan = items.first { it.symbol == "درخشان" }
        assertEquals(71_861L, derakhshan.quantity)
        assertEquals(2_942_765_438.8, derakhshan.currentValue, 0.1)
        assertEquals(AssetCategory.GOLD, derakhshan.assetCategory)
    }

    @Test
    fun averagePriceColumn_isFoundByHeaderNotByFixedIndex() {
        // اگر کارگزاری یک ستون اضافه کند، همه‌چیز یک خانه جابه‌جا می‌شود.
        // تشخیص باید از روی متن سربرگ باشد، نه ایندکس ثابت ۱۶.
        val shifted = realRows().map { r ->
            val m = r.toMutableList()
            m.add(1, if (r === header) "ستون اضافی" else "x")
            m
        }

        val items = ExcelAndCsvParser.extractPortfolioItems(shifted, emptyMap())
        val ayar = items.firstOrNull { it.symbol == "عیار" }
        assertTrue("نماد باید همچنان پیدا شود", ayar != null)
        assertEquals("قیمت سر به سر باید از سربرگ پیدا شود", 640_343.984, ayar!!.averagePrice, 0.01)
    }

    @Test
    fun summaryRowsAreSkipped() {
        val withTotals = realRows() + listOf(
            row("جمع کل", "", "", "", "", "3455351098.0"),
            row("مانده ریالی", "", "", "", "", "12000000.0")
        )

        val items = ExcelAndCsvParser.extractPortfolioItems(withTotals, emptyMap())
        assertEquals(2, items.size)
        assertTrue(items.none { it.symbol.contains("جمع") })
        assertTrue(items.none { it.symbol.contains("مانده") })
    }

    @Test
    fun persianDigitsAndSeparators_areParsed() {
        val persian = listOf(
            header,
            row("طلا", "۱۴۰۵/۰۶/۱۵", "۱٬۲۰۰", "۰", "۰", "۵۱۲٬۵۸۵٬۶۵۹", "۴۲۷٬۱۵۴")
        )

        val items = ExcelAndCsvParser.extractPortfolioItems(persian, emptyMap())
        assertEquals(1, items.size)
        assertEquals(1_200L, items[0].quantity)
        assertEquals(512_585_659.0, items[0].currentValue, 1.0)
    }

    @Test
    fun rowsWithoutValue_areSkipped() {
        val withEmpty = realRows() + listOf(row("نمادخالی", "14050615", "0", "0", "0", "0"))
        val items = ExcelAndCsvParser.extractPortfolioItems(withEmpty, emptyMap())
        assertEquals(2, items.size)
    }

    @Test
    fun knownCategoryMap_overridesDefaultGuess() {
        val overrides = mapOf("درخشان" to AssetCategory.STOCK)
        val items = ExcelAndCsvParser.extractPortfolioItems(realRows(), overrides)
        assertEquals(AssetCategory.STOCK, items.first { it.symbol == "درخشان" }.assetCategory)
    }
}
