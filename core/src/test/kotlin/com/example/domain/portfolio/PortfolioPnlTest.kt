package com.example.domain.portfolio

import com.example.data.model.AssetCategory
import com.example.data.model.PortfolioEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioPnlTest {

    private fun item(
        symbol: String,
        value: Double,
        quantity: Long = 0L,
        averagePrice: Double = 0.0
    ) = PortfolioEntity(
        symbol = symbol,
        assetCategory = AssetCategory.STOCK,
        currentValue = value,
        quantity = quantity,
        lastPrice = if (quantity > 0L) value / quantity else 0.0,
        averagePrice = averagePrice
    )

    @Test
    fun `سود فقط از ردیف‌هایی حساب می‌شود که قیمت خرید دارند`() {
        // کمند: ۱۰۰ هزار واحد، سر به سر ۱۰ هزار ریال، ارزش روز ۱.۱ میلیارد ریال
        // طلا: ردیفی که از اکسل بدون قیمت خرید آمده
        val pnl = PortfolioPnl.calculate(
            listOf(
                item("کمند", value = 1_100_000_000.0, quantity = 100_000L, averagePrice = 10_000.0),
                item("طلا", value = 500_000_000.0)
            )
        )

        // ارزش کل شامل هر دو ردیف است
        assertEquals(1_600_000_000.0, pnl.totalValueRial, 0.01)
        // ولی سود فقط از کمند می‌آید، نه ۶۰۰ میلیون ریالِ ساختگی
        assertEquals(100_000_000.0, pnl.profitRial, 0.01)
        assertEquals(10.0, pnl.profitPercent, 0.001)
        assertEquals(500_000_000.0, pnl.uncoveredValueRial, 0.01)
        assertTrue(pnl.isPartial)
        assertEquals(1, pnl.coveredCount)
        assertEquals(2, pnl.totalCount)
    }

    @Test
    fun `جمع سود تک‌تک نمادها با عدد کل سبد یکی است`() {
        val items = listOf(
            item("الف", value = 1_100_000_000.0, quantity = 100_000L, averagePrice = 10_000.0),
            item("ب", value = 900_000_000.0, quantity = 100_000L, averagePrice = 10_000.0),
            item("ج", value = 500_000_000.0)
        )

        val perSymbol = items
            .filter { PortfolioPnl.hasBasis(it) }
            .sumOf { it.currentValue - (it.quantity.toDouble() * it.averagePrice) }

        assertEquals(perSymbol, PortfolioPnl.calculate(items).profitRial, 0.01)
    }

    @Test
    fun `سبدی که هیچ قیمت خریدی ندارد سود نامعلوم دارد نه صفر`() {
        val pnl = PortfolioPnl.calculate(listOf(item("الف", value = 500_000_000.0)))

        assertFalse(pnl.hasBasis)
        assertEquals(0.0, pnl.profitRial, 0.01)
        assertEquals(0.0, pnl.principalRial, 0.01)
    }

    @Test
    fun `ردیف با تعداد ولی بدون قیمت خرید کنار گذاشته می‌شود`() {
        val pnl = PortfolioPnl.calculate(
            listOf(item("الف", value = 500_000_000.0, quantity = 1_000L, averagePrice = 0.0))
        )

        assertFalse(pnl.hasBasis)
        assertEquals(500_000_000.0, pnl.uncoveredValueRial, 0.01)
    }

    @Test
    fun `سبد خالی همه‌چیز را صفر برمی‌گرداند`() {
        val pnl = PortfolioPnl.calculate(emptyList())

        assertEquals(0.0, pnl.totalValueRial, 0.01)
        assertEquals(0.0, pnl.profitRial, 0.01)
        assertEquals(0, pnl.totalCount)
        assertFalse(pnl.isPartial)
    }

    @Test
    fun `زیان با علامت منفی برمی‌گردد`() {
        val pnl = PortfolioPnl.calculate(
            listOf(item("الف", value = 900_000_000.0, quantity = 100_000L, averagePrice = 10_000.0))
        )

        assertEquals(-100_000_000.0, pnl.profitRial, 0.01)
        assertEquals(-10.0, pnl.profitPercent, 0.001)
    }
}
