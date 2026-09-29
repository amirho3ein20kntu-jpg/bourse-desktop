package com.example.domain.sync

import com.example.data.model.AssetCategory
import com.example.data.model.PortfolioEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioPriceSyncTest {

    private fun item(
        symbol: String,
        value: Double,
        quantity: Long = 0L,
        lastPrice: Double = 0.0,
        manualValue: Boolean = false
    ) = PortfolioEntity(
        id = symbol.hashCode().toLong(),
        symbol = symbol,
        assetCategory = AssetCategory.STOCK,
        currentValue = value,
        quantity = quantity,
        lastPrice = lastPrice,
        isValueManuallySet = manualValue
    )

    private fun prices(vararg pairs: Pair<String, Double>): (String) -> Double? {
        val map = pairs.toMap()
        return { map[it] }
    }

    @Test
    fun valueIsRecomputed_whenQuantityKnownAndSyncEnabled() {
        val portfolio = listOf(item("اهرم", value = 100_000.0, quantity = 100L, lastPrice = 1_000.0))

        val r = PortfolioPriceSync.apply(portfolio, true, prices("اهرم" to 2_000.0))

        assertEquals(1, r.items.size)
        assertEquals(200_000.0, r.items[0].currentValue, 0.01)
        assertEquals(2_000.0, r.items[0].lastPrice, 0.01)
        assertEquals(1, r.revaluedCount)
    }

    @Test
    fun manualValueIsNeverOverwritten() {
        // کاربر ارزش را دستی زده؛ حتی با تعداد واحد و سینک روشن نباید تغییر کند
        val portfolio = listOf(
            item("طلا", value = 55_000_000.0, quantity = 100L, lastPrice = 1_000.0, manualValue = true)
        )

        val r = PortfolioPriceSync.apply(portfolio, true, prices("طلا" to 2_000.0))

        assertEquals(1, r.items.size)
        assertEquals("ارزش دستی نباید بازنویسی شود", 55_000_000.0, r.items[0].currentValue, 0.01)
        assertEquals("ولی قیمت روز باید به‌روز شود", 2_000.0, r.items[0].lastPrice, 0.01)
        assertEquals(0, r.revaluedCount)
        assertEquals(1, r.keptManual)
    }

    @Test
    fun valueUntouched_whenSyncDisabled() {
        val portfolio = listOf(item("اهرم", value = 100_000.0, quantity = 100L, lastPrice = 1_000.0))

        val r = PortfolioPriceSync.apply(portfolio, false, prices("اهرم" to 2_000.0))

        assertEquals(100_000.0, r.items[0].currentValue, 0.01)
        assertEquals(2_000.0, r.items[0].lastPrice, 0.01)
        assertEquals(0, r.revaluedCount)
        assertEquals(1, r.pricedCount)
    }

    @Test
    fun valueUntouched_whenQuantityUnknown() {
        // ردیف فقط ارزش دارد؛ چیزی برای ضرب کردن نیست
        val portfolio = listOf(item("کمند", value = 42_000_000.0, quantity = 0L))

        val r = PortfolioPriceSync.apply(portfolio, true, prices("کمند" to 10_000.0))

        assertEquals(42_000_000.0, r.items[0].currentValue, 0.01)
        assertEquals(0, r.revaluedCount)
    }

    @Test
    fun missingLivePrice_leavesRowCompletelyUntouched() {
        val portfolio = listOf(item("ناشناخته", value = 7_000_000.0, quantity = 50L, lastPrice = 140_000.0))

        val r = PortfolioPriceSync.apply(portfolio, true, prices("اهرم" to 2_000.0))

        assertTrue("ردیف بدون قیمت زنده نباید نوشته شود", r.items.isEmpty())
        assertEquals(listOf("ناشناخته"), r.notFound)
        assertFalse(r.hasChanges)
    }

    @Test
    fun zeroOrNegativePrice_isTreatedAsNoData() {
        val portfolio = listOf(item("اهرم", value = 100_000.0, quantity = 100L, lastPrice = 1_000.0))

        val zero = PortfolioPriceSync.apply(portfolio, true, prices("اهرم" to 0.0))
        assertTrue(zero.items.isEmpty())
        assertEquals(listOf("اهرم"), zero.notFound)

        val negative = PortfolioPriceSync.apply(portfolio, true, prices("اهرم" to -5.0))
        assertTrue(negative.items.isEmpty())
    }

    @Test
    fun unchangedRowsAreNotWritten() {
        // قیمت همان است و ارزش هم همان — نباید چیزی در دیتابیس نوشته شود
        val portfolio = listOf(item("اهرم", value = 200_000.0, quantity = 100L, lastPrice = 2_000.0))

        val r = PortfolioPriceSync.apply(portfolio, true, prices("اهرم" to 2_000.0))

        assertTrue("نوشتن بی‌مورد باعث emission اضافه روی Flow می‌شود", r.items.isEmpty())
        assertEquals(0, r.pricedCount)
        assertEquals(0, r.revaluedCount)
    }

    @Test
    fun mixedPortfolio_reportsAccurateCounts() {
        val portfolio = listOf(
            item("اهرم", value = 100_000.0, quantity = 100L, lastPrice = 1_000.0),          // بازمحاسبه
            item("طلا", value = 9_000_000.0, quantity = 10L, lastPrice = 500.0, manualValue = true), // محافظت
            item("کمند", value = 42_000_000.0, quantity = 0L),                               // بدون تعداد
            item("ناشناخته", value = 1_000.0, quantity = 5L)                                  // بدون قیمت
        )

        val r = PortfolioPriceSync.apply(
            portfolio, true,
            prices("اهرم" to 2_000.0, "طلا" to 900_000.0, "کمند" to 10_000.0)
        )

        assertEquals(3, r.items.size)          // سه ردیف تغییر کرد (قیمت یا ارزش)
        assertEquals(1, r.revaluedCount)       // فقط اهرم ارزشش عوض شد
        assertEquals(1, r.keptManual)          // طلا محافظت شد
        assertEquals(listOf("ناشناخته"), r.notFound)

        val gold = r.items.first { it.symbol == "طلا" }
        assertEquals(9_000_000.0, gold.currentValue, 0.01)

        val kamand = r.items.first { it.symbol == "کمند" }
        assertEquals(42_000_000.0, kamand.currentValue, 0.01)
    }

    @Test
    fun emptyPortfolio_isSafe() {
        val r = PortfolioPriceSync.apply(emptyList(), true, prices("اهرم" to 2_000.0))
        assertTrue(r.items.isEmpty())
        assertTrue(r.notFound.isEmpty())
        assertFalse(r.hasChanges)
    }
}
