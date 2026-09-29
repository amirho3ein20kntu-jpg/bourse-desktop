package com.example.data.model


data class FundCategoryEntity(
    val symbol: String, // e.g. "عیار"
    val category: AssetCategory, // GOLD, STOCK, MIXED, FIXED_INCOME
    val fundName: String = "" // Full name if known e.g. "صندوق طلا عیار"
)
