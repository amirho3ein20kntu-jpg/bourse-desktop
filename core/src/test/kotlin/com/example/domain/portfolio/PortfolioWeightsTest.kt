package com.example.domain.portfolio

import com.example.data.model.AssetCategory
import com.example.data.model.PortfolioEntity
import com.example.data.model.SettingsEntity
import com.example.domain.calculator.PcmrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioWeightsTest {

    private fun item(id: Long, category: AssetCategory, value: Double, target: Double? = null) =
        PortfolioEntity(
            id = id,
            symbol = "نماد$id",
            assetCategory = category,
            currentValue = value,
            manualTargetPercent = target
        )

    @Test
    fun `current weights are each value over total and sum to 100`() {
        val items = listOf(
            item(1, AssetCategory.STOCK, 600.0),
            item(2, AssetCategory.GOLD, 300.0),
            item(3, AssetCategory.MIXED, 100.0)
        )

        val weights = PortfolioWeights.forItems(items, emptyList())

        assertEquals(60.0, weights.getValue(1).currentPercent, 1e-9)
        assertEquals(30.0, weights.getValue(2).currentPercent, 1e-9)
        assertEquals(10.0, weights.getValue(3).currentPercent, 1e-9)
        assertEquals(100.0, weights.values.sumOf { it.currentPercent }, 1e-9)
    }

    @Test
    fun `target weight comes from the engine for the same row`() {
        val items = listOf(
            item(1, AssetCategory.STOCK, 700.0),
            item(2, AssetCategory.GOLD, 300.0)
        )
        val calc = PcmrEngine.calculatePortfolio(items, SettingsEntity())

        val weights = PortfolioWeights.forItems(items, calc.symbolAlerts)

        for (alert in calc.symbolAlerts.filter { !it.isSuggestedNewPosition }) {
            assertEquals(alert.targetWeightPercent, weights.getValue(alert.itemId).targetPercent!!, 1e-9)
        }
        assertTrue(weights.values.all { it.targetPercent != null })
    }

    @Test
    fun `row without engine output has no target`() {
        val weights = PortfolioWeights.forItems(listOf(item(1, AssetCategory.STOCK, 500.0)), emptyList())

        assertEquals(100.0, weights.getValue(1).currentPercent, 1e-9)
        assertNull(weights.getValue(1).targetPercent)
    }

    @Test
    fun `empty or zero-value portfolio gives no weights instead of dividing by zero`() {
        assertTrue(PortfolioWeights.forItems(emptyList(), emptyList()).isEmpty())
        assertTrue(PortfolioWeights.forItems(listOf(item(1, AssetCategory.GOLD, 0.0)), emptyList()).isEmpty())
    }
}
