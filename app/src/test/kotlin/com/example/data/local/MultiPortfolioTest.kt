package com.example.data.local

import com.example.data.model.AssetCategory
import com.example.data.model.PortfolioEntity
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiPortfolioTest {
    private fun item(sym: String) = PortfolioEntity(symbol = sym, assetCategory = AssetCategory.values().first(), currentValue = 1000.0)

    @Test
    fun holdingsAreIsolatedPerPortfolio() = runBlocking {
        val f = File.createTempFile("pcmr", ".db").also { it.deleteOnExit() }
        val db = AppDatabase(f)
        val dir = db.portfolioDirectoryDao()
        val second = dir.create("همسرم")
        val a = PortfolioScope(1L)
        val b = PortfolioScope(second)
        db.portfolioDao(a).insertItem(item("AAA"))
        db.portfolioDao(b).insertItem(item("BBB"))
        db.portfolioDao(b).insertItem(item("CCC"))
        assertEquals(listOf("AAA"), db.portfolioDao(a).getAllPortfolioItemsDirect().map { it.symbol })
        assertEquals(setOf("BBB", "CCC"), db.portfolioDao(b).getAllPortfolioItemsDirect().map { it.symbol }.toSet())
        dir.delete(second)
        assertEquals(1, dir.getAll().size)
        assertEquals(listOf("AAA"), db.portfolioDao(a).getAllPortfolioItemsDirect().map { it.symbol })
    }
}
