package com.example.domain.portfolio

import com.example.data.model.AssetCategory
import com.example.data.model.PortfolioEntity
import com.example.data.model.RebalanceAction
import com.example.data.model.SymbolRebalanceAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * این ماژول داده واقعی سبد کاربر را تغییر می‌دهد، پس تست‌ها روی دو چیز سخت‌گیرند:
 * چیزی از هوا ساخته نشود، و بیشتر از موجودی فروخته نشود.
 */
class OrderExecutionTest {

    private fun item(
        id: Long,
        symbol: String,
        quantity: Long,
        lastPrice: Double,
        averagePrice: Double = 0.0
    ) = PortfolioEntity(
        id = id,
        symbol = symbol,
        assetCategory = AssetCategory.STOCK,
        currentValue = quantity * lastPrice,
        quantity = quantity,
        lastPrice = lastPrice,
        averagePrice = averagePrice
    )

    private fun alert(
        itemId: Long,
        symbol: String,
        action: RebalanceAction,
        units: Long,
        lastPrice: Double,
        isSuggestion: Boolean = false
    ) = SymbolRebalanceAlert(
        itemId = itemId,
        symbol = symbol,
        category = AssetCategory.STOCK,
        currentValue = 0.0,
        targetValue = 0.0,
        deltaValue = 0.0,
        currentWeightPercent = 0.0,
        targetWeightPercent = 0.0,
        action = action,
        actionAmount = units * lastPrice,
        estimatedUnits = units,
        lastPrice = lastPrice,
        isSuggestedNewPosition = isSuggestion
    )

    // ------------------------------------------------------------------
    // ساختن فهرست سفارش‌ها
    // ------------------------------------------------------------------

    @Test
    fun buyAndSellSignalsBecomeExecutableOrders() {
        val portfolio = listOf(
            item(1, "اهرم", quantity = 100_000L, lastPrice = 2_500.0),
            item(2, "کمند", quantity = 50_000L, lastPrice = 10_000.0)
        )
        val alerts = listOf(
            alert(1, "اهرم", RebalanceAction.BUY, units = 20_000L, lastPrice = 2_500.0),
            alert(2, "کمند", RebalanceAction.SELL, units = 5_000L, lastPrice = 10_000.0)
        )

        val plan = OrderExecution.planFrom(alerts, portfolio)

        assertEquals(2, plan.size)
        val buy = plan.first { it.symbol == "اهرم" }
        assertEquals(120_000L, buy.newQuantity)
        assertEquals(300_000_000.0, buy.newValueRial, 0.001)

        val sell = plan.first { it.symbol == "کمند" }
        assertEquals(45_000L, sell.newQuantity)
    }

    @Test
    fun holdSignalsAreNotExecutable() {
        val portfolio = listOf(item(1, "اهرم", 100_000L, 2_500.0))
        val alerts = listOf(alert(1, "اهرم", RebalanceAction.HOLD, 0L, 2_500.0))

        assertTrue(OrderExecution.planFrom(alerts, portfolio).isEmpty())
    }

    @Test
    fun suggestedNewPositionsAreNotExecutable() {
        // طبقه‌ای که وزن هدف دارد ولی کاربر هنوز نمادی برایش انتخاب نکرده
        val alerts = listOf(
            alert(-1, "صندوق‌های طلا", RebalanceAction.BUY, 100L, 20_000.0, isSuggestion = true)
        )

        assertTrue(OrderExecution.planFrom(alerts, emptyList()).isEmpty())
    }

    @Test
    fun signalsWithoutAKnownPriceAreNotExecutable() {
        val portfolio = listOf(item(1, "اهرم", 100_000L, 0.0))
        val alerts = listOf(alert(1, "اهرم", RebalanceAction.BUY, 20_000L, lastPrice = 0.0))

        assertTrue(OrderExecution.planFrom(alerts, portfolio).isEmpty())
    }

    @Test
    fun sellIsCappedAtWhatYouActuallyHold() {
        val portfolio = listOf(item(1, "اهرم", quantity = 3_000L, lastPrice = 2_500.0))
        val alerts = listOf(alert(1, "اهرم", RebalanceAction.SELL, units = 10_000L, lastPrice = 2_500.0))

        val order = OrderExecution.planFrom(alerts, portfolio).single()

        assertEquals(3_000L, order.units)
        assertEquals(0L, order.newQuantity)
        assertTrue("باید علامت بخورد که تعداد کم شد", order.isCappedBySholding)
    }

    @Test
    fun ordinaryOrdersAreNotFlaggedAsCapped() {
        val portfolio = listOf(item(1, "اهرم", 100_000L, 2_500.0))
        val alerts = listOf(alert(1, "اهرم", RebalanceAction.SELL, 20_000L, 2_500.0))

        assertFalse(OrderExecution.planFrom(alerts, portfolio).single().isCappedBySholding)
    }

    // ------------------------------------------------------------------
    // اعمال روی سبد
    // ------------------------------------------------------------------

