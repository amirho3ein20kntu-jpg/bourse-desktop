package com.example.domain.backup

import com.example.data.model.AssetCategory
import com.example.data.model.FundCategoryEntity
import com.example.data.model.PortfolioEntity
import com.example.data.model.SettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioBackupTest {

    private val portfolio = listOf(
        PortfolioEntity(
            id = 11L, symbol = "اهرم", assetCategory = AssetCategory.STOCK,
            currentValue = 350_000_000.0, quantity = 140_000L, lastPrice = 2_500.0,
            averagePrice = 1_900.0, manualTargetPercent = 12.5,
            isValueManuallySet = false, isCategoryManuallySet = true,
            isHoldLocked = true
        ),
        PortfolioEntity(
            id = 12L, symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME,
            currentValue = 220_000_000.0, quantity = 22_000L, lastPrice = 10_000.0
        )
    )

    private val categories = listOf(
        FundCategoryEntity("عیار", AssetCategory.GOLD, "صندوق عیار")
    )

    private val settings = SettingsEntity(
        riskTolerance = 17.5,
        timeHorizonMonths = 12,
        rebalanceThresholdPercent = 4.5,
        isManualWeightsEnabled = true,
        manualGoldWeight = 22.0,
        isLivePriceSyncEnabled = false
    )

    // ------------------------------------------------------------------
    // رفت و برگشت کامل
    // ------------------------------------------------------------------

    @Test
    fun roundTrip_preservesEveryFieldThatMatters() {
        val text = PortfolioBackup.encode(settings, portfolio, categories, "1405-06-19 10:00")
        val restored = PortfolioBackup.decode(text).getOrThrow()

        assertEquals(2, restored.portfolio.size)

        val leverage = restored.portfolio.first { it.symbol == "اهرم" }
        assertEquals(AssetCategory.STOCK, leverage.assetCategory)
        assertEquals(350_000_000.0, leverage.currentValue, 0.001)
        assertEquals(140_000L, leverage.quantity)
        assertEquals(2_500.0, leverage.lastPrice, 0.001)
        // قیمت سر به سر باید زنده بماند — همان چیزی که سود/زیان از آن می‌آید
        assertEquals(1_900.0, leverage.averagePrice, 0.001)
        assertEquals(12.5, leverage.manualTargetPercent!!, 0.001)
        assertTrue(leverage.isCategoryManuallySet)
        // قفل نگه‌داری هم باید از پشتیبان برگردد، وگرنه کاربر پس از بازیابی
        // بی‌خبر پیشنهاد فروش طلا می‌گیرد.
        assertTrue(leverage.isHoldLocked)

        assertEquals(1, restored.fundCategories.size)
        assertEquals(AssetCategory.GOLD, restored.fundCategories.single().category)
    }

    @Test
    fun roundTrip_preservesSettings() {
        val text = PortfolioBackup.encode(settings, portfolio, categories)
        val restored = PortfolioBackup.decode(text).getOrThrow().settings

        assertNotNull(restored)
        assertEquals(17.5, restored!!.riskTolerance, 0.001)
        assertEquals(12, restored.timeHorizonMonths)
        assertEquals(4.5, restored.rebalanceThresholdPercent, 0.001)
        assertTrue(restored.isManualWeightsEnabled)
        assertEquals(22.0, restored.manualGoldWeight, 0.001)
        assertEquals(false, restored.isLivePriceSyncEnabled)
        // شناسه تنظیمات همیشه ۱ است تا رکورد موجود جایگزین شود
        assertEquals(1, restored.id)
    }

    @Test
    fun restoredRowsHaveNoIds_soRoomAssignsFreshOnes() {
        val text = PortfolioBackup.encode(settings, portfolio, categories)
        val restored = PortfolioBackup.decode(text).getOrThrow()

        assertTrue(
            "بازیابی نباید شناسه‌های قدیمی را برگرداند",
            restored.portfolio.all { it.id == 0L }
        )
    }

    // ------------------------------------------------------------------
    // ورودی خراب نباید اپ را بشکند
    // ------------------------------------------------------------------

    @Test
    fun garbageTextFailsCleanly() {
        val result = PortfolioBackup.decode("این یک فایل پشتیبان نیست")

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull()?.message)
    }

    @Test
    fun emptyTextFailsCleanly() {
        assertTrue(PortfolioBackup.decode("").isFailure)
    }

    @Test
    fun backupFromANewerAppVersionIsRefusedWithAClearMessage() {
        val future = """{"formatVersion": 99, "portfolio": []}"""

        val result = PortfolioBackup.decode(future)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("به‌روزرسانی") == true
        )
    }

    @Test
    fun backupWithNothingRestorableIsRefused() {
        val empty = """{"formatVersion": 1, "portfolio": [], "fundCategories": []}"""

        assertTrue(PortfolioBackup.decode(empty).isFailure)
    }

    @Test
    fun rowsWithoutASymbolOrValueAreDropped() {
        val text = """
            {
              "formatVersion": 1,
              "portfolio": [
                {"symbol": "طلا", "category": "GOLD", "currentValue": 1000.0},
                {"symbol": "", "category": "GOLD", "currentValue": 500.0},
                {"symbol": "عیار", "category": "GOLD", "currentValue": 0.0}
              ]
            }
        """.trimIndent()

        val restored = PortfolioBackup.decode(text).getOrThrow()

        assertEquals(1, restored.portfolio.size)
        assertEquals("طلا", restored.portfolio.single().symbol)
    }

    @Test
    fun unknownCategoryNameFallsBackInsteadOfFailingTheWholeRestore() {
        val text = """
            {
              "formatVersion": 1,
              "portfolio": [
                {"symbol": "طلا", "category": "SOMETHING_NEW", "currentValue": 1000.0}
              ]
            }
        """.trimIndent()

        val restored = PortfolioBackup.decode(text).getOrThrow()

        assertEquals(AssetCategory.STOCK, restored.portfolio.single().assetCategory)
    }

    @Test
    fun unknownExtraFieldsAreIgnored_soOlderAppsCanReadNewerFiles() {
        val text = """
            {
              "formatVersion": 1,
              "somethingAddedLater": true,
              "portfolio": [
                {"symbol": "طلا", "category": "GOLD", "currentValue": 1000.0, "futureField": 5}
              ]
            }
        """.trimIndent()

        val restored = PortfolioBackup.decode(text)

        assertTrue(restored.isSuccess)
        assertEquals(1, restored.getOrThrow().portfolio.size)
    }

    @Test
    fun backupWithoutSettingsRestoresPortfolioOnly() {
        val text = PortfolioBackup.encode(null, portfolio, emptyList())
        val restored = PortfolioBackup.decode(text).getOrThrow()

        assertNull(restored.settings)
        assertEquals(2, restored.portfolio.size)
        assertTrue(restored.fundCategories.isEmpty())
    }
}
