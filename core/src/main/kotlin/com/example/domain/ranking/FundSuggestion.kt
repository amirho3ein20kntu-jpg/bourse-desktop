package com.example.domain.ranking

import com.example.data.model.AssetCategory
import com.example.data.model.FundRankingItem

/**
 * وقتی سیگنال خرید می‌گوید «فلان طبقه کم‌وزن است»، این پیشنهاد می‌دهد که
 * **کدام** صندوقِ همان طبقه بهتر است خریداری شود.
 *
 * روش ۴ گزارش `rebalancing-methods-2026-09-28.md`: بین صندوق‌های یک طبقه،
 * آن‌که حباب (P/NAV) کمتری دارد را ترجیح بده — بدون ریسک اضافه، با قیمت
 * ورود بهتر. صندوق‌های اهرمی عمداً کنار گذاشته می‌شوند: دسته‌بندی‌شان به‌عنوان
 * STOCK ساده باعث می‌شود پیشنهاد این تابع ریسک واقعی‌شان را پنهان کند
 * (رجوع به یافته «صندوق‌های اهرمی» در audit).
 */
object FundSuggestion {

    /** زیر این ارزش معاملات روزانه، صندوق عملاً غیرقابل ورود و خروج است. */
    private const val MIN_LIQUIDITY_TOMAN = 1_000_000_000.0

    /**
     * ارزان‌ترین صندوق (کمترین حباب) قابل‌معامله‌ی یک طبقه، یا `null` اگر
     * هیچ صندوقی از آن طبقه داده حباب یا نقدشوندگی کافی نداشته باشد.
     */
    fun lowestBubbleFund(
        category: AssetCategory,
        candidates: List<FundRankingItem>
    ): FundRankingItem? = candidates
        .asSequence()
        .filter { it.category == category }
        .filter { !it.isLeveraged }
        .filter { it.bubblePercent != null }
        .filter { (it.tradeValueToman ?: 0.0) >= MIN_LIQUIDITY_TOMAN }
        .minByOrNull { it.bubblePercent!! }
}
