package com.example.domain.calculator

import com.example.data.model.AssetCategory
import com.example.domain.calculator.WithdrawalPlanner.Holding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * محدودیت واقعی بازار: حداقل فروش ۵۰۰ هزار تومان (۵ میلیون ریال).
 * برنامه‌ای که سفارش زیر این کف بسازد، در کارگزاری ثبت نمی‌شود.
 */
class WithdrawalPlannerTest {

    private fun holding(
        id: Long,
        symbol: String,
        valueRial: Double,
        targetWeight: Double,
        unitPrice: Double = 10_000.0
    ) = Holding(
        itemId = id,
        symbol = symbol,
        category = AssetCategory.STOCK,
        currentValueRial = valueRial,
        targetWeightPercent = targetWeight,
        unitPriceRial = unitPrice
    )

    /** سبد ۱ میلیارد تومانی (۱۰ میلیارد ریال)، متوازن، ۴ نماد ۲۵٪ */
    private fun balancedPortfolio() = listOf(
        holding(1, "اهرم", 2_500_000_000.0, 25.0),
        holding(2, "طلا", 2_500_000_000.0, 25.0),
        holding(3, "زیتون", 2_500_000_000.0, 25.0),
        holding(4, "کمند", 2_500_000_000.0, 25.0)
    )

    // ------------------------------------------------------------------
    // مسئله اصلی: سفارش‌های ریز نباید ساخته شوند
    // ------------------------------------------------------------------

    @Test
    fun aSmallWithdrawalDoesNotProduceUnexecutableCrumbs() {
        // برداشت ۱۰ میلیون تومان از سبدی متوازن با ۴ نماد.
        // پخش یکنواخت یعنی ۴ سفارش ۲.۵ میلیون تومانی — که هر کدام بالای کف است،
        // ولی روی سبد بزرگ‌تر با نمادهای بیشتر همین مبلغ خرد می‌شود.
        val plan = WithdrawalPlanner.plan(balancedPortfolio(), withdrawalRial = 100_000_000.0)

        assertTrue("هیچ سفارشی نباید زیر کف فروش باشد", plan.legs.all {
            it.amountRial >= PcmrEngine.MIN_SELL_ORDER_RIAL || it.closesPosition
        })
        assertEquals(100_000_000.0, plan.coveredRial, 1.0)
        assertNull(plan.shortfallNotice)
    }

    @Test
    fun tinyWithdrawalFromAManySymbolPortfolioConcentratesInsteadOfCrumbling() {
        // ۱۰ نماد، برداشت فقط ۳ میلیون تومان: پخش یکنواخت یعنی ۱۰ سفارش
        // ۳۰۰ هزار تومانی — همه زیر کف. باید در چند سفارش بزرگ جمع شود.
        val many = (1..10).map { holding(it.toLong(), "نماد$it", 1_000_000_000.0, 10.0) }

        val plan = WithdrawalPlanner.plan(many, withdrawalRial = 30_000_000.0)

        assertTrue(plan.legs.isNotEmpty())
        assertTrue("سفارش زیر کف نباید بماند", plan.legs.all {
            it.amountRial >= PcmrEngine.MIN_SELL_ORDER_RIAL || it.closesPosition
        })
        assertEquals(30_000_000.0, plan.coveredRial, 1.0)
    }

    // ------------------------------------------------------------------
    // برداشت باید سبد را متعادل‌تر کند، نه بدتر
    // ------------------------------------------------------------------

    @Test
    fun withdrawalIsTakenFromTheOverweightFundFirst() {
        val skewed = listOf(
            // اهرم خیلی پُروزن شده
            holding(1, "اهرم", 6_000_000_000.0, 25.0),
            holding(2, "طلا", 1_500_000_000.0, 25.0),
            holding(3, "زیتون", 1_500_000_000.0, 25.0),
            holding(4, "کمند", 1_000_000_000.0, 25.0)
        )

        val plan = WithdrawalPlanner.plan(skewed, withdrawalRial = 1_000_000_000.0)

        assertEquals("اهرم", plan.legs.first().symbol)
        // بقیه نمادها که کم‌وزن‌اند نباید دست بخورند
        assertTrue(plan.legs.none { it.symbol == "کمند" })
    }

    @Test
    fun withdrawingFromASkewedPortfolioImprovesItsBalance() {
        val skewed = listOf(
            holding(1, "اهرم", 6_000_000_000.0, 25.0),
            holding(2, "طلا", 1_500_000_000.0, 25.0),
            holding(3, "زیتون", 1_500_000_000.0, 25.0),
            holding(4, "کمند", 1_000_000_000.0, 25.0)
        )
        val pv = skewed.sumOf { it.currentValueRial }
        val trBefore = skewed.sumOf {
            kotlin.math.abs(it.currentValueRial - pv * (it.targetWeightPercent / 100.0))
        } / pv * 100.0

        val plan = WithdrawalPlanner.plan(skewed, withdrawalRial = 1_000_000_000.0)

        assertTrue(
            "انحراف بعد از برداشت باید کمتر شود (قبل: $trBefore، بعد: ${plan.postTrIndex})",
            plan.postTrIndex < trBefore
        )
    }

    // ------------------------------------------------------------------
    // مرزها
    // ------------------------------------------------------------------

