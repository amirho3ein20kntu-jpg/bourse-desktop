package com.example.domain.broker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نمونه‌ها واقعی‌اند — از فرم سفارش صندوق «افران» در ایزی‌تریدر گرفته شده‌اند،
 * نه ساختگی.
 */
class EasyTraderLinkTest {

    private val realBuyUrl = "https://m.easytrader.ir/order-form/IRT1AFRN0001/0/1"
    private val realSellUrl = "https://m.easytrader.ir/order-form/IRT1AFRN0001/1/1"

    // ---------- ساخت آدرس ----------

    @Test
    fun buildsExactlyTheBuyUrlTheBrokerUses() {
        assertEquals(realBuyUrl, EasyTraderLink.orderFormUrl("IRT1AFRN0001", EasyTraderLink.Side.BUY))
    }

    @Test
    fun buildsExactlyTheSellUrlTheBrokerUses() {
        assertEquals(realSellUrl, EasyTraderLink.orderFormUrl("IRT1AFRN0001", EasyTraderLink.Side.SELL))
    }

    @Test
    fun buyAndSellDifferOnlyInTheSideSegment() {
        val buy = EasyTraderLink.orderFormUrl("IRT1AFRN0001", EasyTraderLink.Side.BUY)!!
        val sell = EasyTraderLink.orderFormUrl("IRT1AFRN0001", EasyTraderLink.Side.SELL)!!
        assertEquals(buy.replace("/0/1", "/1/1"), sell)
    }

    @Test
    fun lowercaseIsinIsNormalised() {
        assertEquals(realBuyUrl, EasyTraderLink.orderFormUrl("irt1afrn0001", EasyTraderLink.Side.BUY))
    }

    @Test
    fun surroundingWhitespaceIsTolerated() {
        assertEquals(realBuyUrl, EasyTraderLink.orderFormUrl("  IRT1AFRN0001 ", EasyTraderLink.Side.BUY))
    }

    // ---------- ISIN نامعتبر هیچ لینکی نمی‌سازد ----------

    @Test
    fun anInvalidIsinProducesNoLinkAtAll() {
        // یک کاراکتر اشتباه یعنی فرم سفارشِ اوراق دیگری باز می‌شود. اینجا
        // «تقریباً درست» بدتر از «هیچ» است.
        assertNull(EasyTraderLink.orderFormUrl("IRT1AFRN000", EasyTraderLink.Side.BUY))
        assertNull(EasyTraderLink.orderFormUrl("IRT1AFRN00011", EasyTraderLink.Side.BUY))
        assertNull(EasyTraderLink.orderFormUrl("USX1AFRN0001", EasyTraderLink.Side.BUY))
        assertNull(EasyTraderLink.orderFormUrl("افران", EasyTraderLink.Side.BUY))
        assertNull(EasyTraderLink.orderFormUrl("", EasyTraderLink.Side.BUY))
        assertNull(EasyTraderLink.orderFormUrl(null, EasyTraderLink.Side.BUY))
    }

    @Test
    fun isinValidationMatchesTheRealFormat() {
        assertTrue(EasyTraderLink.isValidIsin("IRT1AFRN0001"))
        assertTrue(EasyTraderLink.isValidIsin("irt1afrn0001"))
        assertFalse(EasyTraderLink.isValidIsin("IRT1AFRN"))
        assertFalse(EasyTraderLink.isValidIsin("اهرم"))
        assertFalse(EasyTraderLink.isValidIsin(null))
        assertFalse(EasyTraderLink.isValidIsin(""))
    }

    // ---------- خواندن ISIN از چیزی که کاربر می‌چسباند ----------

    @Test
    fun readsTheIsinBackOutOfAPastedOrderUrl() {
        assertEquals("IRT1AFRN0001", EasyTraderLink.extractIsin(realBuyUrl))
        assertEquals("IRT1AFRN0001", EasyTraderLink.extractIsin(realSellUrl))
    }

    @Test
    fun acceptsABareIsinToo() {
        // کاربر ممکن است خود کد را کپی کند نه آدرس را؛ رد کردنش فقط آزار است.
        assertEquals("IRT1AFRN0001", EasyTraderLink.extractIsin("IRT1AFRN0001"))
        assertEquals("IRT1AFRN0001", EasyTraderLink.extractIsin(" irt1afrn0001 "))
    }

    @Test
    fun survivesQueryStringsAndTrailingJunk() {
        assertEquals(
            "IRT1AFRN0001",
            EasyTraderLink.extractIsin("https://m.easytrader.ir/order-form/IRT1AFRN0001/0/1?from=watchlist#top")
        )
    }

    @Test
    fun pastedTextWithoutAnIsinYieldsNothing() {
        assertNull(EasyTraderLink.extractIsin("https://m.easytrader.ir/dashboard"))
        assertNull(EasyTraderLink.extractIsin("سلام"))
        assertNull(EasyTraderLink.extractIsin(""))
        assertNull(EasyTraderLink.extractIsin(null))
    }

    @Test
    fun aRoundTripKeepsTheCode() {
        val url = EasyTraderLink.orderFormUrl("IRT1AFRN0001", EasyTraderLink.Side.SELL)!!
        assertEquals("IRT1AFRN0001", EasyTraderLink.extractIsin(url))
    }

    // ---------- تشخیص آدرس فرم سفارش ----------

    @Test
    fun recognisesAnOrderFormUrl() {
        assertTrue(EasyTraderLink.isOrderFormUrl(realBuyUrl))
        assertFalse(EasyTraderLink.isOrderFormUrl("https://m.easytrader.ir/dashboard"))
        assertFalse(EasyTraderLink.isOrderFormUrl("https://example.com/order-form/IRT1AFRN0001/0/1"))
        assertFalse(EasyTraderLink.isOrderFormUrl(null))
    }
}
