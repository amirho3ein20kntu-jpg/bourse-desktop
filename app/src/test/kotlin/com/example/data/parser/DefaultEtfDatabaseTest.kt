package com.example.data.parser

import com.example.data.model.AssetCategory
import com.example.data.parser.DefaultEtfDatabase.MatchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * دسته‌بندی نماد بی‌ضرر نیست: وزن هدف طبقات و در نتیجه سفارش‌های خرید/فروش
 * از آن مشتق می‌شوند. این تست‌ها هم درستی تطبیق و هم **قابل تشخیص بودن حدس**
 * را قفل می‌کنند.
 */
class DefaultEtfDatabaseTest {

    // ------------------------------------------------------------------
    // تطبیق دقیق — بالاترین اطمینان
    // ------------------------------------------------------------------

    @Test
    fun knownSymbols_matchExactlyAndAreNotGuesses() {
        val cases = mapOf(
            "اهرم" to AssetCategory.STOCK,
            "طلا" to AssetCategory.GOLD,
            "عیار" to AssetCategory.GOLD,
            "زیتون" to AssetCategory.MIXED,
            "کمند" to AssetCategory.FIXED_INCOME,
            "افران" to AssetCategory.FIXED_INCOME
        )

        for ((symbol, expected) in cases) {
            val match = DefaultEtfDatabase.matchCategoryForSymbol(symbol)
            assertEquals("دسته $symbol", expected, match.category)
            assertEquals("منبع $symbol", MatchSource.EXACT, match.source)
            assertFalse("$symbol نباید حدس باشد", match.isGuess)
        }
    }

    @Test
    fun exactMatchIgnoresSpacingAndZwnj() {
        // «امین یکم» با فاصله در جدول است؛ کاربر ممکن است بدون فاصله بنویسد
        val match = DefaultEtfDatabase.matchCategoryForSymbol("امینیکم")
        assertEquals(AssetCategory.FIXED_INCOME, match.category)
        assertEquals(MatchSource.EXACT, match.source)
    }

    @Test
    fun sepehrAndSeparAreDifferentFunds() {
        // «سپهر» مختلط و «سپر» درآمد ثابت — نباید به هم نگاشت شوند
        assertEquals(AssetCategory.MIXED, DefaultEtfDatabase.getCategoryForSymbol("سپهر"))
        assertEquals(AssetCategory.FIXED_INCOME, DefaultEtfDatabase.getCategoryForSymbol("سپر"))
    }

    // ------------------------------------------------------------------
    // کلیدواژه — نام خودش دسته را می‌گوید
    // ------------------------------------------------------------------

    @Test
    fun keywordMatchesAreTrustedAndNotGuesses() {
        val cases = mapOf(
            "صندوق طلای من" to AssetCategory.GOLD,
            "سکه ممتاز" to AssetCategory.GOLD,
            "شاخص سی" to AssetCategory.STOCK,
            "صندوق مختلط نوین" to AssetCategory.MIXED,
            "اوراق دولتی" to AssetCategory.FIXED_INCOME
        )

        for ((symbol, expected) in cases) {
            val match = DefaultEtfDatabase.matchCategoryForSymbol(symbol)
            assertEquals("دسته $symbol", expected, match.category)
            assertEquals("منبع $symbol", MatchSource.KEYWORD, match.source)
            assertFalse("$symbol نباید حدس باشد", match.isGuess)
        }
    }

    // ------------------------------------------------------------------
    // حدس — باید صریحاً حدس علامت بخورد
    // ------------------------------------------------------------------

    @Test
    fun completelyUnknownSymbol_fallsBackToStockAndIsMarkedAsGuess() {
        val match = DefaultEtfDatabase.matchCategoryForSymbol("نمادکاملاناشناخته")

        assertEquals(AssetCategory.STOCK, match.category)
        assertEquals(MatchSource.FALLBACK, match.source)
        assertTrue("پیش‌فرض باید حدس شمرده شود", match.isGuess)
    }

    @Test
    fun partialNameMatch_isMarkedAsGuess() {
        // «تابا» زیررشته «تابان» (طلا) است — ممکن است درست باشد، ممکن است نه.
        val match = DefaultEtfDatabase.matchCategoryForSymbol("تابا")

        assertTrue("تطبیق جزئی باید حدس شمرده شود", match.isGuess)
    }

    @Test
    fun emptySymbol_isAGuess() {
        val match = DefaultEtfDatabase.matchCategoryForSymbol("   ")

        assertEquals(AssetCategory.STOCK, match.category)
        assertTrue(match.isGuess)
    }

    @Test
    fun getCategoryForSymbol_agreesWithMatchCategoryForSymbol() {
        val symbols = listOf("اهرم", "طلا", "کمند", "زیتون", "نمادناشناس", "سکه", "")
        for (symbol in symbols) {
            assertEquals(
                symbol,
                DefaultEtfDatabase.matchCategoryForSymbol(symbol).category,
                DefaultEtfDatabase.getCategoryForSymbol(symbol)
            )
        }
    }

    // ------------------------------------------------------------------
    // صندوق‌های اهرمی
    // ------------------------------------------------------------------

    @Test
    fun leveragedFundsAreDetected() {
        for (symbol in listOf("اهرم", "شتاب", "موج", "جهش", "توان", "پیشران", "دوایکس", "نارنج اهرم")) {
            assertTrue("$symbol باید اهرمی شناخته شود", DefaultEtfDatabase.isLeveragedFund(symbol))
        }
    }

    @Test
    fun nonLeveragedFundsAreNotFlagged() {
        for (symbol in listOf("طلا", "عیار", "کمند", "افران", "زیتون", "آگاس")) {
            assertFalse("$symbol نباید اهرمی شناخته شود", DefaultEtfDatabase.isLeveragedFund(symbol))
        }
    }

    // ------------------------------------------------------------------
    // جدول پیش‌فرض
    // ------------------------------------------------------------------

    @Test
    fun everyDefaultMappingResolvesBackToItsOwnCategory() {
        // محافظ در برابر تداخل: افزودن نماد جدید نباید دسته نماد موجودی را عوض کند
        for ((symbol, category) in DefaultEtfDatabase.defaultFundMappings) {
            assertEquals(
                "نماد $symbol",
                category,
                DefaultEtfDatabase.getCategoryForSymbol(symbol)
            )
        }
    }

    @Test
    fun toEntities_coversEveryMapping() {
        val entities = DefaultEtfDatabase.toEntities()
        assertEquals(DefaultEtfDatabase.defaultFundMappings.size, entities.size)
        assertTrue(entities.all { it.symbol.isNotBlank() })
    }
}
