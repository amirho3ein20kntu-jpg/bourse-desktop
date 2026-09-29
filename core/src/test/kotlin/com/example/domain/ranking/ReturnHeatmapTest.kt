package com.example.domain.ranking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnHeatmapTest {

    // ------------------------------------------------------------------
    // مقیاس ستون
    // ------------------------------------------------------------------

    @Test
    fun scaleIsTheLargestMagnitudeInTheColumn() {
        assertEquals(158.0, ReturnHeatmap.scaleOf(listOf(23.4, 158.0, 36.3))!!, 0.001)
    }

    @Test
    fun aLargeNegativeSetsTheScaleTooSoLossesAreNotFlattened() {
        assertEquals(80.0, ReturnHeatmap.scaleOf(listOf(12.0, -80.0, 5.0))!!, 0.001)
    }

    @Test
    fun missingValuesAreIgnoredWhenScaling() {
        assertEquals(40.0, ReturnHeatmap.scaleOf(listOf(null, 40.0, null))!!, 0.001)
    }

    @Test
    fun aColumnWithNoDataHasNoScale() {
        assertNull(ReturnHeatmap.scaleOf(listOf(null, null)))
        assertNull(ReturnHeatmap.scaleOf(emptyList()))
    }

    @Test
    fun aColumnOfAllZerosHasNoScale_colouringItWouldBeMeaningless() {
        assertNull(ReturnHeatmap.scaleOf(listOf(0.0, 0.0, 0.0)))
    }

    @Test
    fun brokenNumbersAreIgnoredRatherThanPoisoningTheScale() {
        assertEquals(10.0, ReturnHeatmap.scaleOf(listOf(10.0, Double.NaN, Double.POSITIVE_INFINITY))!!, 0.001)
    }

    // ------------------------------------------------------------------
    // مقیاس دوسویه با لنگر روی صفر
    // ------------------------------------------------------------------

    @Test
    fun zeroSitsExactlyAtTheMidpoint() {
        assertEquals(0f, ReturnHeatmap.intensity(0.0, 100.0)!!, 0.0001f)
    }

    @Test
    fun theBestAndWorstReachTheEnds() {
        assertEquals(1f, ReturnHeatmap.intensity(100.0, 100.0)!!, 0.0001f)
        assertEquals(-1f, ReturnHeatmap.intensity(-100.0, 100.0)!!, 0.0001f)
    }

    @Test
    fun aWeakPositiveStaysOnThePositiveSide_notRecolouredAsALoss() {
        // در ستونی که همه مثبت‌اند، ضعیف‌ترین صندوق نباید قرمز دیده شود
        val scale = ReturnHeatmap.scaleOf(listOf(5.0, 50.0, 100.0))
        val weakest = ReturnHeatmap.intensity(5.0, scale)!!

        assertTrue("ضعیف‌ترینِ مثبت باید مثبت بماند", weakest > 0f)
        assertTrue("ولی کم‌رنگ", weakest < 0.1f)
    }

    @Test
    fun valuesBeyondTheScaleAreClamped() {
        assertEquals(1f, ReturnHeatmap.intensity(500.0, 100.0)!!, 0.0001f)
        assertEquals(-1f, ReturnHeatmap.intensity(-500.0, 100.0)!!, 0.0001f)
    }

    @Test
    fun aMissingValueHasNoIntensity_soTheCellCanShowADash() {
        assertNull(ReturnHeatmap.intensity(null, 100.0))
        assertNull(ReturnHeatmap.intensity(Double.NaN, 100.0))
    }

    @Test
    fun withoutAScaleEverythingSitsAtTheMidpoint() {
        assertEquals(0f, ReturnHeatmap.intensity(42.0, null)!!, 0.0001f)
    }

    // ------------------------------------------------------------------
    // آلفا
    // ------------------------------------------------------------------

    @Test
    fun noDataMeansNoColourAtAll() {
        assertEquals(0f, ReturnHeatmap.alphaFor(null), 0.0001f)
    }

    @Test
    fun exactlyZeroGetsNoFill_itIsTheNeutralMidpoint() {
        assertEquals(0f, ReturnHeatmap.alphaFor(0f), 0.0001f)
    }

    @Test
    fun theStrongestCellNeverGetsDarkEnoughToHideItsNumber() {
        assertEquals(ReturnHeatmap.MAX_ALPHA, ReturnHeatmap.alphaFor(1f), 0.0001f)
        assertEquals(ReturnHeatmap.MAX_ALPHA, ReturnHeatmap.alphaFor(-1f), 0.0001f)
        assertTrue("متن روی سلول باید خوانا بماند", ReturnHeatmap.MAX_ALPHA < 0.6f)
    }

    @Test
    fun evenTheFaintestNonZeroCellIsDistinguishableFromAnEmptyOne() {
        val faint = ReturnHeatmap.alphaFor(0.001f)

        assertTrue(faint >= ReturnHeatmap.MIN_ALPHA)
        assertTrue(faint > ReturnHeatmap.alphaFor(null))
    }

    @Test
    fun alphaGrowsWithMagnitudeRegardlessOfSign() {
        assertTrue(ReturnHeatmap.alphaFor(0.8f) > ReturnHeatmap.alphaFor(0.3f))
        assertEquals(ReturnHeatmap.alphaFor(0.6f), ReturnHeatmap.alphaFor(-0.6f), 0.0001f)
    }

    // ------------------------------------------------------------------
    // انتخاب رنگ
    // ------------------------------------------------------------------

    @Test
    fun signPicksTheHue() {
        assertTrue(ReturnHeatmap.isPositive(12.0))
        assertTrue("صفر سمت مثبت شمرده می‌شود", ReturnHeatmap.isPositive(0.0))
        assertTrue(!ReturnHeatmap.isPositive(-3.0))
    }

    @Test
    fun aRealColumnFromTheApiColoursSensibly() {
        // اعداد واقعی یک صندوق: ماهانه ۲۳.۵، سه‌ماهه ۳۶.۳، شش‌ماهه ۱۵.۳، سالانه ۱۵۸.۱
        val column = listOf(23.47, 36.32, 15.32, 158.08)
        val scale = ReturnHeatmap.scaleOf(column)

        assertNotNull(scale)
        val intensities = column.map { ReturnHeatmap.intensity(it, scale)!! }

        assertTrue("همه مثبت‌اند پس همه سمت سبز", intensities.all { it > 0f })
        assertEquals("بزرگ‌ترین باید پررنگ‌ترین باشد", 1f, intensities.max(), 0.0001f)
    }
}

