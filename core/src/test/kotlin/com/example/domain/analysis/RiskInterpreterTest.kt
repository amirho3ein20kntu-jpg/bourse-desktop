package com.example.domain.analysis

import com.example.data.model.FundRiskStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskInterpreterTest {

    private fun stats(
        drawdown: Double? = null,
        sharpe: Double? = null,
        volatility: Double? = null,
        windowDays: Int = 90
    ) = FundRiskStats(
        fundId = "f1",
        windowDays = windowDays,
        maxDrawdownPct = drawdown,
        sharpeRatio = sharpe,
        annualVolatilityPct = volatility
    )

    // ---------- داده ناموجود ----------

    @Test
    fun missingValues_produceNoInterpretation() {
        assertNull(RiskInterpreter.maxDrawdown(null))
        assertNull(RiskInterpreter.sharpe(null))
        assertNull(RiskInterpreter.volatility(null))
    }

    @Test
    fun nanAndInfinityAreTreatedAsMissing() {
        assertNull(RiskInterpreter.maxDrawdown(Double.NaN))
        assertNull(RiskInterpreter.sharpe(Double.POSITIVE_INFINITY))
        assertNull(RiskInterpreter.volatility(Double.NaN))
    }

    @Test
    fun emptyStats_haveNoSummary() {
        assertNull(RiskInterpreter.summary(null))
        assertNull(RiskInterpreter.summary(stats()))
    }

    // ---------- حداکثر افت ----------

    @Test
    fun drawdownSignIsIgnored_theApiMaySendEitherForm() {
        // FundBase «‎−۲۲.۴۵‎» نشان می‌دهد، ولی RPC ممکن است ۲۲.۴۵ مثبت بدهد.
        // هر دو یک افت‌اند و باید یک تفسیر بگیرند.
        assertEquals(
            RiskInterpreter.maxDrawdown(-22.45)!!.text,
            RiskInterpreter.maxDrawdown(22.45)!!.text
        )
    }

    @Test
    fun theRealFundbaseNumber_readsAsASizeableDrawdown() {
        // ‎−۲۲.۴۵٪‎ همان عددی است که کاربر روی سایت دیده.
        val result = RiskInterpreter.maxDrawdown(-22.45)!!
        assertEquals(InterpretationTone.CAUTION, result.tone)
        assertTrue(result.text, result.text.contains("قابل‌توجه"))
    }

    @Test
    fun tinyDrawdown_isPositive() {
        assertEquals(InterpretationTone.POSITIVE, RiskInterpreter.maxDrawdown(-2.0)!!.tone)
    }

    @Test
    fun ordinaryDrawdown_isNeutral() {
        assertEquals(InterpretationTone.NEUTRAL, RiskInterpreter.maxDrawdown(-10.0)!!.tone)
    }

    @Test
    fun severeDrawdown_isNegative() {
        assertEquals(InterpretationTone.NEGATIVE, RiskInterpreter.maxDrawdown(-45.0)!!.tone)
    }

    @Test
    fun drawdownExplainsItIsAPastLow_notACurrentLoss() {
        // کاربر معمولاً این دو را اشتباه می‌گیرد.
        val text = RiskInterpreter.maxDrawdown(-22.0)!!.text
        assertTrue(text, text.contains("اگر") || text.contains("بدترین"))
    }

    @Test
    fun everyDrawdownValueProducesAnInterpretation() {
        (0..100).forEach {
            assertNotNull("افت ‎-$it‎", RiskInterpreter.maxDrawdown(-it.toDouble()))
        }
    }

    // ---------- نسبت شارپ ----------

    @Test
    fun negativeSharpe_saysTheRiskDidNotPayOff() {
        val result = RiskInterpreter.sharpe(-0.4)!!
        assertEquals(InterpretationTone.NEGATIVE, result.tone)
    }

    @Test
    fun midSharpe_isAcceptable() {
        assertEquals(InterpretationTone.NEUTRAL, RiskInterpreter.sharpe(1.4)!!.tone)
    }

    @Test
    fun goodSharpe_isPositive() {
        assertEquals(InterpretationTone.POSITIVE, RiskInterpreter.sharpe(2.5)!!.tone)
    }

    @Test
    fun theRealFundbaseSharpe_isFlaggedAsTooGoodToExtrapolate() {
        // ۴.۷۱ عددی است که کاربر روی سایت دید. شارپ در این حد روی ۹۰ روز
        // تقریباً همیشه یعنی یک دوره صعودی خاص — نه کیفیت پایدار.
        val result = RiskInterpreter.sharpe(4.71)!!
        assertEquals(InterpretationTone.CAUTION, result.tone)
        assertTrue(result.text, result.text.contains("90"))
    }

    @Test
    fun theSharpeCaveatQuotesTheActualWindow() {
        val result = RiskInterpreter.sharpe(5.0, windowDays = 30)!!
        assertTrue(result.text, result.text.contains("30"))
    }

    // ---------- نوسان ----------

    @Test
    fun lowVolatility_isPositive() {
        assertEquals(InterpretationTone.POSITIVE, RiskInterpreter.volatility(9.0)!!.tone)
    }

    @Test
    fun ordinaryVolatility_isNeutral() {
        assertEquals(InterpretationTone.NEUTRAL, RiskInterpreter.volatility(22.0)!!.tone)
    }

    @Test
    fun theRealFundbaseVolatility_readsAsVeryHigh() {
        // ۵۲.۴۳٪ — همان عددی که کاربر دید.
        val result = RiskInterpreter.volatility(52.43)!!
        assertEquals(InterpretationTone.NEGATIVE, result.tone)
    }

    @Test
    fun volatilitySignIsIgnored() {
        assertEquals(
            RiskInterpreter.volatility(52.43)!!.text,
            RiskInterpreter.volatility(-52.43)!!.text
        )
    }

    // ---------- جمع‌بندی ----------

    @Test
    fun theThreeRealNumbersTogether_produceAUsefulSentence() {
        val summary = RiskInterpreter.summary(
            stats(drawdown = -22.45, sharpe = 4.71, volatility = 52.43)
        )!!
        assertTrue(summary, summary.contains("22"))
        assertTrue(summary, summary.contains("90"))
        assertTrue(summary, summary.contains("کوتاه"))
    }

    @Test
    fun aCalmFundGetsNoSummary() {
        assertNull(RiskInterpreter.summary(stats(drawdown = -4.0, sharpe = 1.2, volatility = 12.0)))
    }

    @Test
    fun heavyDrawdownWithoutAGreatSharpe_warnsAboutTolerance() {
        val summary = RiskInterpreter.summary(stats(drawdown = -38.0, sharpe = 0.8))!!
        assertTrue(summary, summary.contains("تحمل"))
    }

    @Test
    fun theSummaryUsesTheWindowItWasGiven() {
        val summary = RiskInterpreter.summary(
            stats(drawdown = -25.0, windowDays = 180)
        )!!
        assertTrue(summary, summary.contains("180"))
    }

    @Test
    fun summaryNeverTellsTheUserToBuyOrSell() {
        val samples = listOf(
            stats(drawdown = -22.45, sharpe = 4.71, volatility = 52.43),
            stats(drawdown = -40.0, sharpe = -0.2, volatility = 70.0),
            stats(drawdown = -18.0, sharpe = 2.2, volatility = 33.0)
        )
        samples.mapNotNull { RiskInterpreter.summary(it) }.forEach { summary ->
            assertTrue("«$summary»", !summary.contains("بخرید") && !summary.contains("بفروشید"))
        }
    }

    // ---------- مدل ----------

    @Test
    fun hasAnyStat_isFalseOnlyWhenEverythingIsMissing() {
        assertTrue(!stats().hasAnyStat)
        assertTrue(stats(drawdown = -1.0).hasAnyStat)
        assertTrue(stats(sharpe = 0.0).hasAnyStat)
        assertTrue(stats(volatility = 0.0).hasAnyStat)
    }
}
