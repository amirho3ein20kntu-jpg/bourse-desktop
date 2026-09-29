package com.example.data.parser

import com.example.data.model.AssetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * جدول دسته‌بندیِ واردشده بر حدس‌های داخلی اپ اولویت دارد، پس یک ردیفِ ناخوانا
 * که «سهامی» فرض شود می‌تواند دستهٔ درستِ یک صندوق درآمد ثابت را بازنویسی کند و
 * کل وزن‌های هدف را جابه‌جا کند. این تست‌ها آن مسیر را می‌بندند.
 */
class FundCategoryImportTest {

    @Test
    fun recognisedTypesAreImported() {
        val rows = listOf(
            listOf("نماد", "نوع صندوق"),
            listOf("عیار", "طلا"),
            listOf("اهرم", "سهامی اهرمی"),
            listOf("زیتون", "مختلط"),
            listOf("کمند", "درآمد ثابت")
        )

        val result = ExcelAndCsvParser.extractFundCategories(rows)

        assertEquals(4, result.categories.size)
        assertTrue(result.unrecognizedSymbols.isEmpty())
        val byName = result.categories.associate { it.symbol to it.category }
        assertEquals(AssetCategory.GOLD, byName["عیار"])
        assertEquals(AssetCategory.STOCK, byName["اهرم"])
        assertEquals(AssetCategory.MIXED, byName["زیتون"])
        assertEquals(AssetCategory.FIXED_INCOME, byName["کمند"])
    }

    @Test
    fun unreadableTypeIsSkipped_notSilentlyTreatedAsStock() {
        val rows = listOf(
            listOf("نماد", "نوع صندوق"),
            listOf("کمند", "درآمد ثابت"),
            listOf("افران", "؟؟؟"),
            listOf("کارا", "")
        )

        val result = ExcelAndCsvParser.extractFundCategories(rows)

        assertEquals(1, result.categories.size)
        assertEquals("کمند", result.categories.single().symbol)
        assertEquals(listOf("افران", "کارا"), result.unrecognizedSymbols)
    }

    @Test
    fun singleLetterCodesAreUnderstood() {
        val rows = listOf(
            listOf("نماد", "نوع"),
            listOf("طلا", "G"),
            listOf("آگاس", "S"),
            listOf("سپهر", "M"),
            listOf("اعتماد", "F")
        )

        val result = ExcelAndCsvParser.extractFundCategories(rows)

        assertEquals(4, result.categories.size)
        assertTrue(result.unrecognizedSymbols.isEmpty())
    }

    @Test
    fun emptyRowsProduceEmptyImport() {
        val result = ExcelAndCsvParser.extractFundCategories(emptyList())

        assertTrue(result.categories.isEmpty())
        assertTrue(result.unrecognizedSymbols.isEmpty())
    }

    // ------------------------------------------------------------------
    // خودِ قاعده تشخیص
    // ------------------------------------------------------------------

    @Test
    fun fromStringOrNull_returnsNullForUnknownText() {
        assertNull(AssetCategory.fromStringOrNull("نامشخص"))
        assertNull(AssetCategory.fromStringOrNull(""))
        assertNull(AssetCategory.fromStringOrNull("   "))
        assertNull(AssetCategory.fromStringOrNull("XYZ"))
    }

    @Test
    fun fromString_keepsStockFallbackForCallersThatNeedOne() {
        assertEquals(AssetCategory.STOCK, AssetCategory.fromString("نامشخص"))
        assertEquals(AssetCategory.GOLD, AssetCategory.fromString("صندوق طلا"))
    }
}
