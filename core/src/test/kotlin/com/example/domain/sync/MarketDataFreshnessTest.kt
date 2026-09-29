package com.example.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketDataFreshnessTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    // ------------------------------------------------------------------
    // رگرسیون اصلی: داده کهنه نباید «زنده» برچسب بخورد
    // ------------------------------------------------------------------

    @Test
    fun cachedDataIsNotLabelledLive() {
        val label = MarketDataFreshness.sourceLabel(hasLiveData = true, ageMs = 3 * hour)

        assertFalse(label.contains("زنده"))
        assertTrue(label.contains("کش"))
        assertTrue(label.contains("3 ساعت پیش"))
    }

    @Test
    fun freshDataIsLabelledLive() {
        assertEquals(
            "FundBase (زنده)",
            MarketDataFreshness.sourceLabel(hasLiveData = true, ageMs = 5_000L)
        )
    }

    @Test
    fun noSuccessfulFetchYet_isNeverFresh() {
        assertFalse(MarketDataFreshness.isFresh(null))
        assertEquals(
            "FundBase (زمان نامشخص)",
            MarketDataFreshness.sourceLabel(hasLiveData = true, ageMs = null)
        )
    }

    @Test
    fun symbolWithoutAnyData() {
        assertEquals(
            "بدون داده",
            MarketDataFreshness.sourceLabel(hasLiveData = false, ageMs = 5_000L)
        )
    }

    // ------------------------------------------------------------------
    // مرز تازگی
    // ------------------------------------------------------------------

    @Test
    fun freshnessBoundaryIsInclusive() {
        assertTrue(MarketDataFreshness.isFresh(MarketDataFreshness.FRESH_LIMIT_MS))
        assertFalse(MarketDataFreshness.isFresh(MarketDataFreshness.FRESH_LIMIT_MS + 1))
    }

    @Test
    fun negativeAgeFromClockSkewIsNotFresh() {
        // ساعت دستگاه عقب رفته: سنِ منفی نباید «زنده» حساب شود
        assertFalse(MarketDataFreshness.isFresh(-1_000L))
        assertEquals("کمتر از یک دقیقه پیش", MarketDataFreshness.describeAge(-1_000L))
    }

    // ------------------------------------------------------------------
    // توصیف سن
    // ------------------------------------------------------------------

    @Test
    fun ageDescriptions() {
        assertEquals("کمتر از یک دقیقه پیش", MarketDataFreshness.describeAge(30_000L))
        assertEquals("1 دقیقه پیش", MarketDataFreshness.describeAge(minute))
        assertEquals("59 دقیقه پیش", MarketDataFreshness.describeAge(59 * minute))
        assertEquals("1 ساعت پیش", MarketDataFreshness.describeAge(hour))
        assertEquals("23 ساعت پیش", MarketDataFreshness.describeAge(23 * hour))
        assertEquals("2 روز پیش", MarketDataFreshness.describeAge(48 * hour))
    }
}
