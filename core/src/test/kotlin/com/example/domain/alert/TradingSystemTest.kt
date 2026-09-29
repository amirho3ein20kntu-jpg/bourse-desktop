package com.example.domain.alert

import org.junit.Assert.assertEquals
import org.junit.Test

class TradingSystemTest {

    @Test
    fun presetThresholdsMapToTheSystemTheSettingsChipsName() {
        // همان چهار دکمه صفحه تنظیمات
        assertEquals(TradingSystem.HFR, TradingSystem.forThreshold(3.0))
        assertEquals(TradingSystem.MFR, TradingSystem.forThreshold(5.0))
        assertEquals(TradingSystem.MFR, TradingSystem.forThreshold(10.0))
        assertEquals(TradingSystem.LFR, TradingSystem.forThreshold(20.0))
    }

    @Test
    fun lowerBoundOfEachRangeBelongsToTheSlowerSystem() {
        assertEquals(TradingSystem.HFR, TradingSystem.forThreshold(4.9))
        assertEquals(TradingSystem.MFR, TradingSystem.forThreshold(19.9))
        assertEquals(TradingSystem.LFR, TradingSystem.forThreshold(50.0))
    }

    @Test
    fun thresholdsBelowTheHfrRangeStillCountAsHfr() {
        assertEquals(TradingSystem.HFR, TradingSystem.forThreshold(1.0))
    }

    @Test
    fun labelsShowNonOverlappingRanges() {
        assertEquals("سیستم پربسامد (HFR: ۲٪ تا کمتر از ۵٪)", TradingSystem.HFR.label)
        assertEquals("سیستم میان‌بسامد (MFR: ۵٪ تا کمتر از ۲۰٪)", TradingSystem.MFR.label)
        assertEquals("سیستم کم‌بسامد (LFR: ۲۰٪ و بیشتر)", TradingSystem.LFR.label)
    }
}
