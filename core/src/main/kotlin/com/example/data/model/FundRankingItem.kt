package com.example.data.model

/**
 * مدل ارزیابی و رتبه‌بندی یک صندوق.
 *
 * قاعده این فایل: **هیچ فیلدی مقدار ساختگی نمی‌گیرد.**
 * هر عددی که از API نیامده باشد `null` می‌ماند و UI موظف است
 * «داده در دسترس نیست» نشان دهد، نه عدد جایگزین.
 *
 * منابع واقعی:
 *  - `fund_live_data` : قیمت، NAV، حباب، ارزش معاملات، نقدشوندگی، خالص دارایی
 *  - `funds`          : دسته، مدیر، تاریخ تأسیس
 *  - `ta_symbol_state`: روند، RSI، فاصله از میانگین‌های متحرک، حمایت و مقاومت
 */
data class FundRankingItem(
    val symbol: String,
    val fundName: String,
    val category: AssetCategory,
    val inUserPortfolio: Boolean = false,
    val userPortfolioValueRial: Double = 0.0,

    // --- داده بازار (واقعی یا null) ---
    val navPriceRial: Double? = null,
    val marketPriceRial: Double? = null,
    val bubblePercent: Double? = null,
    val tradeValueToman: Double? = null,
    val tradeVolume: Long? = null,
    val priceChangePercent: Double? = null,
    val liquidityToman: Double? = null,
    val netAssetToman: Double? = null,
    val establishedDate: String = "",
    val managerName: String = "",

    // --- وضعیت تکنیکال واقعی ---
    val technical: FundTechnicalState = FundTechnicalState(symbol = ""),

    /**
     * بازدهی دوره‌ای از جدول `fund_return_data`؛ null یعنی برای این صندوق
     * ردیفی وجود نداشت و جدول مقایسه باید «—» نشان دهد.
     */
    val returns: FundReturns? = null,

    /** شناسه FundBase — کلید اتصال به جدول بازدهی */
    val fundId: String = "",

    /**
     * امتیاز «تداوم بازدهی»، **خوانده‌شده از** `rpc/get_fund_return_consistency`.
     * `null` یعنی FundBase برای این صندوق عددی نداده (مثلاً سابقه‌اش زیر
     * ۳۰ روز است) — نه اینکه صفر است؛ صفر یعنی «بدترینِ دسته».
     */
    val consistency: FundConsistency? = null,

    // --- وضعیت داده ---
    val hasLiveMarketData: Boolean = false,
    /**
     * true یعنی داده از یک دریافت موفقِ تازه می‌آید. false با
     * `hasLiveMarketData = true` یعنی داده از کش است (شبکه در دسترس نبوده یا
     * درخواست شکست خورده) و کاربر باید کهنه بودنش را ببیند.
     */
    val isMarketDataFresh: Boolean = false,
    val dataSourceName: String = "",
    /** ساعتِ آخرین دریافت موفق از API — نه زمان ساختِ این ردیف. */
    val lastUpdatedTime: String = "",

    val isLeveraged: Boolean = false,

    // --- توصیف‌ها (فقط وقتی داده هست ساخته می‌شوند) ---
    val bubbleTierDescription: String = "",
    val bubbleStatus: StatusLevel? = null,
    val liquidityDescription: String = "",
    val trendDescription: String = "",
    val trendStatus: StatusLevel? = null,
    val stabilityDescription: String = "",

    /** امتیاز کل ۰ تا ۱۰۰؛ null یعنی داده کافی برای امتیازدهی نبود */
    val compositeScore: Int? = null,
    val gradeTier: String = "",
    val descriptiveTags: List<String> = emptyList(),
    val recommendationReason: String = "",
    val isTop4PickInCategory: Boolean = false,

    // --- رتبه در گروه هم‌رده (null = قابل محاسبه نبود) ---
    val categoryPeerCount: Int = 0,
    val categoryPersianName: String = "",
    val totalInGroup: Int = 0,
    val rankMomentum: Int? = null,
    val rankLiquidity: Int? = null,
    val rankLowBubble: Int? = null,
    val rankNetAsset: Int? = null,
    val rankAge: Int? = null,

    val technicalInsight: FundTechnicalInsight? = null
) {
    /** امتیاز مومنتوم واقعی از داده تکنیکال */
    val momentumScore: Int? get() = technical.momentumScore

    val hasTechnicalData: Boolean get() = technical.hasData
}

enum class StatusLevel {
    EXCELLENT,
    GOOD,
    FAIR,
    RISKY
}
