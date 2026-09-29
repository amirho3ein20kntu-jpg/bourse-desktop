package com.example.data.model

/**
 * آخرین قیمت‌های طلا، سکه و دلار آزاد از آلان‌چند، برای کارت بالای دیده‌بان.
 *
 * منبعی جدا از FundBase است — نه NAV صندوق، بلکه قیمت نقدی خود فلز/ارز.
 * هر فیلدی که پارس نشود `null` می‌ماند و UI باید «دریافت نشد» نشان دهد،
 * نه صفر یا آخرین مقدار حدسی.
 */
data class AlanchandQuotes(
    val gram18Toman: Double?,
    val quarterCoinToman: Double?,
    val halfCoinToman: Double?,
    val fullCoinToman: Double?,
    val usdSellToman: Double?,
    val goldUpdateText: String?,
    val currencyUpdateText: String?,
    val fetchedAtEpochMs: Long
) {
    val hasAnyValue: Boolean
        get() = gram18Toman != null || quarterCoinToman != null || halfCoinToman != null ||
            fullCoinToman != null || usdSellToman != null
}
