package com.example.domain.portfolio

import com.example.data.model.AssetCategory
import com.example.data.model.PortfolioEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioEditTest {

    private val imported = PortfolioEntity(
        id = 7L,
        symbol = "اهرم",
        assetCategory = AssetCategory.STOCK,
        currentValue = 350_000_000.0,
        quantity = 140_000L,
        lastPrice = 2_500.0,
        averagePrice = 1_900.0,
        manualTargetPercent = 12.5
    )

    // ------------------------------------------------------------------
    // رگرسیون اصلی: ویرایش نباید قیمت سر به سر را پاک کند
    // ------------------------------------------------------------------

    @Test
    fun edit_keepsBreakEvenPriceImportedFromBrokerFile() {
        val edited = PortfolioEdit.applyManualEdit(
            existing = imported,
            id = imported.id,
            symbol = "اهرم",
            category = AssetCategory.STOCK,
            currentValueRial = 380_000_000.0,
            quantity = 140_000L,
            lastPriceRial = 2_714.0
        )

        assertEquals(1_900.0, edited.averagePrice, 0.001)
        assertEquals(380_000_000.0, edited.currentValue, 0.001)
        assertEquals(2_714.0, edited.lastPrice, 0.001)
    }

    @Test
    fun edit_keepsManualTargetPercent() {
        val edited = PortfolioEdit.applyManualEdit(
            existing = imported,
            id = imported.id,
            symbol = "اهرم",
            category = AssetCategory.STOCK,
            currentValueRial = 380_000_000.0,
            quantity = 140_000L,
            lastPriceRial = 2_714.0
        )

        assertEquals(12.5, edited.manualTargetPercent!!, 0.001)
    }

    @Test
    fun edit_keepsBreakEvenEvenWhenQuantityIsClearedToZero() {
        val edited = PortfolioEdit.applyManualEdit(
            existing = imported,
            id = imported.id,
            symbol = "اهرم",
            category = AssetCategory.STOCK,
            currentValueRial = 380_000_000.0,
            quantity = 0L,
            lastPriceRial = 0.0
        )

        assertEquals(1_900.0, edited.averagePrice, 0.001)
    }

    // ------------------------------------------------------------------
    // نماد جدید
    // ------------------------------------------------------------------

    @Test
    fun newSymbolWithQuantity_seedsBreakEvenFromTodayPrice_soProfitStartsAtZero() {
        val created = PortfolioEdit.applyManualEdit(
            existing = null,
            id = 0L,
            symbol = "طلا",
            category = AssetCategory.GOLD,
            currentValueRial = 280_000_000.0,
            quantity = 14_000L,
            lastPriceRial = 20_000.0
        )

        assertEquals(20_000.0, created.averagePrice, 0.001)
        // سود/زیان = ارزش روز − (تعداد × سر به سر) = صفر، نه کل ارزش
        assertEquals(
            created.currentValue,
            created.quantity * created.averagePrice,
            0.001
        )
        assertNull(created.manualTargetPercent)
    }

    @Test
    fun newSymbolWithoutQuantity_hasNoBreakEven() {
        val created = PortfolioEdit.applyManualEdit(
            existing = null,
            id = 0L,
            symbol = "کمند",
            category = AssetCategory.FIXED_INCOME,
            currentValueRial = 220_000_000.0,
            quantity = 0L,
            lastPriceRial = 0.0
        )

        assertEquals(0.0, created.averagePrice, 0.001)
    }

    @Test
    fun existingBreakEvenOfZeroFallsBackToTodayPrice() {
        val noBreakEven = imported.copy(averagePrice = 0.0)

        val edited = PortfolioEdit.applyManualEdit(
            existing = noBreakEven,
            id = noBreakEven.id,
            symbol = "اهرم",
            category = AssetCategory.STOCK,
            currentValueRial = 380_000_000.0,
            quantity = 140_000L,
            lastPriceRial = 2_714.0
        )

        assertEquals(2_714.0, edited.averagePrice, 0.001)
    }

    // ------------------------------------------------------------------
    // پرچم‌های محافظت از ورودی کاربر (رفتار قبلی باید حفظ شود)
    // ------------------------------------------------------------------

    @Test
    fun valueIsLockedOnlyWhenThereIsNoQuantityToRevalueFrom() {
        val withQuantity = PortfolioEdit.applyManualEdit(
            existing = null, id = 0L, symbol = "طلا",
            category = AssetCategory.GOLD,
            currentValueRial = 100.0, quantity = 10L, lastPriceRial = 10.0
        )
        val withoutQuantity = PortfolioEdit.applyManualEdit(
            existing = null, id = 0L, symbol = "طلا",
            category = AssetCategory.GOLD,
            currentValueRial = 100.0, quantity = 0L, lastPriceRial = 0.0
        )

        assertFalse(withQuantity.isValueManuallySet)
        assertTrue(withoutQuantity.isValueManuallySet)
        assertTrue(withQuantity.isCategoryManuallySet)
    }

    @Test
    fun symbolIsTrimmed() {
        val created = PortfolioEdit.applyManualEdit(
            existing = null, id = 0L, symbol = "  عیار  ",
            category = AssetCategory.GOLD,
            currentValueRial = 100.0, quantity = 0L, lastPriceRial = 0.0
        )

        assertEquals("عیار", created.symbol)
    }

    @Test
    fun manualEdit_keepsTheHoldLockFlag() {
        // قفل نگه‌داری از صفحه سیگنال‌ها روشن می‌شود، نه از این دیالوگ؛
        // یک ویرایش ساده ارزش نباید بی‌صدا قفل را بردارد.
        val existing = PortfolioEntity(
            id = 7, symbol = "طلا", assetCategory = AssetCategory.GOLD,
            currentValue = 500_000_000.0, quantity = 100_000L, lastPrice = 5_000.0,
            isHoldLocked = true
        )

        val edited = PortfolioEdit.applyManualEdit(
            existing = existing,
            id = 7,
            symbol = "طلا",
            category = AssetCategory.GOLD,
            currentValueRial = 520_000_000.0,
            quantity = 100_000L,
            lastPriceRial = 5_200.0
        )

        assertTrue(edited.isHoldLocked)
    }

    @Test
    fun newSymbol_startsUnlocked() {
        val created = PortfolioEdit.applyManualEdit(
            existing = null,
            id = 0L,
            symbol = "کمند",
            category = AssetCategory.FIXED_INCOME,
            currentValueRial = 100_000_000.0,
            quantity = 10_000L,
            lastPriceRial = 10_000.0
        )

        assertFalse(created.isHoldLocked)
    }
}
