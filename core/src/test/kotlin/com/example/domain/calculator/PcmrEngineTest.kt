package com.example.domain.calculator

import com.example.data.model.AssetCategory
import com.example.data.model.PortfolioEntity
import com.example.data.model.RebalanceAction
import com.example.data.model.SettingsEntity
import com.example.data.model.TimeHorizon
import com.example.data.model.TransactionType
import com.example.domain.alert.RebalanceAlertPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PcmrEngineTest {

    // ------------------------------------------------------------------
    // ۱. تطابق با اکسل دوره (031_Fuormula.xlsx) — مرجع نهایی فرمول‌ها
    // ------------------------------------------------------------------

    @Test
    fun targetWeights_matchesCourseExcel_autoSheet() {
        // شیت «چینش اتومات»: RT=10، EML G=33، EML S=22
        //   B8  =B2/B3*30        => 9.090909
        //   B9  =B2/B4*60        => 27.272727
        //   B10 =B2/(B4*1/3)*10  => 13.636364   ← نسبت مختلط به ۱/۳ تغییر کرده
        //   B11 =100-(B8+B9+B10) => 50.0
        //
        // اکسل دوره ۲/۳ داشت و مقدار B10 در آن ۶.۸۱۸ می‌شد. این تفاوت عمدی است.
        val settings = SettingsEntity(
            riskTolerance = 10.0,
            timeHorizonMonths = 6,
            emlGold6M = 33.0,
            emlStock6M = 22.0
        )

        val t = PcmrEngine.calculateTargetWeights(settings)
        assertEquals(9.1, t.goldWeight, 0.05)
        assertEquals(27.3, t.stockWeight, 0.05)
        assertEquals(13.6, t.mixedWeight, 0.05)
        assertEquals(50.0, t.fixedIncomeWeight, 0.05)
        assertEquals(100.0, t.totalWeight, 0.1)
    }

    @Test
    fun targetWeights_matchesCourseExcel_manualSheet_reverseRt() {
        // شیت «چینش دستی»: G=9.090909، S=35.294118، M=8.823529، EML G=27.47، EML S=12.91
        //   B11 =(B2/100*B9)+(B3/100*B10)+(B4/100*B10*1/3) => 7.433465
        //   (با نسبت ۲/۳ اکسل دوره عدد ۷.۸۱۳ می‌شد)
        val settings = SettingsEntity(
            timeHorizonMonths = 6,
            emlGold6M = 27.47,
            emlStock6M = 12.91,
            isManualWeightsEnabled = true,
            manualGoldWeight = 9.090909,
            manualStockWeight = 35.294118,
            manualMixedWeight = 8.823529,
            manualFixedWeight = 46.791444
        )

        val t = PcmrEngine.calculateTargetWeights(settings)
        assertEquals(7.4, t.calculatedRt, 0.05)
    }

    @Test
    fun emlMixed_isOneThirdOfStock() {
        // قفل کردن قاعده EML_M = ۱/۳ × EML_S
        val settings = SettingsEntity(timeHorizonMonths = 6, emlStock6M = 30.0)
        assertEquals(10.0, settings.currentEmlMixed(TimeHorizon.SIX_MONTHS), 0.001)
    }

    @Test
    fun mixedEmlRatioHasExactlyOneDefinition() {
        // این عدد قبلاً در صفحه تنظیمات هم هاردکد بود و از مدل جدا می‌افتاد.
        val settings = SettingsEntity(timeHorizonMonths = 6, emlStock6M = 45.0)
        assertEquals(
            SettingsEntity.MIXED_TO_STOCK_EML_RATIO * 45.0,
            settings.currentEmlMixed(TimeHorizon.SIX_MONTHS),
            0.001
        )
    }

    @Test
    fun halvingTheMixedEmlDoublesTheMixedTargetWeight() {
        // چرا این تغییر بی‌اثر نیست: EML_M در مخرج M = RT/EML_M × 10 است.
        val settings = SettingsEntity(
            riskTolerance = 12.0,
            timeHorizonMonths = 6,
            emlStock6M = 30.0,
            emlGold6M = 15.0
        )

        val t = PcmrEngine.calculateTargetWeights(settings)

        // با ۲/۳ عدد ۶٪ بود؛ با ۱/۳ می‌شود ۱۲٪
        assertEquals(12.0, t.mixedWeight, 0.01)
    }

    // ------------------------------------------------------------------
    // ۲. فرمول پایه وزن‌ها
    // ------------------------------------------------------------------

    @Test
    fun targetWeights_moderateRiskTolerance() {
        // RT=12، EML S=30، EML G=15، EML M=10  (مختلط = ۱/۳ سهامی)
        // G=(12/15)*30=24 ، S=(12/30)*60=24 ، M=(12/10)*10=12 ، F=40
        val settings = SettingsEntity(
            riskTolerance = 12.0,
            timeHorizonMonths = 6,
            emlStock6M = 30.0,
            emlGold6M = 15.0
        )

        val t = PcmrEngine.calculateTargetWeights(settings)
        assertEquals(24.0, t.goldWeight, 0.01)
        assertEquals(24.0, t.stockWeight, 0.01)
        assertEquals(12.0, t.mixedWeight, 0.01)
        assertEquals(40.0, t.fixedIncomeWeight, 0.01)
        assertFalse(t.isAggressive)
        assertFalse(t.isAdjustedDown)
        assertEquals(100.0, t.totalWeight, 0.01)
    }

    @Test
    fun targetWeights_aggressiveProfile_usesFrozenRatios() {
        val settings = SettingsEntity(
            riskTolerance = 30.0,
            timeHorizonMonths = 6,
            emlStock6M = 25.0,
            emlGold6M = 18.0
        )

        val t = PcmrEngine.calculateTargetWeights(settings)
        assertEquals(20.0, t.goldWeight, 0.01)
        assertEquals(70.0, t.stockWeight, 0.01)
        assertEquals(10.0, t.mixedWeight, 0.01)
        assertEquals(0.0, t.fixedIncomeWeight, 0.01)
        assertTrue(t.isAggressive)
        assertEquals(100.0, t.totalWeight, 0.01)
    }

    @Test
    fun aggressiveProfile_boundaryIsStrictlyAbove25() {
        // مستند: «RT بالای ۲۵» — پس در RT دقیقاً ۲۵ باید فرمول پایه اجرا شود
        val base = SettingsEntity(timeHorizonMonths = 6, emlStock6M = 25.0, emlGold6M = 18.0)

        val at25 = PcmrEngine.calculateTargetWeights(base.copy(riskTolerance = 25.0))
        assertFalse("در RT=۲۵ نباید نسبت فریزشده اعمال شود", at25.isAggressive)

        val above = PcmrEngine.calculateTargetWeights(base.copy(riskTolerance = 25.1))
        assertTrue(above.isAggressive)
        assertEquals(70.0, above.stockWeight, 0.01)
    }

    // ------------------------------------------------------------------
    // چینش دستی: تضمین مجموع = ۱۰۰٪
    // ------------------------------------------------------------------

    @Test
    fun manualWeights_areNormalizedWhenTheyDoNotSumTo100() {
        // کاربر ۱۱۰٪ وارد کرده؛ UI فقط هشدار می‌دهد و جلوی ذخیره را نمی‌گیرد
        val settings = SettingsEntity(
            timeHorizonMonths = 6,
            emlStock6M = 30.0,
            emlGold6M = 15.0,
            isManualWeightsEnabled = true,
            manualGoldWeight = 30.0,
            manualStockWeight = 40.0,
            manualMixedWeight = 20.0,
            manualFixedWeight = 20.0
        )

        val t = PcmrEngine.calculateTargetWeights(settings)

        assertEquals(100.0, t.totalWeight, 0.1)
        assertTrue("نرمال‌سازی باید علامت بخورد", t.isAdjustedDown)
        assertEquals(27.3, t.goldWeight, 0.15)   // 30/110
        assertEquals(36.4, t.stockWeight, 0.15)  // 40/110
    }

    @Test
    fun manualWeights_summingUnder100_areScaledUp() {
        val settings = SettingsEntity(
            timeHorizonMonths = 6,
            isManualWeightsEnabled = true,
            manualGoldWeight = 10.0,
            manualStockWeight = 20.0,
            manualMixedWeight = 10.0,
            manualFixedWeight = 10.0
        )

        val t = PcmrEngine.calculateTargetWeights(settings)
        assertEquals(100.0, t.totalWeight, 0.1)
        assertTrue(t.isAdjustedDown)
    }

    @Test
    fun manualWeights_exactly100_areLeftAlone() {
        val settings = SettingsEntity(
            timeHorizonMonths = 6,
            isManualWeightsEnabled = true,
            manualGoldWeight = 25.0,
            manualStockWeight = 35.0,
            manualMixedWeight = 15.0,
            manualFixedWeight = 25.0
        )

        val t = PcmrEngine.calculateTargetWeights(settings)
        assertEquals(25.0, t.goldWeight, 0.01)
        assertEquals(35.0, t.stockWeight, 0.01)
        assertEquals(15.0, t.mixedWeight, 0.01)
        assertEquals(25.0, t.fixedIncomeWeight, 0.01)
        assertFalse("جمع درست بوده، نباید چیزی تغییر کند", t.isAdjustedDown)
    }

    @Test
    fun manualWeights_allZero_fallsBackToAutomaticFormula() {
        val settings = SettingsEntity(
            riskTolerance = 12.0,
            timeHorizonMonths = 6,
            emlStock6M = 30.0,
            emlGold6M = 15.0,
            isManualWeightsEnabled = true,
            manualGoldWeight = 0.0,
            manualStockWeight = 0.0,
            manualMixedWeight = 0.0,
            manualFixedWeight = 0.0
        )

        val t = PcmrEngine.calculateTargetWeights(settings)
        assertEquals(24.0, t.goldWeight, 0.01)
        assertEquals(24.0, t.stockWeight, 0.01)
        assertEquals(100.0, t.totalWeight, 0.01)
    }

    @Test
    fun depositMathStaysCorrect_evenWithBadManualWeights() {
        // رگرسیون: وزن‌های نرمال‌نشده ماشین‌حساب واریز را می‌شکستند
        val settings = SettingsEntity(
            rebalanceThresholdPercent = 5.0,
            timeHorizonMonths = 6,
            isManualWeightsEnabled = true,
            manualGoldWeight = 60.0,
            manualStockWeight = 60.0,
            manualMixedWeight = 30.0,
            manualFixedWeight = 30.0   // جمع = ۱۸۰٪
        )
        val items = listOf(
            PortfolioEntity(symbol = "طلا", assetCategory = AssetCategory.GOLD, currentValue = 50_000_000.0),
            PortfolioEntity(symbol = "اهرم", assetCategory = AssetCategory.STOCK, currentValue = 50_000_000.0)
        )

        val portfolio = PcmrEngine.calculatePortfolio(items, settings)
        assertEquals(0.0, portfolio.unallocatedWeightPercent, 0.1)

        val deposit = 20_000_000.0
        val res = PcmrEngine.calculateDepositWithdrawal(portfolio, TransactionType.DEPOSIT, deposit)
        assertEquals(deposit, res.orders.sumOf { it.orderAmount }, 1.0)
        assertEquals(0.0, res.postTrIndex, 0.05)
    }

    @Test
    fun targetWeights_scalesDownWhenRiskyAssetsExceed100() {
        // اکسل اجازه می‌دهد F منفی شود؛ موتور سه دارایی ریسکی را متناسب کوچک می‌کند.
        val settings = SettingsEntity(
            riskTolerance = 24.0,
            timeHorizonMonths = 6,
            emlStock6M = 15.0,
            emlGold6M = 10.0
        )

        val t = PcmrEngine.calculateTargetWeights(settings)
        assertTrue(t.isAdjustedDown)
        assertEquals(0.0, t.fixedIncomeWeight, 0.01)
        assertEquals(100.0, t.totalWeight, 0.5)
    }

    // ------------------------------------------------------------------
    // ۳. سیگنال‌های بازتعادل
    // ------------------------------------------------------------------

    @Test
    fun rebalanceSignals_buySellHold() {
        val settings = SettingsEntity(
            riskTolerance = 12.0,
            rebalanceThresholdPercent = 5.0,
            timeHorizonMonths = 6,
            emlStock6M = 30.0,
            emlGold6M = 15.0
        )
        // هدف: طلا ۲۴٪، سهامی ۲۴٪، مختلط ۱۲٪، ثابت ۴۰٪ — PV = 100M
        val items = listOf(
            PortfolioEntity(symbol = "طلا", assetCategory = AssetCategory.GOLD, currentValue = 60_000_000.0, lastPrice = 10_000.0),
            PortfolioEntity(symbol = "اهرم", assetCategory = AssetCategory.STOCK, currentValue = 10_000_000.0, lastPrice = 20_000.0),
            PortfolioEntity(symbol = "زیتون", assetCategory = AssetCategory.MIXED, currentValue = 6_000_000.0, lastPrice = 15_000.0),
            PortfolioEntity(symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME, currentValue = 24_000_000.0, lastPrice = 10_000.0)
        )

        val r = PcmrEngine.calculatePortfolio(items, settings)
        assertEquals(100_000_000.0, r.totalPv, 0.01)
        assertTrue(r.isRebalanceTriggered)

        assertEquals(RebalanceAction.SELL, r.symbolAlerts.first { it.symbol == "طلا" }.action)
        assertEquals(RebalanceAction.BUY, r.symbolAlerts.first { it.symbol == "اهرم" }.action)
        // مختلط با نسبت ۱/۳ حالا ۱۲٪ هدف دارد و ۶M کم دارد، پس دیگر HOLD نیست
        assertEquals(RebalanceAction.BUY, r.symbolAlerts.first { it.symbol == "زیتون" }.action)
        assertEquals(RebalanceAction.BUY, r.symbolAlerts.first { it.symbol == "کمند" }.action)

        // Delta-V = Vr − Va
        assertEquals(-36_000_000.0, r.symbolAlerts.first { it.symbol == "طلا" }.deltaValue, 100.0)
        assertEquals(14_000_000.0, r.symbolAlerts.first { it.symbol == "اهرم" }.deltaValue, 100.0)

        // Tr = Σ|Va − Vr| / PV × 100 = (36+14+6+16)/100 = 72٪
        assertEquals(72.0, r.trIndex, 0.2)
        assertEquals(0.0, r.unallocatedWeightPercent, 0.05)
    }

    // ------------------------------------------------------------------
    // ۴. رگرسیون: طبقه‌ای که نماد ندارد (باگ وزن گم‌شده)
    // ------------------------------------------------------------------

    private fun twoAssetSettings() = SettingsEntity(
        riskTolerance = 12.0,
        rebalanceThresholdPercent = 5.0,
        timeHorizonMonths = 6,
        emlStock6M = 30.0,
        emlGold6M = 15.0
    )

    /** سبدی که فقط طلا و سهامی دارد؛ مختلط (۱۲٪) و ثابت (۴۰٪) نماد ندارند. */
    private fun twoAssetPortfolio() = listOf(
        PortfolioEntity(symbol = "طلا", assetCategory = AssetCategory.GOLD, currentValue = 50_000_000.0),
        PortfolioEntity(symbol = "اهرم", assetCategory = AssetCategory.STOCK, currentValue = 50_000_000.0)
    )

    @Test
    fun missingCategories_produceSuggestionRows_andWeightsStillSumTo100() {
        val r = PcmrEngine.calculatePortfolio(twoAssetPortfolio(), twoAssetSettings())

        assertEquals(0.0, r.unallocatedWeightPercent, 0.05)
        assertEquals(100.0, r.symbolAlerts.sumOf { it.targetWeightPercent }, 0.1)

        val suggestions = r.symbolAlerts.filter { it.isSuggestedNewPosition }
        assertEquals(2, suggestions.size)

        val mixed = suggestions.first { it.category == AssetCategory.MIXED }
        assertEquals(0.0, mixed.currentValue, 0.01)
        assertEquals(12_000_000.0, mixed.targetValue, 1.0)  // 100M × ۱۲٪
        assertEquals(RebalanceAction.BUY, mixed.action)
        assertNotNull(mixed.minOrderNotice)

        val fixed = suggestions.first { it.category == AssetCategory.FIXED_INCOME }
        assertEquals(40_000_000.0, fixed.targetValue, 1.0)  // 100M × ۴۰٪
        assertEquals(RebalanceAction.BUY, fixed.action)

        // مجموع Delta-V روی کل سبد باید صفر باشد (خرید = فروش)
        assertEquals(0.0, r.symbolAlerts.sumOf { it.deltaValue }, 1.0)
    }

    @Test
    fun deposit_ordersSumToDepositAmount() {
        // رگرسیون باگ گزارش‌شده: واریز ۲۰M قبلاً ۴۲.۴M سفارشِ فروش تولید می‌کرد.
        val portfolio = PcmrEngine.calculatePortfolio(twoAssetPortfolio(), twoAssetSettings())
        val deposit = 20_000_000.0

        val res = PcmrEngine.calculateDepositWithdrawal(portfolio, TransactionType.DEPOSIT, deposit)

        assertEquals(deposit, res.orders.sumOf { it.orderAmount }, 1.0)
        assertEquals(120_000_000.0, res.newPv, 0.01)
        assertEquals(120_000_000.0, res.orders.sumOf { it.postTransactionValue }, 1.0)
        assertFalse(res.isAmountClamped)
    }

    @Test
    fun withdrawal_ordersSumToNegativeWithdrawalAmount() {
        val portfolio = PcmrEngine.calculatePortfolio(twoAssetPortfolio(), twoAssetSettings())
        val withdrawal = 30_000_000.0

        val res = PcmrEngine.calculateDepositWithdrawal(portfolio, TransactionType.WITHDRAWAL, withdrawal)

        assertEquals(-withdrawal, res.orders.sumOf { it.orderAmount }, 1.0)
        assertEquals(70_000_000.0, res.newPv, 0.01)
        assertEquals(70_000_000.0, res.orders.sumOf { it.postTransactionValue }, 1.0)
    }

    @Test
    fun postTrIndex_isZeroAfterTransaction() {
        val portfolio = PcmrEngine.calculatePortfolio(twoAssetPortfolio(), twoAssetSettings())

        val dep = PcmrEngine.calculateDepositWithdrawal(portfolio, TransactionType.DEPOSIT, 20_000_000.0)
        assertEquals(0.0, dep.postTrIndex, 0.05)

        val wit = PcmrEngine.calculateDepositWithdrawal(portfolio, TransactionType.WITHDRAWAL, 30_000_000.0)
        assertEquals(0.0, wit.postTrIndex, 0.05)

        // هر ردیف باید دقیقاً روی وزن هدفش بنشیند
        for (o in dep.orders) {
            assertEquals(dep.newPv * (o.targetWeight / 100.0), o.postTransactionValue, 1.0)
        }
    }

    @Test
    fun postTransactionValues_areNeverNegative() {
        val portfolio = PcmrEngine.calculatePortfolio(twoAssetPortfolio(), twoAssetSettings())
        val res = PcmrEngine.calculateDepositWithdrawal(portfolio, TransactionType.WITHDRAWAL, 99_000_000.0)
        for (o in res.orders) {
            assertTrue("ارزش منفی برای ${o.symbol}", o.postTransactionValue >= -1.0)
        }
    }

    // ------------------------------------------------------------------
    // ۵. اعتبارسنجی برداشت
    // ------------------------------------------------------------------

    @Test
    fun withdrawalLargerThanPortfolio_isClampedToPv() {
        val portfolio = PcmrEngine.calculatePortfolio(twoAssetPortfolio(), twoAssetSettings())

        val res = PcmrEngine.calculateDepositWithdrawal(portfolio, TransactionType.WITHDRAWAL, 500_000_000.0)

        assertTrue(res.isAmountClamped)
        assertEquals(500_000_000.0, res.inputAmount, 0.01)
        assertEquals(100_000_000.0, res.effectiveAmount, 0.01)
        assertEquals(0.0, res.newPv, 0.01)
        assertEquals(-100_000_000.0, res.orders.sumOf { it.orderAmount }, 1.0)
    }

    // ------------------------------------------------------------------
    // ۶. کف اقتصادی ۱۰ میلیون تومان (بخش ۴ مستند)
    // ------------------------------------------------------------------

    @Test
    fun suggestionBelowEconomicFloor_isFlagged() {
        // PV = ۵۰۰M ریال (۵۰ میلیون تومان)
        //   مختلط  ۱۲٪ =  ۶۰M ریال =  ۶ میلیون تومان -> زیر کف ۱۰ میلیون تومان
        //   ثابت   ۴۰٪ = ۲۰۰M ریال = ۲۰ میلیون تومان -> بالای کف
        val items = listOf(
            PortfolioEntity(symbol = "طلا", assetCategory = AssetCategory.GOLD, currentValue = 250_000_000.0),
            PortfolioEntity(symbol = "اهرم", assetCategory = AssetCategory.STOCK, currentValue = 250_000_000.0)
        )
        val r = PcmrEngine.calculatePortfolio(items, twoAssetSettings())

        val mixed = r.symbolAlerts.first { it.category == AssetCategory.MIXED && it.isSuggestedNewPosition }
        assertTrue(mixed.targetValue < PcmrEngine.MIN_ECONOMIC_POSITION_RIAL)
        assertTrue(mixed.minOrderNotice!!.contains("کف اقتصادی"))

        val fixed = r.symbolAlerts.first { it.category == AssetCategory.FIXED_INCOME && it.isSuggestedNewPosition }
        assertTrue(fixed.targetValue >= PcmrEngine.MIN_ECONOMIC_POSITION_RIAL)
        assertTrue(fixed.minOrderNotice!!.contains("انتخاب کنید"))
    }

    // ------------------------------------------------------------------
    // ۷. وزن دستی نماد نباید وزن طبقه را نشت دهد
    // ------------------------------------------------------------------

    @Test
    fun manualTargetPercent_doesNotLeakCategoryWeight() {
        val items = listOf(
            PortfolioEntity(symbol = "اهرم", assetCategory = AssetCategory.STOCK, currentValue = 50_000_000.0, manualTargetPercent = 10.0),
            PortfolioEntity(symbol = "آگاس", assetCategory = AssetCategory.STOCK, currentValue = 50_000_000.0)
        )
        val r = PcmrEngine.calculatePortfolio(items, twoAssetSettings())

        val stockWeight = r.symbolAlerts.filter { it.category == AssetCategory.STOCK }.sumOf { it.targetWeightPercent }
        assertEquals(24.0, stockWeight, 0.1)   // وزن کل طبقه سهامی
        assertEquals(10.0, r.symbolAlerts.first { it.symbol == "اهرم" }.targetWeightPercent, 0.1)
        assertEquals(14.0, r.symbolAlerts.first { it.symbol == "آگاس" }.targetWeightPercent, 0.1)
        assertEquals(0.0, r.unallocatedWeightPercent, 0.05)
    }

    // ------------------------------------------------------------------
    // ۸. حالت‌های مرزی
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // شناسه پایدار ردیف‌ها (کلید LazyColumn)
    // ------------------------------------------------------------------

    @Test
    fun itemIds_areUnique_acrossRealRowsAndSuggestions() {
        // کلید تکراری در LazyColumn باعث کرش می‌شود، پس یکتا بودن باید تضمین شود
        val items = listOf(
            PortfolioEntity(id = 1L, symbol = "طلا", assetCategory = AssetCategory.GOLD, currentValue = 50_000_000.0),
            PortfolioEntity(id = 2L, symbol = "عیار", assetCategory = AssetCategory.GOLD, currentValue = 30_000_000.0),
            PortfolioEntity(id = 3L, symbol = "اهرم", assetCategory = AssetCategory.STOCK, currentValue = 50_000_000.0)
        )
        val r = PcmrEngine.calculatePortfolio(items, twoAssetSettings())

        val ids = r.symbolAlerts.map { it.itemId }
        assertEquals("شناسه‌ها باید یکتا باشند", ids.size, ids.distinct().size)

        // ردیف‌های واقعی شناسه دیتابیس را نگه می‌دارند
        assertEquals(1L, r.symbolAlerts.first { it.symbol == "طلا" }.itemId)
        assertEquals(3L, r.symbolAlerts.first { it.symbol == "اهرم" }.itemId)

        // ردیف‌های پیشنهادی شناسه منفی می‌گیرند
        assertTrue(r.symbolAlerts.filter { it.isSuggestedNewPosition }.all { it.itemId < 0 })

        // سفارش‌های ماشین‌حساب هم همان شناسه‌ها را حمل می‌کنند
        val orders = PcmrEngine.calculateDepositWithdrawal(r, TransactionType.DEPOSIT, 10_000_000.0).orders
        val orderIds = orders.map { it.itemId }
        assertEquals(orderIds.size, orderIds.distinct().size)
        assertEquals(ids.toSet(), orderIds.toSet())
    }

    @Test
    fun itemIds_areUnique_whenAllCategoriesAreEmpty() {
        // فقط یک طبقه پر است -> سه ردیف پیشنهادی، هر کدام باید شناسه متفاوت بگیرد
        val items = listOf(
            PortfolioEntity(id = 7L, symbol = "طلا", assetCategory = AssetCategory.GOLD, currentValue = 100_000_000.0)
        )
        val r = PcmrEngine.calculatePortfolio(items, twoAssetSettings())
        val ids = r.symbolAlerts.map { it.itemId }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(3, r.symbolAlerts.count { it.isSuggestedNewPosition })
    }

    @Test
    fun edgeCases_emptyAndZeroPortfolio() {
        val settings = SettingsEntity()

        val empty = PcmrEngine.calculatePortfolio(emptyList(), settings)
        assertEquals(0.0, empty.totalPv, 0.01)
        assertEquals(0.0, empty.trIndex, 0.01)
        assertFalse(empty.isRebalanceTriggered)
        assertTrue(empty.symbolAlerts.isEmpty())

        val zeroItems = listOf(
            PortfolioEntity(symbol = "طلا", assetCategory = AssetCategory.GOLD, currentValue = 0.0),
            PortfolioEntity(symbol = "اهرم", assetCategory = AssetCategory.STOCK, currentValue = 0.0)
        )
        val zero = PcmrEngine.calculatePortfolio(zeroItems, settings)
        assertEquals(0.0, zero.totalPv, 0.01)
        assertTrue(zero.symbolAlerts.isEmpty())

        val depositOnEmpty = PcmrEngine.calculateDepositWithdrawal(empty, TransactionType.DEPOSIT, 10_000_000.0)
        assertEquals(10_000_000.0, depositOnEmpty.newPv, 0.01)
        assertTrue(depositOnEmpty.orders.isEmpty())

        val negative = PcmrEngine.calculateDepositWithdrawal(empty, TransactionType.DEPOSIT, -5_000.0)
        assertEquals(0.0, negative.effectiveAmount, 0.01)
    }

    @Test
    fun singleCategoryPortfolio_stillBalancesToFullWeight() {
        // فقط طلا در سبد — سه طبقه دیگر باید ردیف پیشنهاد بگیرند
        val items = listOf(
            PortfolioEntity(symbol = "طلا", assetCategory = AssetCategory.GOLD, currentValue = 200_000_000.0)
        )
        val r = PcmrEngine.calculatePortfolio(items, twoAssetSettings())

        assertEquals(100.0, r.symbolAlerts.sumOf { it.targetWeightPercent }, 0.1)
        assertEquals(3, r.symbolAlerts.count { it.isSuggestedNewPosition })

        val res = PcmrEngine.calculateDepositWithdrawal(r, TransactionType.DEPOSIT, 50_000_000.0)
        assertEquals(50_000_000.0, res.orders.sumOf { it.orderAmount }, 1.0)
        assertEquals(0.0, res.postTrIndex, 0.05)
    }

    @Test
    fun balancedPortfolio_hasZeroTrAndAllHold() {
        // سبدی که دقیقاً روی وزن هدف نشسته: ۲۴/۲۴/۱۲/۴۰
        val items = listOf(
            PortfolioEntity(symbol = "طلا", assetCategory = AssetCategory.GOLD, currentValue = 24_000_000.0),
            PortfolioEntity(symbol = "اهرم", assetCategory = AssetCategory.STOCK, currentValue = 24_000_000.0),
            PortfolioEntity(symbol = "زیتون", assetCategory = AssetCategory.MIXED, currentValue = 12_000_000.0),
            PortfolioEntity(symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME, currentValue = 40_000_000.0)
        )
        val r = PcmrEngine.calculatePortfolio(items, twoAssetSettings())

        assertEquals(0.0, r.trIndex, 0.05)
        assertFalse(r.isRebalanceTriggered)
        assertTrue(r.symbolAlerts.all { it.action == RebalanceAction.HOLD })
        assertTrue(r.symbolAlerts.none { it.isSuggestedNewPosition })
    }

    @Test
    fun buyAndSellTotals_areEqualWhenWeightsSumTo100() {
        val r = PcmrEngine.calculatePortfolio(twoAssetPortfolio(), twoAssetSettings())
        assertTrue(abs(r.totalBuyAmount - r.totalSellAmount) < 1.0)
        assertEquals(r.balancedExecutionAmount, minOf(r.totalBuyAmount, r.totalSellAmount), 0.01)
    }

    // ------------------------------------------------------------------
    // درصد هدف اختصاصی نماد نباید ضمانت «جمع = ۱۰۰٪» را بشکند
    // ------------------------------------------------------------------

    @Test
    fun manualTargetPercents_areScaledDownWhenTheyExceedTheirCategoryWeight() {
        // طبقه طلا وزن ۲۰٪ می‌گیرد ولی کاربر دو نماد ۲۵٪ و ۱۵٪ داده (جمع ۴۰٪).
        val settings = SettingsEntity(
            isManualWeightsEnabled = true,
            manualGoldWeight = 20.0,
            manualStockWeight = 40.0,
            manualMixedWeight = 0.0,
            manualFixedWeight = 40.0
        )
        val items = listOf(
            PortfolioEntity(
                id = 1, symbol = "طلا", assetCategory = AssetCategory.GOLD,
                currentValue = 100_000_000.0, manualTargetPercent = 25.0
            ),
            PortfolioEntity(
                id = 2, symbol = "عیار", assetCategory = AssetCategory.GOLD,
                currentValue = 100_000_000.0, manualTargetPercent = 15.0
            ),
            PortfolioEntity(
                id = 3, symbol = "اهرم", assetCategory = AssetCategory.STOCK,
                currentValue = 200_000_000.0
            ),
            PortfolioEntity(
                id = 4, symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME,
                currentValue = 200_000_000.0
            )
        )

        val result = PcmrEngine.calculatePortfolio(items, settings)

        // ۲۵ و ۱۵ به نسبت کوچک می‌شوند: ۱۲.۵٪ و ۷.۵٪ — جمعشان دقیقاً وزن طبقه
        val gold = result.symbolAlerts.filter { it.category == AssetCategory.GOLD }
        assertEquals(20.0, gold.sumOf { it.targetWeightPercent }, 0.15)

        // و مهم‌تر: هیچ وزنی تخصیص‌نیافته یا اضافه‌تخصیص‌یافته نمانده
        assertEquals(0.0, result.unallocatedWeightPercent, 0.15)
    }

    @Test
    fun manualTargetPercents_areLeftAloneWhenTheyFitInsideTheCategoryWeight() {
        val settings = SettingsEntity(
            isManualWeightsEnabled = true,
            manualGoldWeight = 40.0,
            manualStockWeight = 30.0,
            manualMixedWeight = 0.0,
            manualFixedWeight = 30.0
        )
        val items = listOf(
            PortfolioEntity(
                id = 1, symbol = "طلا", assetCategory = AssetCategory.GOLD,
                currentValue = 100_000_000.0, manualTargetPercent = 25.0
            ),
            PortfolioEntity(
                id = 2, symbol = "عیار", assetCategory = AssetCategory.GOLD,
                currentValue = 100_000_000.0
            ),
            PortfolioEntity(
                id = 3, symbol = "اهرم", assetCategory = AssetCategory.STOCK,
                currentValue = 200_000_000.0
            ),
            PortfolioEntity(
                id = 4, symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME,
                currentValue = 200_000_000.0
            )
        )

        val result = PcmrEngine.calculatePortfolio(items, settings)

        val gold = result.symbolAlerts.first { it.symbol == "طلا" }
        assertEquals(25.0, gold.targetWeightPercent, 0.05)
        // باقیمانده طبقه (۴۰ − ۲۵ = ۱۵٪) به نماد دوم می‌رسد
        val ayar = result.symbolAlerts.first { it.symbol == "عیار" }
        assertEquals(15.0, ayar.targetWeightPercent, 0.05)
        assertEquals(0.0, result.unallocatedWeightPercent, 0.15)
    }

    @Test
    fun negativeManualTargetPercent_isTreatedAsZero() {
        val settings = SettingsEntity(
            isManualWeightsEnabled = true,
            manualGoldWeight = 20.0,
            manualStockWeight = 40.0,
            manualMixedWeight = 0.0,
            manualFixedWeight = 40.0
        )
        val items = listOf(
            PortfolioEntity(
                id = 1, symbol = "طلا", assetCategory = AssetCategory.GOLD,
                currentValue = 100_000_000.0, manualTargetPercent = -10.0
            ),
            PortfolioEntity(
                id = 2, symbol = "عیار", assetCategory = AssetCategory.GOLD,
                currentValue = 100_000_000.0
            ),
            PortfolioEntity(
                id = 3, symbol = "اهرم", assetCategory = AssetCategory.STOCK,
                currentValue = 200_000_000.0
            ),
            PortfolioEntity(
                id = 4, symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME,
                currentValue = 200_000_000.0
            )
        )

        val result = PcmrEngine.calculatePortfolio(items, settings)

        val tala = result.symbolAlerts.first { it.symbol == "طلا" }
        assertEquals(0.0, tala.targetWeightPercent, 0.05)
        assertEquals(0.0, result.unallocatedWeightPercent, 0.15)
    }

    // ------------------------------------------------------------------
    // ماشین‌حساب باید تعداد واحد و کف سفارش را بدهد — سفارش در کارگزاری
    // بر حسب واحد ثبت می‌شود، نه مبلغ.
    // ------------------------------------------------------------------

    private fun twoSymbolPortfolio() = listOf(
        PortfolioEntity(
            id = 1, symbol = "اهرم", assetCategory = AssetCategory.STOCK,
            currentValue = 500_000_000.0, quantity = 200_000L, lastPrice = 2_500.0
        ),
        PortfolioEntity(
            id = 2, symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME,
            currentValue = 500_000_000.0, quantity = 50_000L, lastPrice = 10_000.0
        )
    )

    @Test
    fun depositOrders_reportEstimatedUnitsFromLastPrice() {
        val settings = SettingsEntity(
            isManualWeightsEnabled = true,
            manualGoldWeight = 0.0,
            manualStockWeight = 50.0,
            manualMixedWeight = 0.0,
            manualFixedWeight = 50.0
        )
        val calc = PcmrEngine.calculatePortfolio(twoSymbolPortfolio(), settings)

        val result = PcmrEngine.calculateDepositWithdrawal(
            portfolioResult = calc,
            type = TransactionType.DEPOSIT,
            transactionAmount = 100_000_000.0
        )

        val leverage = result.orders.first { it.symbol == "اهرم" }
        // سبد متوازن است، پس هر نماد نصف واریز را می‌گیرد: ۵۰ میلیون ریال
        assertEquals(50_000_000.0, leverage.absoluteAmount, 1_000.0)
        // ۵۰ میلیون ریال ÷ ۲۵۰۰ ریال = ۲۰٬۰۰۰ واحد
        assertEquals(20_000L, leverage.estimatedUnits)

        val fixed = result.orders.first { it.symbol == "کمند" }
        assertEquals(5_000L, fixed.estimatedUnits)
    }

    @Test
    fun ordersWithoutAKnownPrice_reportZeroUnitsRatherThanAGuess() {
        val items = listOf(
            PortfolioEntity(
                id = 1, symbol = "اهرم", assetCategory = AssetCategory.STOCK,
                currentValue = 500_000_000.0, quantity = 0L, lastPrice = 0.0
            ),
            PortfolioEntity(
                id = 2, symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME,
                currentValue = 500_000_000.0, quantity = 50_000L, lastPrice = 10_000.0
            )
        )
        val settings = SettingsEntity(
            isManualWeightsEnabled = true,
            manualGoldWeight = 0.0, manualStockWeight = 50.0,
            manualMixedWeight = 0.0, manualFixedWeight = 50.0
        )
        val calc = PcmrEngine.calculatePortfolio(items, settings)

        val result = PcmrEngine.calculateDepositWithdrawal(
            calc, TransactionType.DEPOSIT, 100_000_000.0
        )

        assertEquals(0L, result.orders.first { it.symbol == "اهرم" }.estimatedUnits)
    }

    @Test
    fun tinyDepositOrders_carryTheExchangeMinimumNotice() {
        val settings = SettingsEntity(
            isManualWeightsEnabled = true,
            manualGoldWeight = 0.0, manualStockWeight = 50.0,
            manualMixedWeight = 0.0, manualFixedWeight = 50.0
        )
        val calc = PcmrEngine.calculatePortfolio(twoSymbolPortfolio(), settings)

        // واریز ۲۰ هزار تومان: سهم هر نماد ۱۰ هزار تومان، زیر کف خرید بورس
        val result = PcmrEngine.calculateDepositWithdrawal(
            calc, TransactionType.DEPOSIT, 200_000.0
        )

        val buys = result.orders.filter { it.action == RebalanceAction.BUY }
        assertTrue("باید حداقل یک سفارش خرید باشد", buys.isNotEmpty())
        assertTrue(
            "سفارش زیر کف باید هشدار داشته باشد",
            buys.all { it.minOrderNotice != null }
        )
    }

    @Test
    fun comfortablySizedOrders_haveNoMinimumNotice() {
        val settings = SettingsEntity(
            isManualWeightsEnabled = true,
            manualGoldWeight = 0.0, manualStockWeight = 50.0,
            manualMixedWeight = 0.0, manualFixedWeight = 50.0
        )
        val calc = PcmrEngine.calculatePortfolio(twoSymbolPortfolio(), settings)

        val result = PcmrEngine.calculateDepositWithdrawal(
            calc, TransactionType.DEPOSIT, 500_000_000.0
        )

        assertTrue(result.orders.filter { it.action != RebalanceAction.HOLD }
            .all { it.minOrderNotice == null })
    }

    @Test
    fun alertsCarryLastPrice_soDownstreamCanSizeOrders() {
        val settings = SettingsEntity(
            isManualWeightsEnabled = true,
            manualGoldWeight = 0.0, manualStockWeight = 50.0,
            manualMixedWeight = 0.0, manualFixedWeight = 50.0
        )
        val calc = PcmrEngine.calculatePortfolio(twoSymbolPortfolio(), settings)

        assertEquals(2_500.0, calc.symbolAlerts.first { it.symbol == "اهرم" }.lastPrice, 0.001)
        assertEquals(10_000.0, calc.symbolAlerts.first { it.symbol == "کمند" }.lastPrice, 0.001)
    }

    // ------------------------------------------------------------------
    // قفل نگه‌داری (isHoldLocked)
    // ------------------------------------------------------------------

    /**
     * سبد پرتکرارِ این بخش: طلا اضافه‌وزن است و بقیه کم‌وزن.
     * وزن‌های هدف دستی‌اند تا تست به فرمول RT/EML گره نخورد.
     */
    private fun goldHeavyPortfolio(goldLocked: Boolean) = listOf(
        PortfolioEntity(
            id = 1, symbol = "طلا", assetCategory = AssetCategory.GOLD,
            currentValue = 500_000_000.0, quantity = 100_000L, lastPrice = 5_000.0,
            isHoldLocked = goldLocked
        ),
        PortfolioEntity(
            id = 2, symbol = "اهرم", assetCategory = AssetCategory.STOCK,
            currentValue = 300_000_000.0, quantity = 120_000L, lastPrice = 2_500.0
        ),
        PortfolioEntity(
            id = 3, symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME,
            currentValue = 200_000_000.0, quantity = 20_000L, lastPrice = 10_000.0
        )
    )

    /** طلا ۲۰٪، سهامی ۴۰٪، درآمد ثابت ۴۰٪ — سبد بالا یعنی طلا ۵۰٪ و اضافه‌وزن. */
    private fun lockSettings() = SettingsEntity(
        isManualWeightsEnabled = true,
        manualGoldWeight = 20.0, manualStockWeight = 40.0,
        manualMixedWeight = 0.0, manualFixedWeight = 40.0
    )

    @Test
    fun withoutLock_overweightGoldGetsSellSignal() {
        val calc = PcmrEngine.calculatePortfolio(goldHeavyPortfolio(goldLocked = false), lockSettings())
        val gold = calc.symbolAlerts.first { it.symbol == "طلا" }

        assertEquals(RebalanceAction.SELL, gold.action)
        // ۵۰۰ میلیون فعلی در برابر ۲۰٪ از یک میلیارد = ۲۰۰ میلیون
        assertEquals(300_000_000.0, gold.actionAmount, 1_000.0)
        assertNull(calc.lockedHold)
    }

    @Test
    fun lock_turnsSellSignalIntoHold_andKeepsItOutOfTotalSells() {
        val calc = PcmrEngine.calculatePortfolio(goldHeavyPortfolio(goldLocked = true), lockSettings())
        val gold = calc.symbolAlerts.first { it.symbol == "طلا" }

        assertEquals(RebalanceAction.HOLD, gold.action)
        assertTrue(gold.isHoldLocked)
        assertEquals(300_000_000.0, gold.suppressedSellAmount, 1_000.0)
        // هیچ فروشی در کل سبد پیشنهاد نمی‌شود، چون تنها ردیف مازاد قفل است
        assertEquals(0.0, calc.totalSellAmount, 1.0)
        assertTrue(calc.symbolAlerts.none { it.action == RebalanceAction.SELL })
    }

    @Test
    fun lock_doesNotHideRealDeviationFromTrIndex() {
        val free = PcmrEngine.calculatePortfolio(goldHeavyPortfolio(goldLocked = false), lockSettings())
        val locked = PcmrEngine.calculatePortfolio(goldHeavyPortfolio(goldLocked = true), lockSettings())

        // قفل تصمیمِ کاربر است، نه اینکه سبد متعادل شده باشد؛ Tr باید یکی بماند.
        assertEquals(free.trIndex, locked.trIndex, 0.001)
        assertTrue(locked.isRebalanceTriggered)
    }

    @Test
    fun lock_reportsCashNeededToReachTargetWithoutSelling() {
        val calc = PcmrEngine.calculatePortfolio(goldHeavyPortfolio(goldLocked = true), lockSettings())
        val summary = calc.lockedHold

        assertNotNull(summary)
        requireNotNull(summary)
        assertEquals(listOf("طلا"), summary.bindingSymbols)
        assertFalse(summary.isUnreachableByCash)
        // طلا ۵۰۰ میلیون است و باید ۲۰٪ سبد باشد ⇒ کل سبد باید ۲.۵ میلیارد شود
        assertEquals(2_500_000_000.0, summary.targetPvRial, 1_000.0)
        assertEquals(1_500_000_000.0, summary.requiredCashRial, 1_000.0)
    }

    @Test
    fun lockedCashPlan_spendsEveryRialOnTheOtherFunds_andLeavesGoldAlone() {
        val calc = PcmrEngine.calculatePortfolio(goldHeavyPortfolio(goldLocked = true), lockSettings())
        val plan = PcmrEngine.lockedHoldCashPlan(calc)

        assertNotNull(plan)
        requireNotNull(plan)
        assertEquals(2_500_000_000.0, plan.newPv, 1_000.0)

        // نماد قفل‌شده دقیقاً سر جایش می‌ماند: نه خرید، نه فروش.
        val gold = plan.orders.first { it.symbol == "طلا" }
        assertEquals(RebalanceAction.HOLD, gold.action)
        assertEquals(0.0, gold.orderAmount, 1_000.0)

        // بقیه فقط خرید می‌گیرند و جمعشان همان پول نقد ورودی است.
        val others = plan.orders.filter { it.symbol != "طلا" }
        assertTrue(others.all { it.action == RebalanceAction.BUY })
        assertEquals(1_500_000_000.0, others.sumOf { it.orderAmount }, 2_000.0)

        // ۴۰٪ از ۲.۵ میلیارد = ۱ میلیارد برای هرکدام
        assertEquals(1_000_000_000.0, plan.orders.first { it.symbol == "اهرم" }.postTransactionValue, 2_000.0)
        assertEquals(1_000_000_000.0, plan.orders.first { it.symbol == "کمند" }.postTransactionValue, 2_000.0)

        // و پس از اجرای این برنامه سبد واقعاً متعادل است.
        assertEquals(0.0, plan.postTrIndex, 0.2)
    }

    @Test
    fun lockedCashPlan_reportsUnitsSoTheOrderCanBePlaced() {
        val calc = PcmrEngine.calculatePortfolio(goldHeavyPortfolio(goldLocked = true), lockSettings())
        val plan = requireNotNull(PcmrEngine.lockedHoldCashPlan(calc))

        // ۷۰۰ میلیون خرید «اهرم» با قیمت ۲۵۰۰ ریال ⇒ ۲۸۰ هزار واحد
        assertEquals(280_000L, plan.orders.first { it.symbol == "اهرم" }.estimatedUnits)
    }

    @Test
    fun lockedPositionWithZeroTargetWeight_isFlaggedAsUnreachableByCash() {
        // طلا قفل است ولی وزن هدفش صفر — هیچ مقدار پول نقدی وزنش را صفر نمی‌کند.
        val settings = SettingsEntity(
            isManualWeightsEnabled = true,
            manualGoldWeight = 0.0, manualStockWeight = 50.0,
            manualMixedWeight = 0.0, manualFixedWeight = 50.0
        )
        val calc = PcmrEngine.calculatePortfolio(goldHeavyPortfolio(goldLocked = true), settings)
        val summary = requireNotNull(calc.lockedHold)

        assertTrue(summary.isUnreachableByCash)
        assertEquals(0.0, summary.requiredCashRial, 1.0)
        assertNull(PcmrEngine.lockedHoldCashPlan(calc))
    }

    @Test
    fun underweightLockedPosition_stillGetsABuySignal() {
        // قفل فقط جلوی فروش را می‌گیرد؛ خرید همچنان پیشنهاد می‌شود.
        val portfolio = listOf(
            PortfolioEntity(
                id = 1, symbol = "طلا", assetCategory = AssetCategory.GOLD,
                currentValue = 100_000_000.0, isHoldLocked = true
            ),
            PortfolioEntity(
                id = 2, symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME,
                currentValue = 900_000_000.0
            )
        )
        val calc = PcmrEngine.calculatePortfolio(portfolio, lockSettings())
        val gold = calc.symbolAlerts.first { it.symbol == "طلا" }

        assertEquals(RebalanceAction.BUY, gold.action)
        assertEquals(0.0, gold.suppressedSellAmount, 0.001)
        assertNull(calc.lockedHold)
    }

    @Test
    fun withdrawal_neverSellsALockedPosition_andShiftsTheLoadToTheFreeRows() {
        val calc = PcmrEngine.calculatePortfolio(goldHeavyPortfolio(goldLocked = true), lockSettings())

        val result = PcmrEngine.calculateDepositWithdrawal(
            calc, TransactionType.WITHDRAWAL, 200_000_000.0
        )

        val gold = result.orders.first { it.symbol == "طلا" }
        assertEquals(RebalanceAction.HOLD, gold.action)
        assertEquals(500_000_000.0, gold.postTransactionValue, 1.0)
        assertEquals(listOf("طلا"), result.frozenSymbols)

        // پول برداشتی واقعاً از سبد بیرون می‌رود: جمع سفارش‌ها = منفیِ مبلغ برداشت
        assertEquals(-200_000_000.0, result.orders.sumOf { it.orderAmount }, 2_000.0)
        assertEquals(800_000_000.0, result.newPv, 1.0)

        // و ردیف‌های آزاد کل بار را برمی‌دارند: ۳۰۰ میلیون باقیمانده به نسبت ۴۰/۴۰
        assertEquals(150_000_000.0, result.orders.first { it.symbol == "اهرم" }.postTransactionValue, 2_000.0)
        assertEquals(150_000_000.0, result.orders.first { it.symbol == "کمند" }.postTransactionValue, 2_000.0)
    }

    @Test
    fun withdrawal_isCappedByTheUnlockedPartOfThePortfolio() {
        val calc = PcmrEngine.calculatePortfolio(goldHeavyPortfolio(goldLocked = true), lockSettings())

        // کل سبد ۱ میلیارد است ولی ۵۰۰ میلیونش قفل است
        val result = PcmrEngine.calculateDepositWithdrawal(
            calc, TransactionType.WITHDRAWAL, 900_000_000.0
        )

        assertEquals(500_000_000.0, result.effectiveAmount, 1.0)
        assertTrue(result.isAmountClamped)
        assertTrue(result.isLimitedByLockedPositions)
        assertEquals(500_000_000.0, result.newPv, 1.0)
        assertTrue(result.orders.none { it.symbol == "طلا" && it.action == RebalanceAction.SELL })
    }

    @Test
    fun deposit_withNoLocks_behavesExactlyAsBefore() {
        // محافظ بازنویسی فاز ۴: بدون قفل، سهم هر ردیف همان `newPv × Weight` است.
        val calc = PcmrEngine.calculatePortfolio(goldHeavyPortfolio(goldLocked = false), lockSettings())
        val result = PcmrEngine.calculateDepositWithdrawal(
            calc, TransactionType.DEPOSIT, 1_000_000_000.0
        )

        assertTrue(result.frozenSymbols.isEmpty())
        assertFalse(result.isLimitedByLockedPositions)
        assertEquals(2_000_000_000.0, result.newPv, 1.0)
        assertEquals(400_000_000.0, result.orders.first { it.symbol == "طلا" }.postTransactionValue, 2_000.0)
        assertEquals(800_000_000.0, result.orders.first { it.symbol == "اهرم" }.postTransactionValue, 2_000.0)
        assertEquals(0.0, result.postTrIndex, 0.2)
    }

    @Test
    fun twoLockedPositions_cashIsSizedByTheMostOverweightOne() {
        val portfolio = listOf(
            // ۵۰٪ سبد، هدف ۲۰٪  ⇒  نیاز به سبد ۲.۵ میلیاردی
            PortfolioEntity(
                id = 1, symbol = "طلا", assetCategory = AssetCategory.GOLD,
                currentValue = 500_000_000.0, isHoldLocked = true
            ),
            // ۳۰٪ سبد، هدف ۴۰٪ ⇒ کم‌وزن است و قفلش بی‌اثر
            PortfolioEntity(
                id = 2, symbol = "اهرم", assetCategory = AssetCategory.STOCK,
                currentValue = 300_000_000.0, isHoldLocked = true
            ),
            PortfolioEntity(
                id = 3, symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME,
                currentValue = 200_000_000.0
            )
        )
        val calc = PcmrEngine.calculatePortfolio(portfolio, lockSettings())
        val summary = requireNotNull(calc.lockedHold)

        assertEquals(listOf("طلا"), summary.bindingSymbols)
        assertEquals(1_500_000_000.0, summary.requiredCashRial, 1_000.0)

        // نماد قفل‌شده کم‌وزن، در برنامه خرید هم خرید می‌گیرد — قفل مانعش نیست.
        val plan = requireNotNull(PcmrEngine.lockedHoldCashPlan(calc))
        assertEquals(RebalanceAction.BUY, plan.orders.first { it.symbol == "اهرم" }.action)
    }

    @Test
    fun withdrawal_freezesASecondLockedRowWhenTheFirstFreezePushesItOverweight() {
        // هر دو قفل‌اند. با برداشت بزرگ، «اهرم» هم اضافه‌وزن می‌شود و باید
        // منجمد شود، وگرنه فرمول برایش فروش می‌نوشت.
        val portfolio = listOf(
            PortfolioEntity(
                id = 1, symbol = "طلا", assetCategory = AssetCategory.GOLD,
                currentValue = 500_000_000.0, isHoldLocked = true
            ),
            PortfolioEntity(
                id = 2, symbol = "اهرم", assetCategory = AssetCategory.STOCK,
                currentValue = 300_000_000.0, isHoldLocked = true
            ),
            PortfolioEntity(
                id = 3, symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME,
                currentValue = 200_000_000.0
            )
        )
        val calc = PcmrEngine.calculatePortfolio(portfolio, lockSettings())
        val result = PcmrEngine.calculateDepositWithdrawal(
            calc, TransactionType.WITHDRAWAL, 200_000_000.0
        )

        assertTrue(result.orders.none { it.action == RebalanceAction.SELL && it.isHoldLocked })
        assertEquals(setOf("طلا", "اهرم"), result.frozenSymbols.toSet())
        // تنها ردیف آزاد کل برداشت را می‌دهد
        assertEquals(0.0, result.orders.first { it.symbol == "کمند" }.postTransactionValue, 2_000.0)
        assertEquals(-200_000_000.0, result.orders.sumOf { it.orderAmount }, 2_000.0)
    }

    // ------------------------------------------------------------------
    // آستانه تحریک و سهم قفل از Tr
    // ------------------------------------------------------------------

    @Test
    fun trigger_comparesTheSameRoundedTrThatTheUserAndTheAlertSee() {
        // Tr خام = ۴٫۹۶٪ که ۵٫۰ نمایش داده می‌شود
        val portfolio = listOf(
            PortfolioEntity(id = 1, symbol = "طلا", assetCategory = AssetCategory.GOLD, currentValue = 524_800_000.0),
            PortfolioEntity(id = 2, symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME, currentValue = 475_200_000.0)
        )
        val settings = SettingsEntity(
            isManualWeightsEnabled = true,
            manualGoldWeight = 50.0, manualStockWeight = 0.0,
            manualMixedWeight = 0.0, manualFixedWeight = 50.0,
            rebalanceThresholdPercent = 5.0
        )
        val calc = PcmrEngine.calculatePortfolio(portfolio, settings)
        val alert = RebalanceAlertPolicy.decide(
            trIndex = calc.trIndex,
            thresholdPercent = settings.rebalanceThresholdPercent,
            hasPortfolio = true,
            state = RebalanceAlertPolicy.AlertState(),
            nowEpochMs = 1L
        )

        assertEquals(5.0, calc.trIndex, 0.0)
        // قبلاً داشبورد «زیر آستانه» می‌گفت و نوتیفیکیشن «از آستانه گذشت»
        assertTrue(calc.isRebalanceTriggered)
        assertEquals(calc.isRebalanceTriggered, alert.shouldNotify)
    }

    /**
     * سبد واقعی از خروجی کارگزاری (ستون «ارزش فعلی»، ریال). وزن هدف دستی
     * برابر وزن فعلی طبقات است تا Tr پیش از تکان بازار صفر باشد؛ بعد طلا
     * [goldMovePercent] درصد بالا می‌رود.
     */
    private fun brokerPortfolio(lockedSymbols: Set<String>, goldMovePercent: Double): List<PortfolioEntity> =
        listOf(
            Triple("اطلس", AssetCategory.STOCK, 978_491_082.36),
            Triple("آگاس", AssetCategory.STOCK, 362_641_535.67),
            Triple("افران", AssetCategory.FIXED_INCOME, 1_012_456_127.08),
            Triple("عیار", AssetCategory.GOLD, 634_108_716.33),
            Triple("زیتون", AssetCategory.MIXED, 1_012_197_415.38),
            Triple("درخشان", AssetCategory.GOLD, 3_115_742_626.79),
            Triple("غزنجان", AssetCategory.STOCK, 800_294.88),
            Triple("سهامدار", AssetCategory.STOCK, 2_877_292_568.13)
        ).mapIndexed { i, (symbol, category, value) ->
            val move = if (category == AssetCategory.GOLD) 1.0 + goldMovePercent / 100.0 else 1.0
            PortfolioEntity(
                id = i + 1L, symbol = symbol, assetCategory = category,
                currentValue = value * move, isHoldLocked = symbol in lockedSymbols
            )
        }

    private fun brokerSettings(threshold: Double): SettingsEntity {
        val start = brokerPortfolio(emptySet(), 0.0)
        val pv = start.sumOf { it.currentValue }
        fun weight(c: AssetCategory) = start.filter { it.assetCategory == c }.sumOf { it.currentValue } / pv * 100.0
        return SettingsEntity(
            isManualWeightsEnabled = true,
            manualGoldWeight = weight(AssetCategory.GOLD),
            manualStockWeight = weight(AssetCategory.STOCK),
            manualMixedWeight = weight(AssetCategory.MIXED),
            manualFixedWeight = weight(AssetCategory.FIXED_INCOME),
            rebalanceThresholdPercent = threshold
        )
    }

    /** Tr پس از اجرای برنامه‌ی بدون پول نقد، با بازاندازه‌گیری کامل موتور. */
    private fun trAfterCashlessPlan(items: List<PortfolioEntity>, settings: SettingsEntity): Double {
        val calc = PcmrEngine.calculatePortfolio(items, settings)
        val plan = PcmrEngine.calculateDepositWithdrawal(calc, TransactionType.DEPOSIT, 0.0)
        val after = items.map { item ->
            item.copy(currentValue = plan.orders.first { it.itemId == item.id }.postTransactionValue)
        }
        return PcmrEngine.calculatePortfolio(after, settings).trIndex
    }

    @Test
    fun thresholdIsAppliedPerTradingSystem_onTheRealPortfolio() {
        // طلا ۱۲٪ بالا ⇒ Tr = ۵٫۴٪
        val items = brokerPortfolio(emptySet(), 12.0)
        val triggered = listOf(3.0, 5.0, 10.0, 20.0).associateWith {
            PcmrEngine.calculatePortfolio(items, brokerSettings(it)).isRebalanceTriggered
        }

        assertEquals(5.4, PcmrEngine.calculatePortfolio(items, brokerSettings(5.0)).trIndex, 0.0)
        assertEquals(mapOf(3.0 to true, 5.0 to true, 10.0 to false, 20.0 to false), triggered)
    }

    @Test
    fun lockBoundTr_isZeroWithoutLocks() {
        val calc = PcmrEngine.calculatePortfolio(brokerPortfolio(emptySet(), 12.0), brokerSettings(5.0))
        assertEquals(0.0, calc.lockBoundTrIndex, 0.0)
        assertEquals(0.0, trAfterCashlessPlan(brokerPortfolio(emptySet(), 12.0), brokerSettings(5.0)), 0.05)
    }

    @Test
    fun lockBoundTr_isTheTrLeftAfterEveryTradePossibleWithoutCash() {
        val settings = brokerSettings(5.0)
        val items = brokerPortfolio(setOf("درخشان"), 12.0)
        val calc = PcmrEngine.calculatePortfolio(items, settings)

        // Tr کامل می‌ماند و هشدار روشن است
        assertEquals(5.4, calc.trIndex, 0.0)
        assertTrue(calc.isRebalanceTriggered)
        // ولی پس از معامله ۴٫۰ می‌ماند — زیر آستانه ۵٪ میان‌بسامد، بالای ۳٪ پربسامد
        assertEquals(4.0, calc.lockBoundTrIndex, 0.0)
        assertEquals(trAfterCashlessPlan(items, settings), calc.lockBoundTrIndex, 0.0)
    }

    @Test
    fun lockingEveryGoldFund_leavesTheWholeTrToCash() {
        val settings = brokerSettings(5.0)
        val items = brokerPortfolio(setOf("درخشان", "عیار"), 12.0)
        val calc = PcmrEngine.calculatePortfolio(items, settings)

        // هیچ فروشی ممکن نیست، پس هیچ بخشی از Tr با معامله پایین نمی‌آید
        assertEquals(0.0, calc.totalSellAmount, 1.0)
        assertEquals(calc.trIndex, calc.lockBoundTrIndex, 0.0)
        assertEquals(trAfterCashlessPlan(items, settings), calc.lockBoundTrIndex, 0.0)
        // مبلغ نقد لازم را پرتقاضاترین قفل تعیین می‌کند، با یک یا دو قفل یکی است
        val oneLock = PcmrEngine.calculatePortfolio(brokerPortfolio(setOf("درخشان"), 12.0), settings)
        assertEquals(oneLock.lockedHold!!.requiredCashRial, calc.lockedHold!!.requiredCashRial, 1.0)
    }

    @Test
    fun lockWithinTolerance_leavesNoLockBoundTr() {
        // طلا فقط ۳٪ بالا رفته: قفل درخشان در آستانه ۲۰٪ «مانع» حساب نمی‌شود
        val calc = PcmrEngine.calculatePortfolio(brokerPortfolio(setOf("درخشان"), 3.0), brokerSettings(20.0))
        assertNull(calc.lockedHold)
        assertEquals(0.0, calc.lockBoundTrIndex, 0.0)
    }

    @Test
    fun lockBoundTr_accountsForBuysIntoEmptyCategories() {
        // «کمند» قفل و اضافه‌وزن؛ طبقه سهامی خالی است و ردیف پیشنهاد ورود می‌گیرد
        val portfolio = listOf(
            PortfolioEntity(id = 1, symbol = "طلا", assetCategory = AssetCategory.GOLD, currentValue = 300_000_000.0),
            PortfolioEntity(
                id = 2, symbol = "کمند", assetCategory = AssetCategory.FIXED_INCOME,
                currentValue = 700_000_000.0, isHoldLocked = true
            )
        )
        val calc = PcmrEngine.calculatePortfolio(portfolio, lockSettings())
        // هدف: طلا ۲۰۰، سهامی ۴۰۰، کمند ۴۰۰. بدون نقد، کمند ۷۰۰ می‌ماند و
        // ۳۰۰ باقیمانده ۱ به ۲ بین طلا (۱۰۰) و سهامی (۲۰۰) پخش می‌شود.
        // |۱۰۰−۲۰۰| + |۲۰۰−۴۰۰| + |۷۰۰−۴۰۰| = ۶۰۰ ⇒ ۶۰٪
        assertEquals(60.0, calc.lockBoundTrIndex, 0.0)
    }

    @Test
    fun automaticWeightsAlwaysSumToExactly100_afterRounding() {
        // هر وزن جدا گرد می‌شد و جمع در حدود یک‌چهارم ریسک‌پذیری‌ها ۱۰۰٫۱ درمی‌آمد.
        for (months in listOf(3, 6, 12)) {
            for (tenths in 1..400) {
                val weights = PcmrEngine.calculateTargetWeights(
                    SettingsEntity(riskTolerance = tenths / 10.0, timeHorizonMonths = months)
                )
                val sum = weights.goldWeight + weights.stockWeight +
                    weights.mixedWeight + weights.fixedIncomeWeight
                assertEquals("RT=${tenths / 10.0} افق=$months", 100.0, sum, 1e-9)
                assertTrue(weights.fixedIncomeWeight >= 0.0)
            }
        }
    }
}