    @Test
    fun applyingABuyUpdatesQuantityValueAndWeightedBreakEven() {
        val portfolio = listOf(
            item(1, "اهرم", quantity = 100_000L, lastPrice = 2_500.0, averagePrice = 2_000.0)
        )
        val plan = OrderExecution.planFrom(
            listOf(alert(1, "اهرم", RebalanceAction.BUY, 100_000L, 3_000.0)),
            portfolio
        )

        val updated = OrderExecution.apply(portfolio, plan).single()

        assertEquals(200_000L, updated.quantity)
        assertEquals(600_000_000.0, updated.currentValue, 0.001)
        assertEquals(3_000.0, updated.lastPrice, 0.001)
        // میانگین وزنی: (۱۰۰٬۰۰۰×۲۰۰۰ + ۱۰۰٬۰۰۰×۳۰۰۰) ÷ ۲۰۰٬۰۰۰ = ۲۵۰۰
        assertEquals(2_500.0, updated.averagePrice, 0.001)
    }

    @Test
    fun applyingASellKeepsTheBreakEvenPrice() {
        val portfolio = listOf(
            item(1, "اهرم", quantity = 100_000L, lastPrice = 2_500.0, averagePrice = 2_000.0)
        )
        val plan = OrderExecution.planFrom(
            listOf(alert(1, "اهرم", RebalanceAction.SELL, 40_000L, 2_500.0)),
            portfolio
        )

        val updated = OrderExecution.apply(portfolio, plan).single()

        assertEquals(60_000L, updated.quantity)
        assertEquals(2_000.0, updated.averagePrice, 0.001)
    }

    @Test
    fun buyingIntoAnEmptyPositionSetsBreakEvenToTodaysPrice() {
        val portfolio = listOf(item(1, "اهرم", quantity = 0L, lastPrice = 2_500.0))
        val plan = OrderExecution.planFrom(
            listOf(alert(1, "اهرم", RebalanceAction.BUY, 10_000L, 2_500.0)),
            portfolio
        )

        val updated = OrderExecution.apply(portfolio, plan).single()

        assertEquals(2_500.0, updated.averagePrice, 0.001)
    }

    @Test
    fun unknownBreakEvenStaysUnknown_ratherThanBeingInvented() {
        // موجودی هست ولی سر به سرش را نمی‌دانیم؛ میانگین وزنی بی‌معنا می‌شود
        val portfolio = listOf(
            item(1, "اهرم", quantity = 100_000L, lastPrice = 2_500.0, averagePrice = 0.0)
        )
        val plan = OrderExecution.planFrom(
            listOf(alert(1, "اهرم", RebalanceAction.BUY, 10_000L, 2_500.0)),
            portfolio
        )

        val updated = OrderExecution.apply(portfolio, plan).single()

        assertEquals(0.0, updated.averagePrice, 0.001)
    }

    @Test
    fun appliedRowsAreNoLongerValueLocked_soLivePriceSyncCanMaintainThem() {
        val portfolio = listOf(
            item(1, "اهرم", 100_000L, 2_500.0).copy(isValueManuallySet = true)
        )
        val plan = OrderExecution.planFrom(
            listOf(alert(1, "اهرم", RebalanceAction.BUY, 10_000L, 2_500.0)),
            portfolio
        )

        assertFalse(OrderExecution.apply(portfolio, plan).single().isValueManuallySet)
    }

    @Test
    fun onlySelectedOrdersAreApplied() {
        val portfolio = listOf(
            item(1, "اهرم", 100_000L, 2_500.0),
            item(2, "کمند", 50_000L, 10_000.0)
        )
        val plan = OrderExecution.planFrom(
            listOf(
                alert(1, "اهرم", RebalanceAction.BUY, 10_000L, 2_500.0),
                alert(2, "کمند", RebalanceAction.SELL, 5_000L, 10_000.0)
            ),
            portfolio
        )

        // کاربر فقط سفارش اول را اجرا کرده
        val changed = OrderExecution.apply(portfolio, plan.filter { it.symbol == "اهرم" })

        assertEquals(1, changed.size)
        assertEquals("اهرم", changed.single().symbol)
    }

    @Test
    fun applyingNothingChangesNothing() {
        val portfolio = listOf(item(1, "اهرم", 100_000L, 2_500.0))

        assertTrue(OrderExecution.apply(portfolio, emptyList()).isEmpty())
    }

    @Test
    fun ordersForRowsThatVanishedAreIgnored() {
        val portfolio = listOf(item(1, "اهرم", 100_000L, 2_500.0))
        val plan = OrderExecution.planFrom(
            listOf(alert(1, "اهرم", RebalanceAction.BUY, 10_000L, 2_500.0)),
            portfolio
        )

        // ردیف بین نمایش دیالوگ و تأیید حذف شده
        assertTrue(OrderExecution.apply(emptyList(), plan).isEmpty())
    }
}