/**
 * تست‌های مقیاس تک‌سویه — همان که امتیاز تداوم بازدهی از آن استفاده می‌کند.
 */
class SequentialAlphaTest {

    @Test
    fun missingScore_getsNoColor() {
        assertEquals(0f, ReturnHeatmap.sequentialAlpha(null), 1e-6f)
    }

    @Test
    fun zeroScore_stillGetsTheFloorColor_soItIsNotMistakenForMissingData() {
        assertEquals(ReturnHeatmap.MIN_ALPHA, ReturnHeatmap.sequentialAlpha(0), 1e-6f)
    }

    @Test
    fun fullScore_getsTheCeiling() {
        assertEquals(ReturnHeatmap.MAX_ALPHA, ReturnHeatmap.sequentialAlpha(100), 1e-6f)
    }

    @Test
    fun alphaRisesMonotonicallyWithScore() {
        val alphas = (0..100).map { ReturnHeatmap.sequentialAlpha(it) }
        assertTrue(alphas.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun outOfRangeScoresAreClamped_notExtrapolated() {
        assertEquals(ReturnHeatmap.MIN_ALPHA, ReturnHeatmap.sequentialAlpha(-40), 1e-6f)
        assertEquals(ReturnHeatmap.MAX_ALPHA, ReturnHeatmap.sequentialAlpha(400), 1e-6f)
    }

    @Test
    fun everyAlphaStaysWithinTheLegibleBand() {
        val alphas = (0..100).map { ReturnHeatmap.sequentialAlpha(it) }
        assertTrue(alphas.all { it >= ReturnHeatmap.MIN_ALPHA && it <= ReturnHeatmap.MAX_ALPHA })
    }
}