    @Test
    fun withdrawingEverythingClosesEveryPosition() {
        val portfolio = balancedPortfolio()
        val total = portfolio.sumOf { it.currentValueRial }

        val plan = WithdrawalPlanner.plan(portfolio, withdrawalRial = total)

        assertEquals(total, plan.coveredRial, 1.0)
        assertTrue(plan.legs.all { it.closesPosition })
    }

    @Test
    fun askingForMoreThanYouHaveIsCappedAndExplained() {
        val portfolio = balancedPortfolio()
        val total = portfolio.sumOf { it.currentValueRial }

        val plan = WithdrawalPlanner.plan(portfolio, withdrawalRial = total * 2)

        assertEquals(total, plan.coveredRial, 1.0)
        assertNotNull(plan.shortfallNotice)
        assertTrue(plan.shortfallNotice!!.contains("بیشتر"))
    }

    @Test
    fun aWithdrawalBelowTheExchangeMinimumCannotBeExecuted() {
        // ۲۰ هزار تومان: زیر کف فروش، و هیچ نمادی هم آن‌قدر کوچک نیست که بسته شود
        val plan = WithdrawalPlanner.plan(balancedPortfolio(), withdrawalRial = 200_000.0)

        assertTrue(plan.legs.isEmpty())
        assertNotNull(plan.shortfallNotice)
    }

    @Test
    fun zeroAndNegativeAmountsProduceNothing() {
        assertTrue(WithdrawalPlanner.plan(balancedPortfolio(), 0.0).legs.isEmpty())
        assertTrue(WithdrawalPlanner.plan(balancedPortfolio(), -5.0).legs.isEmpty())
    }

    @Test
    fun anEmptyPortfolioIsSafe() {
        val plan = WithdrawalPlanner.plan(emptyList(), 100_000_000.0)

        assertTrue(plan.legs.isEmpty())
        assertEquals(0.0, plan.coveredRial, 0.001)
    }

    // ------------------------------------------------------------------
    // جزئیاتی که کاربر می‌بیند
    // ------------------------------------------------------------------

    @Test
    fun legsCarryUnitsSoTheOrderCanActuallyBePlaced() {
        val portfolio = listOf(
            holding(1, "اهرم", 6_000_000_000.0, 25.0, unitPrice = 2_500.0),
            holding(2, "طلا", 1_500_000_000.0, 25.0),
            holding(3, "زیتون", 1_500_000_000.0, 25.0),
            holding(4, "کمند", 1_000_000_000.0, 25.0)
        )

        val leg = WithdrawalPlanner.plan(portfolio, 500_000_000.0).legs.first { it.symbol == "اهرم" }

        assertEquals(Math.round(leg.amountRial / 2_500.0), leg.estimatedUnits)
    }

    @Test
    fun aLegLeavingATinyRemainderIsFlagged() {
        // کمند وزن هدف کوچکی دارد و پُروزن است، پس بیشترِ برداشت از آن می‌آید
        // و باقیمانده‌اش زیر کف اقتصادی ۱۰ میلیون تومان می‌ماند.
        val portfolio = listOf(
            holding(1, "اهرم", 1_000_000_000.0, 95.0),
            holding(2, "کمند", 120_000_000.0, 5.0)
        )

        val plan = WithdrawalPlanner.plan(portfolio, withdrawalRial = 100_000_000.0)

        val kamand = plan.legs.first { it.symbol == "کمند" }
        assertTrue(
            "باقیمانده ${kamand.valueAfterRial} زیر کف اقتصادی است و باید علامت بخورد",
            kamand.leavesStub
        )
    }

    @Test
    fun noLegIsFlaggedAsBothClosedAndStub() {
        val plan = WithdrawalPlanner.plan(balancedPortfolio(), 5_000_000_000.0)

        assertTrue(plan.legs.none { it.closesPosition && it.leavesStub })
    }

    @Test
    fun aFullyCoveredPlanReportsNoShortfall() {
        val plan = WithdrawalPlanner.plan(balancedPortfolio(), 1_000_000_000.0)

        assertTrue(plan.isFullyCovered)
        assertFalse(plan.legs.isEmpty())
    }

    @Test
    fun aHoldLockedFundIsNeverSold_evenWhenItIsTheMostOverweight() {
        // طلا قفل است و بیشترین مازاد را دارد؛ پیش از این اولین فروش برنامه همین بود.
        val portfolio = listOf(
            holding(1, "طلا", 4_000_000_000.0, 20.0).copy(isHoldLocked = true),
            holding(2, "اهرم", 3_000_000_000.0, 40.0),
            holding(3, "کمند", 3_000_000_000.0, 40.0)
        )

        val plan = WithdrawalPlanner.plan(portfolio, withdrawalRial = 500_000_000.0)

        assertTrue(plan.legs.none { it.itemId == 1L })
        assertEquals(500_000_000.0, plan.coveredRial, 1.0)
    }

    @Test
    fun withdrawingMoreThanTheUnlockedPartLeavesAShortfallInsteadOfSellingTheLock() {
        val portfolio = listOf(
            holding(1, "طلا", 4_000_000_000.0, 50.0).copy(isHoldLocked = true),
            holding(2, "اهرم", 1_000_000_000.0, 50.0)
        )

        val plan = WithdrawalPlanner.plan(portfolio, withdrawalRial = 2_000_000_000.0)

        assertTrue(plan.legs.none { it.itemId == 1L })
        assertEquals(1_000_000_000.0, plan.coveredRial, 1.0)
        assertNotNull(plan.shortfallNotice)
    }
}
