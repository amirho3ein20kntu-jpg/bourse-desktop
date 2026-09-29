package com.example.domain.portfolio

import com.example.data.model.AssetCategory
import com.example.data.model.HoldingSnapshotEntity
import com.example.data.model.PortfolioEntity
import com.example.data.model.PortfolioSnapshotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioHistoryTest {

    private fun item(
        symbol: String,
        value: Double,
        quantity: Long = 0L,
        averagePrice: Double = 0.0,
        category: AssetCategory = AssetCategory.STOCK
    ) = PortfolioEntity(
        symbol = symbol,
        assetCategory = category,
        currentValue = value,
        quantity = quantity,
        averagePrice = averagePrice
    )

    private fun holding(day: String, symbol: String, value: Double, quantity: Long = 0L) =
        HoldingSnapshotEntity(
            day = day,
            symbol = symbol,
            assetCategory = AssetCategory.STOCK,
            quantity = quantity,
            valueRial = value
        )

    @Test
    fun `عکس روزانه سود را از همان PortfolioPnl می‌گیرد`() {
        val items = listOf(
            item("کمند", value = 1_100_000_000.0, quantity = 100_000L, averagePrice = 10_000.0),
            // بدون قیمت خرید: در ارزش کل هست ولی نباید سود ساختگی بسازد
            item("طلا", value = 500_000_000.0, category = AssetCategory.GOLD)
        )

        val capture = PortfolioHistory.capture(items, "2026-09-27", 1L, trIndex = 3.0)!!
        val pnl = PortfolioPnl.calculate(items)

        assertEquals(pnl.totalValueRial, capture.snapshot.totalValueRial, 0.01)
        assertEquals(pnl.principalRial, capture.snapshot.principalRial, 0.01)
        assertEquals(pnl.basisValueRial, capture.snapshot.basisValueRial, 0.01)

        val series = PortfolioHistory.pnlSeries(listOf(capture.snapshot))
        assertEquals(pnl.profitRial, series.single().profitRial, 0.01)
        assertEquals(pnl.profitPercent, series.single().profitPercent, 0.0001)
    }

    @Test
    fun `ردیف‌های تکراری یک نماد در ترکیب جمع می‌شوند`() {
        val capture = PortfolioHistory.capture(
            listOf(
                item("کمند", value = 300.0, quantity = 30L, averagePrice = 9.0),
                item("کمند ", value = 200.0, quantity = 20L),
                item("طلا", value = 500.0, quantity = 5L, averagePrice = 100.0)
            ),
            "2026-09-27", 1L, 0.0
        )!!

        assertEquals(2, capture.holdings.size)
        assertEquals(2, capture.snapshot.symbolCount)
        val kamand = capture.holdings.single { it.symbol == "کمند" }
        assertEquals(50L, kamand.quantity)
        assertEquals(500.0, kamand.valueRial, 0.01)
        // فقط ردیفی که قیمت خرید داشت در بهای تمام‌شده می‌آید
        assertEquals(270.0, kamand.principalRial, 0.01)
    }

    @Test
    fun `سبد خالی ثبت نمی‌شود`() {
        assertNull(PortfolioHistory.capture(emptyList(), "2026-09-27", 1L, 0.0))
        assertNull(PortfolioHistory.capture(listOf(item("کمند", 0.0)), "2026-09-27", 1L, 0.0))
    }

    @Test
    fun `روزهای بدون مبنای سود از نمودار سود کنار می‌روند`() {
        val snapshots = listOf(
            // ثبت‌شده پیش از نسخه ۹: basisValueRial صفر است
            PortfolioSnapshotEntity("2026-09-01", 1L, 1_000.0, principalRial = 900.0),
            PortfolioSnapshotEntity("2026-09-10", 2L, 1_000.0, principalRial = 0.0, basisValueRial = 1_000.0),
            PortfolioSnapshotEntity("2026-09-20", 3L, 1_200.0, principalRial = 1_000.0, basisValueRial = 950.0)
        )

        val series = PortfolioHistory.pnlSeries(snapshots)

        assertEquals(listOf("2026-09-20"), series.map { it.day })
        assertEquals(-50.0, series.single().profitRial, 0.01)
        assertEquals(-5.0, series.single().profitPercent, 0.0001)
    }

    @Test
    fun `ترکیب هر روز وزن درصدی نمادها را می‌دهد`() {
        val days = PortfolioHistory.composition(
            listOf(
                holding("2026-09-20", "کمند", 250.0),
                holding("2026-09-20", "طلا", 750.0),
                holding("2026-09-01", "کمند", 1_000.0)
            )
        )

        assertEquals(listOf("2026-09-01", "2026-09-20"), days.map { it.day })
        assertEquals(100.0, days[0].weights.getValue("کمند"), 0.001)
        assertEquals(75.0, days[1].weights.getValue("طلا"), 0.001)
        assertEquals(25.0, days[1].weights.getValue("کمند"), 0.001)
        assertEquals(listOf("طلا", "کمند"), days[1].weights.keys.toList())
    }

    @Test
    fun `تغییرات نماد اضافه و حذف و تعداد را نشان می‌دهد، جدیدترین اول`() {
        val changes = PortfolioHistory.changes(
            listOf(
                holding("2026-09-01", "کمند", 1_000.0, quantity = 100L),
                holding("2026-09-01", "آگاس", 500.0, quantity = 50L),
                holding("2026-09-01", "دستی", 300.0),
                // ۱۰ سپتامبر: فقط قیمت عوض شد — نباید در فهرست بیاید
                holding("2026-09-10", "کمند", 1_100.0, quantity = 100L),
                holding("2026-09-10", "آگاس", 450.0, quantity = 50L),
                holding("2026-09-10", "دستی", 300.0),
                // ۲۰ سپتامبر: آگاس فروخته شد، طلا خریده شد، کمند بیشتر شد
                holding("2026-09-20", "کمند", 2_200.0, quantity = 200L),
                holding("2026-09-20", "طلا", 800.0, quantity = 8L),
                holding("2026-09-20", "دستی", 300.0, quantity = 30L)
            )
        )

        val change = changes.single()
        assertEquals("2026-09-10", change.fromDay)
        assertEquals("2026-09-20", change.toDay)
        assertEquals(listOf("طلا"), change.added)
        assertEquals(listOf("آگاس"), change.removed)
        // «دستی» قبلاً تعداد نداشت؛ «۰ ← ۳۰» تغییر ساختگی است
        assertEquals(
            listOf(PortfolioHistory.QuantityChange("کمند", 100L, 200L)),
            change.quantityChanges
        )
    }

    @Test
    fun `یک روز داده هیچ تغییری نمی‌سازد`() {
        assertTrue(PortfolioHistory.changes(listOf(holding("2026-09-01", "کمند", 1.0))).isEmpty())
    }
}
