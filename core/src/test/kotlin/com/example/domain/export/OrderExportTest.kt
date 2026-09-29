package com.example.domain.export

import com.example.data.model.AssetCategory
import com.example.data.model.DepositWithdrawalOrder
import com.example.data.model.RebalanceAction
import com.example.data.model.SymbolRebalanceAlert
import com.example.domain.calculator.WithdrawalPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderExportTest {

    private fun alert(
        symbol: String,
        action: RebalanceAction,
        amountRial: Double,
        units: Long = 0L,
        notice: String? = null
    ) = SymbolRebalanceAlert(
        itemId = symbol.hashCode().toLong(),
        symbol = symbol,
        category = AssetCategory.STOCK,
        currentValue = 0.0,
        targetValue = 0.0,
        deltaValue = 0.0,
        currentWeightPercent = 12.34,
        targetWeightPercent = 25.0,
        action = action,
        actionAmount = amountRial,
        estimatedUnits = units,
        minOrderNotice = notice
    )

    // ------------------------------------------------------------------
    // CSV باید در اکسل فارسی درست باز شود
    // ------------------------------------------------------------------

    @Test
    fun csvStartsWithABomSoExcelReadsPersianCorrectly() {
        val csv = OrderExport.signalsToCsv(
            listOf(alert("اهرم", RebalanceAction.BUY, 50_000_000.0))
        )

        assertTrue("بدون BOM اکسل ویندوزی فارسی را خراب نشان می‌دهد", csv.startsWith("﻿"))
    }

    @Test
    fun amountsAreWrittenInTomanAsPlainNumbers() {
        // ۵۰ میلیون ریال = ۵ میلیون تومان
        val csv = OrderExport.signalsToCsv(
            listOf(alert("اهرم", RebalanceAction.BUY, 50_000_000.0, units = 20_000L))
        )

        val row = csv.lines()[1]
        assertTrue("مبلغ باید تومانی و بدون جداکننده باشد: $row", row.contains(",5000000,"))
        assertTrue(row.contains(",20000,"))
    }

    @Test
    fun holdRowsAreLeftOut_theyAreNotOrders() {
        val csv = OrderExport.signalsToCsv(
            listOf(
                alert("اهرم", RebalanceAction.BUY, 50_000_000.0),
                alert("کمند", RebalanceAction.HOLD, 0.0)
            )
        )

        assertTrue(csv.contains("اهرم"))
        assertFalse(csv.contains("کمند"))
    }

    @Test
    fun commasInsideAValueAreQuotedSoTheCsvDoesNotBreak() {
        val csv = OrderExport.signalsToCsv(
            listOf(
                alert(
                    "طلا",
                    RebalanceAction.SELL,
                    10_000_000.0,
                    notice = "کمتر از حداقل فروش, لطفاً بررسی کنید"
                )
            )
        )

        val row = csv.lines()[1]
        assertTrue("ویرگول داخل سلول باید داخل نقل‌قول برود: $row", row.contains("\"کمتر از حداقل فروش, لطفاً بررسی کنید\""))
        // ساختار نباید بشکند: هفت ستون، پس شش ویرگولِ جداکننده بیرون از نقل‌قول
        assertEquals(7, splitCsvRow(row).size)
    }

    @Test
    fun everyRowHasTheSameColumnCountAsTheHeader() {
        val csv = OrderExport.signalsToCsv(
            listOf(
                alert("اهرم", RebalanceAction.BUY, 50_000_000.0, 20_000L),
                alert("طلا", RebalanceAction.SELL, 7_000_000.0, 350L, "زیر کف فروش")
            )
        )

        val lines = csv.removePrefix("﻿").lines().filter { it.isNotBlank() }
        val headerCount = splitCsvRow(lines.first()).size
        lines.drop(1).forEach {
            assertEquals("ستون‌های ردیف باید با سربرگ بخواند: $it", headerCount, splitCsvRow(it).size)
        }
    }

    @Test
    fun anEmptyOrderListStillProducesAUsableHeader() {
        val csv = OrderExport.signalsToCsv(emptyList())

        assertTrue(csv.contains("نماد"))
        assertEquals(1, csv.removePrefix("﻿").lines().filter { it.isNotBlank() }.size)
    }

    // ------------------------------------------------------------------
    // متن قابل ارسال
    // ------------------------------------------------------------------

    @Test
    fun textListsEveryActionableOrderWithUnits() {
        val text = OrderExport.signalsToText(
            listOf(
                alert("اهرم", RebalanceAction.BUY, 50_000_000.0, 20_000L),
                alert("کمند", RebalanceAction.HOLD, 0.0)
            ),
            title = "سیگنال‌های ریبلنس"
        )

        assertTrue(text.contains("سیگنال‌های ریبلنس"))
        assertTrue(text.contains("خرید اهرم"))
        assertTrue(text.contains("5000000"))
        assertTrue(text.contains("20000 واحد"))
        assertFalse(text.contains("کمند"))
    }

    @Test
    fun textSaysSoWhenThereIsNothingToDo() {
        val text = OrderExport.signalsToText(
            listOf(alert("کمند", RebalanceAction.HOLD, 0.0)),
            title = "سیگنال‌ها"
        )

        assertTrue(text.contains("سفارشی برای اجرا وجود ندارد"))
    }

    @Test
    fun textCarriesTheMinimumOrderWarning() {
        val text = OrderExport.signalsToText(
            listOf(alert("طلا", RebalanceAction.SELL, 1_000_000.0, 50L, "زیر کف فروش بورس")),
            title = "سیگنال‌ها"
        )

        assertTrue(text.contains("زیر کف فروش بورس"))
    }

    // ------------------------------------------------------------------
    // سفارش‌های واریز/برداشت و برنامه برداشت
    // ------------------------------------------------------------------

    @Test
    fun transactionOrdersExportKeepsUnitsAndWarnings() {
        val csv = OrderExport.transactionOrdersToCsv(
            listOf(
                DepositWithdrawalOrder(
                    itemId = 1,
                    symbol = "اهرم",
                    category = AssetCategory.STOCK,
                    orderAmount = 50_000_000.0,
                    action = RebalanceAction.BUY,
                    absoluteAmount = 50_000_000.0,
                    currentWeight = 20.0,
                    targetWeight = 25.0,
                    postTransactionValue = 0.0,
                    estimatedUnits = 20_000L,
                    minOrderNotice = "زیر کف خرید"
                )
            )
        )

        assertTrue(csv.contains("اهرم"))
        assertTrue(csv.contains("20000"))
        assertTrue(csv.contains("زیر کف خرید"))
    }

    @Test
    fun withdrawalPlanExportShowsBeforeAndAfterValues() {
        val plan = WithdrawalPlanner.plan(
            holdings = listOf(
                WithdrawalPlanner.Holding(
                    1, "اهرم", AssetCategory.STOCK, 6_000_000_000.0, 25.0, 2_500.0
                ),
                WithdrawalPlanner.Holding(
                    2, "کمند", AssetCategory.STOCK, 2_000_000_000.0, 75.0, 10_000.0
                )
            ),
            withdrawalRial = 1_000_000_000.0
        )

        val csv = OrderExport.withdrawalPlanToCsv(plan)

        assertTrue(csv.startsWith("﻿"))
        assertTrue(csv.contains("ارزش قبل"))
        assertTrue(csv.contains("اهرم"))
    }

    @Test
    fun withdrawalPlanTextExplainsShortfallInsteadOfPretendingItWorked() {
        val plan = WithdrawalPlanner.plan(
            holdings = listOf(
                WithdrawalPlanner.Holding(
                    1, "اهرم", AssetCategory.STOCK, 1_000_000_000.0, 100.0, 2_500.0
                )
            ),
            withdrawalRial = 200_000.0 // زیر کف فروش
        )

        val text = OrderExport.withdrawalPlanToText(plan, "برنامه برداشت")

        assertTrue(plan.legs.isEmpty())
        assertTrue(text.contains("برنامه برداشت"))
        assertTrue(text.length > "برنامه برداشت".length)
    }

    @Test
    fun fileNamesCarryTheDateSoVersionsDoNotCollide() {
        assertEquals(
            "pcmr-orders-1405-06-19.csv",
            OrderExport.fileName("pcmr-orders", "1405-06-19")
        )
    }

    // ------------------------------------------------------------------

    /** جداکننده‌های بیرون از نقل‌قول را می‌شمارد. */
    private fun splitCsvRow(row: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in row.removePrefix("﻿")) {
            when {
                ch == '"' -> { inQuotes = !inQuotes; sb.append(ch) }
                ch == ',' && !inQuotes -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(ch)
            }
        }
        out.add(sb.toString())
        return out
    }
}
