package com.example.domain.analysis

import com.example.data.model.FundTechnicalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalInterpreterTest {

    private fun state(
        hasData: Boolean = true,
        rsi: Double? = null,
        sma50: Double? = null,
        sma200: Double? = null,
        trendWeekly: String = "",
        trendMonthly: String = ""
    ) = FundTechnicalState(
        symbol = "تست",
        hasData = hasData,
        rsi14 = rsi,
        priceVsSma50Pct = sma50,
        priceVsSma200Pct = sma200,
        trendWeekly = trendWeekly,
        trendMonthly = trendMonthly
    )

    // ---------- داده ناموجود ----------

    @Test
    fun missingValues_produceNoInterpretation() {
        assertNull(TechnicalInterpreter.sma200(null))
        assertNull(TechnicalInterpreter.sma50(null))
        assertNull(TechnicalInterpreter.rsi(null))
        assertNull(TechnicalInterpreter.support(null))
        assertNull(TechnicalInterpreter.resistance(null))
    }

    @Test
    fun nanAndInfinityAreTreatedAsMissing() {
        assertNull(TechnicalInterpreter.sma200(Double.NaN))
        assertNull(TechnicalInterpreter.rsi(Double.POSITIVE_INFINITY))
        assertNull(TechnicalInterpreter.sma50(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun stateWithoutData_hasNoSummary() {
        assertNull(TechnicalInterpreter.summary(state(hasData = false, rsi = 80.0, sma200 = 90.0)))
    }

    // ---------- SMA۲۰۰ ----------

    @Test
    fun farAboveLongTermAverage_warnsAboutACorrection() {
        val result = TechnicalInterpreter.sma200(52.0)
        assertNotNull(result)
        assertEquals(InterpretationTone.CAUTION, result!!.tone)
        assertTrue(result.text.contains("اصلاح"))
    }

    @Test
    fun theStretchWarningQuotesTheActualNumber() {
        // کاربر باید ببیند «۵۲٪»، نه یک هشدار کلی بدون عدد.
        assertTrue(TechnicalInterpreter.sma200(52.4)!!.text.contains("52"))
    }

    @Test
    fun moderatelyAboveLongTermAverage_isPositiveNotAWarning() {
        val result = TechnicalInterpreter.sma200(30.0)!!
        assertEquals(InterpretationTone.POSITIVE, result.tone)
    }

    @Test
    fun slightlyBelowLongTermAverage_isNeutral_notYetNegative() {
        assertEquals(InterpretationTone.NEUTRAL, TechnicalInterpreter.sma200(-5.0)!!.tone)
    }

    @Test
    fun clearlyBelowLongTermAverage_isNegative() {
        assertEquals(InterpretationTone.NEGATIVE, TechnicalInterpreter.sma200(-25.0)!!.tone)
    }

    @Test
    fun exactlyAtTheAverage_isPositiveSide() {
        assertEquals(InterpretationTone.POSITIVE, TechnicalInterpreter.sma200(0.0)!!.tone)
    }

    // ---------- SMA۵۰ ----------

    @Test
    fun theFiftyDayThresholdIsTighterThanTheTwoHundredDayOne() {
        // ۳۰٪ بالای میانگین ۵۰ روزه «کشیده» است، ولی همان عدد روی ۲۰۰ روزه
        // فقط یک روند صعودی سالم است. آستانه یکسان، این تفاوت را گم می‌کرد.
        assertEquals(InterpretationTone.CAUTION, TechnicalInterpreter.sma50(30.0)!!.tone)
        assertEquals(InterpretationTone.POSITIVE, TechnicalInterpreter.sma200(30.0)!!.tone)
    }

    @Test
    fun wellBelowTheFiftyDayAverage_isNegative() {
        assertEquals(InterpretationTone.NEGATIVE, TechnicalInterpreter.sma50(-20.0)!!.tone)
    }

    // ---------- RSI ----------

    @Test
    fun overboughtRsi_isACautionNotANegative() {
        // اشباع خرید یعنی «کشش زیاد بوده»، نه «بد است».
        val result = TechnicalInterpreter.rsi(78.0)!!
        assertEquals(InterpretationTone.CAUTION, result.tone)
        assertTrue(result.text.contains("اشباع خرید"))
    }

    @Test
    fun oversoldRsi_isFlaggedAsAPossibleEntry_withACondition() {
        val result = TechnicalInterpreter.rsi(22.0)!!
        assertEquals(InterpretationTone.CAUTION, result.tone)
        // شرطی بودن مهم است: «اگر روند بلندمدت سالم باشد».
        assertTrue(result.text.contains("اگر"))
    }

    @Test
    fun midRangeRsi_isNeutral() {
        assertEquals(InterpretationTone.NEUTRAL, TechnicalInterpreter.rsi(50.0)!!.tone)
    }

    @Test
    fun rsiJustAboveFifty_favoursBuyers() {
        assertEquals(InterpretationTone.POSITIVE, TechnicalInterpreter.rsi(60.0)!!.tone)
    }

    @Test
    fun rsiBoundariesLandOnTheExpectedSide() {
        assertEquals(InterpretationTone.CAUTION, TechnicalInterpreter.rsi(70.0)!!.tone)
        assertEquals(InterpretationTone.POSITIVE, TechnicalInterpreter.rsi(55.0)!!.tone)
        assertEquals(InterpretationTone.NEUTRAL, TechnicalInterpreter.rsi(45.0)!!.tone)
        assertEquals(InterpretationTone.NEGATIVE, TechnicalInterpreter.rsi(30.0)!!.tone)
    }

    @Test
    fun everyRsiValueProducesAnInterpretation() {
        (0..100).forEach { assertNotNull("RSI=$it", TechnicalInterpreter.rsi(it.toDouble())) }
    }

    // ---------- حمایت و مقاومت ----------

    @Test
    fun sittingOnSupport_isFlagged() {
        val result = TechnicalInterpreter.support(1.5)!!
        assertEquals(InterpretationTone.CAUTION, result.tone)
        assertTrue(result.text.contains("حمایت"))
    }

    @Test
    fun supportDistanceSignIsIgnored_onlyMagnitudeMatters() {
        // حمایت پایین‌تر از قیمت است، پس API ممکن است عدد منفی بدهد.
        assertEquals(
            TechnicalInterpreter.support(2.0)!!.text,
            TechnicalInterpreter.support(-2.0)!!.text
        )
    }

    @Test
    fun rightUnderResistance_mentionsVolume() {
        assertTrue(TechnicalInterpreter.resistance(2.0)!!.text.contains("حجم"))
    }

    // ---------- جمع‌بندی ----------

    @Test
    fun stretchedAndOverbought_produceTheStrongestWarning() {
        val summary = TechnicalInterpreter.summary(state(rsi = 76.0, sma200 = 55.0))!!
        assertTrue(summary.contains("۲۰۰ روزه"))
        assertTrue(summary.contains("اشباع خرید"))
        assertTrue(summary.contains("احتمال اصلاح کوتاه‌مدت بالاست"))
    }

    @Test
    fun theSummaryQuotesTheNumberTheUserAskedAbout() {
        // درخواست کاربر عیناً همین بود: «چون ۵۰ درصد بالای میانگین دویست
        // روزه است ممکن است اصلاح کند».
        val summary = TechnicalInterpreter.summary(state(sma200 = 50.0))!!
        assertTrue(summary, summary.contains("50"))
        assertTrue(summary, summary.contains("اصلاح"))
    }

    @Test
    fun aCalmFundFallsBackToDescribingItsTrend() {
        val summary = TechnicalInterpreter.summary(
            state(rsi = 52.0, sma200 = 5.0, trendWeekly = "up", trendMonthly = "up")
        )!!
        assertTrue(summary, summary.contains("صعودی"))
    }

    @Test
    fun conflictingTrendsAreDescribedAsSuch() {
        val summary = TechnicalInterpreter.summary(
            state(rsi = 50.0, sma200 = 3.0, trendWeekly = "down", trendMonthly = "up")
        )!!
        assertTrue(summary, summary.contains("هفته اخیر منفی"))
    }

    @Test
    fun aFundWithNothingNoteworthy_getsNoSummary() {
        val summary = TechnicalInterpreter.summary(state(rsi = 50.0, sma200 = 4.0))
        assertNull(summary)
    }

    @Test
    fun belowTrendAndOversold_warnsThatTheBounceNeedsConfirmation() {
        val summary = TechnicalInterpreter.summary(state(rsi = 25.0, sma200 = -30.0))!!
        assertTrue(summary, summary.contains("تأیید"))
    }

    @Test
    fun summaryNeverEndsMidSentence() {
        val samples = listOf(
            state(rsi = 80.0, sma200 = 60.0),
            state(rsi = 20.0, sma200 = -40.0),
            state(rsi = 50.0, sma200 = 0.0, trendWeekly = "up", trendMonthly = "up"),
            state(rsi = 75.0, sma200 = 10.0)
        )
        samples.mapNotNull { TechnicalInterpreter.summary(it) }.forEach {
            assertTrue("«$it»", it.endsWith(".") || it.endsWith("است") || it.endsWith("باشد") ||
                    it.endsWith("هست") || it.endsWith("بالاست") || it.endsWith("دارد") ||
                    it.endsWith("شود"))
        }
    }

    @Test
    fun summaryDoesNotTellTheUserToBuyOrSell() {
        // این تفسیرها توصیفی‌اند. سیگنال خرید و فروش جای دیگری ساخته می‌شود و
        // بر پایه استراتژی است، نه یک اندیکاتور تنها.
        val samples = listOf(
            state(rsi = 80.0, sma200 = 60.0),
            state(rsi = 20.0, sma200 = -40.0),
            state(rsi = 50.0, sma200 = 2.0, trendWeekly = "up", trendMonthly = "up")
        )
        samples.mapNotNull { TechnicalInterpreter.summary(it) }.forEach { summary ->
            assertTrue("«$summary»", !summary.contains("بخرید") && !summary.contains("بفروشید"))
        }
    }
}
