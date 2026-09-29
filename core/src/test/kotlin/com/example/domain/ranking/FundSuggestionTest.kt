package com.example.domain.ranking

import com.example.data.model.AssetCategory
import com.example.data.model.FundRankingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FundSuggestionTest {

    private fun fund(
        symbol: String,
        category: AssetCategory = AssetCategory.GOLD,
        bubblePercent: Double? = null,
        tradeValueToman: Double? = 2_000_000_000.0,
        isLeveraged: Boolean = false
    ) = FundRankingItem(
        symbol = symbol,
        fundName = symbol,
        category = category,
        bubblePercent = bubblePercent,
        tradeValueToman = tradeValueToman,
        isLeveraged = isLeveraged
    )

    @Test
    fun lowestBubbleFund_picksTheCheapestFundInTheCategory() {
        val funds = listOf(
            fund("طلا-الف", bubblePercent = 3.5),
            fund("طلا-ب", bubblePercent = 0.8),
            fund("طلا-ج", bubblePercent = 1.9)
        )
        val result = FundSuggestion.lowestBubbleFund(AssetCategory.GOLD, funds)
        assertEquals("طلا-ب", result?.symbol)
    }

    @Test
    fun lowestBubbleFund_ignoresOtherCategories() {
        val funds = listOf(
            fund("طلا-الف", category = AssetCategory.GOLD, bubblePercent = 5.0),
            fund("سهام-الف", category = AssetCategory.STOCK, bubblePercent = 0.1)
        )
        val result = FundSuggestion.lowestBubbleFund(AssetCategory.GOLD, funds)
        assertEquals("طلا-الف", result?.symbol)
    }

    @Test
    fun lowestBubbleFund_excludesLeveragedFundsEvenWithLowestBubble() {
        // صندوق اهرمی به‌عنوان STOCK ساده دسته‌بندی می‌شود؛ ریسک واقعی‌اش را
        // این تابع نباید پنهان کند.
        val funds = listOf(
            fund("اهرم-الف", category = AssetCategory.STOCK, bubblePercent = 0.1, isLeveraged = true),
            fund("سهام-ب", category = AssetCategory.STOCK, bubblePercent = 4.0, isLeveraged = false)
        )
        val result = FundSuggestion.lowestBubbleFund(AssetCategory.STOCK, funds)
        assertEquals("سهام-ب", result?.symbol)
    }

    @Test
    fun lowestBubbleFund_excludesFundsWithNoBubbleData() {
        val funds = listOf(
            fund("طلا-الف", bubblePercent = null),
            fund("طلا-ب", bubblePercent = 2.0)
        )
        val result = FundSuggestion.lowestBubbleFund(AssetCategory.GOLD, funds)
        assertEquals("طلا-ب", result?.symbol)
    }

    @Test
    fun lowestBubbleFund_excludesIlliquidFunds() {
        val funds = listOf(
            fund("طلا-کم‌حجم", bubblePercent = 0.1, tradeValueToman = 100_000_000.0),
            fund("طلا-پرحجم", bubblePercent = 3.0, tradeValueToman = 5_000_000_000.0)
        )
        val result = FundSuggestion.lowestBubbleFund(AssetCategory.GOLD, funds)
        assertEquals("طلا-پرحجم", result?.symbol)
    }

    @Test
    fun lowestBubbleFund_returnsNullWhenNoCandidateQualifies() {
        val funds = listOf(fund("طلا-الف", bubblePercent = null))
        assertNull(FundSuggestion.lowestBubbleFund(AssetCategory.GOLD, funds))
    }
}
