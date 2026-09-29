package com.example.data.remote

import com.example.data.model.AssetCategory
import com.example.data.model.FundRankingItem
import com.example.data.model.FundTechnicalInsight
import com.example.data.model.FundTechnicalState
import com.example.data.model.PortfolioEntity
import com.example.data.model.StatusLevel
import com.example.data.parser.DefaultEtfDatabase
import com.example.domain.sync.MarketDataFreshness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

/**
 * ارزیابی و رتبه‌بندی صندوق‌ها.
 *
 * **قاعده این فایل:** هر عددی که نمایش داده می‌شود باید از API آمده باشد.
 * نسخه قبلی این سرویس بازدهی ۱/۳/۶/۱۲ ماهه، سرانه خریدار و جریان پول حقیقی را
 * از روی ثابت‌های هاردکدشده می‌ساخت و کنار برچسب «زنده» نشان می‌داد.
 * آن بخش‌ها حذف شدند؛ FundBase این فیلدها را ارائه نمی‌کند.
 *
 * جایگزین: داده تکنیکال واقعی از جدول `ta_symbol_state`
 * (روند روزانه/هفتگی/ماهانه، RSI، فاصله از میانگین‌های متحرک، حمایت و مقاومت).
 */
object TsetmcFundService {

    private val benchmarkSymbols = listOf(
        "طلا", "عیار", "کهربا", "زرفام", "گوهر", "زر", "درخشان",
        "اهرم", "شتاب", "موج", "جهش", "توان", "پیشران", "دوایکس", "نارنج اهرم",
        "کاریس", "سرو", "آگاس", "اطلس", "دارا یکم", "پالایش",
        "زیتون", "سپهر",
        "افران", "کمند", "کارا", "اعتماد", "یاقوت", "لبخند", "ثمر", "فیروزا"
    )

    /**
     * @param forceRefresh کش را دور می‌زند. رفرشِ دستیِ کاربر باید واقعاً درخواست
     *        بزند؛ محافظ backoff پس از HTTP 429 در هر حالت محترم شمرده می‌شود.
     */
    suspend fun analyzeAndRankFunds(
        portfolioItems: List<PortfolioEntity>,
        forceRefresh: Boolean = false
    ): List<FundRankingItem> = withContext(Dispatchers.IO) {
        // هر دو منبع در یک رفت‌وبرگشت گرفته می‌شوند
        val fundBaseMap = FundBaseApiService.fetchAllLiveFunds(forceRefresh)
        FundBaseApiService.fetchAllTechnicalStates(forceRefresh)
        FundBaseApiService.fetchAllReturns(forceRefresh)
        // بعد از داده زنده صدا زده می‌شود، چون دسته‌ها را از همان برمی‌دارد.
        FundBaseApiService.fetchAllConsistency(forceRefresh)

        // سن و زمانِ واقعیِ آخرین دریافت موفق — نه ساعتِ همین لحظه.
        // اگر درخواست شکست خورده باشد، این‌ها همان مقادیر دریافت قبلی می‌مانند
        // و ردیف‌ها به‌درستی «کش» برچسب می‌خورند.
        val marketDataAgeMs = FundBaseApiService.marketDataAgeMs
        val marketDataTimestamp = FundBaseApiService.lastFetchTimestamp

        val userSymbols = portfolioItems.map { it.symbol.trim() }.toSet()

        // اگر یک نماد چند ردیف داشته باشد (مثلاً یک‌بار دستی و یک‌بار از اکسل)،
        // ارزش‌ها باید **جمع** شوند. نسخه قبلی از associate استفاده می‌کرد که
        // فقط آخرین ردیف را نگه می‌داشت و بقیه را بی‌صدا دور می‌ریخت، پس
        // «ارزش در سبد شما» در دیده‌بان کمتر از واقعیت نشان داده می‌شد.
        val portfolioValueMap = portfolioItems
            .groupBy { it.symbol.trim() }
            .mapValues { (_, rows) -> rows.sumOf { it.currentValue } }

        // برای دسته، ردیف با بیشترین ارزش ملاک است — یک انتخاب قطعی، نه
        // «هرکدام که آخر آمد».
        val portfolioCategoryMap = portfolioItems
            .groupBy { it.symbol.trim() }
            .mapValues { (_, rows) -> rows.maxBy { it.currentValue }.assetCategory }

        val candidateSymbols = LinkedHashSet<String>()
        if (fundBaseMap.isNotEmpty()) candidateSymbols.addAll(fundBaseMap.keys)
        candidateSymbols.addAll(userSymbols)
        candidateSymbols.addAll(benchmarkSymbols)

        val evaluated = candidateSymbols.map { symbol ->
            evaluateFund(
                symbol = symbol,
                inPortfolio = userSymbols.contains(symbol),
                userValRial = portfolioValueMap[symbol] ?: 0.0,
                userCategory = portfolioCategoryMap[symbol],
                fundBaseData = FundBaseApiService.getCachedFund(symbol),
                technical = FundBaseApiService.getTechnicalState(symbol),
                marketDataAgeMs = marketDataAgeMs,
                marketDataTimestamp = marketDataTimestamp
            )
        }

        val ranked = calculateGroupRanks(evaluated)

        // چهار انتخاب برتر هر دسته، فقط از بین آن‌هایی که امتیاز واقعی دارند
        val top4 = mutableSetOf<String>()
        ranked.groupBy { it.category }.forEach { (_, funds) ->
            funds.filter { it.compositeScore != null }
                .sortedByDescending { it.compositeScore }
                .take(4)
                .forEach { top4.add(it.symbol) }
        }

        ranked
            .map { if (top4.contains(it.symbol)) it.copy(isTop4PickInCategory = true) else it }
            .sortedWith(
                compareByDescending<FundRankingItem> { it.compositeScore ?: -1 }
                    .thenBy { it.symbol }
            )
    }

    private fun groupKeyOf(fund: FundRankingItem): String = when {
        fund.category == AssetCategory.GOLD -> "GOLD"
        fund.category == AssetCategory.STOCK && fund.isLeveraged -> "LEVERAGED"
        fund.category == AssetCategory.STOCK -> "STOCK"
        fund.category == AssetCategory.MIXED -> "MIXED"
        else -> "FIXED_INCOME"
    }

    private fun groupPersianName(key: String): String = when (key) {
        "GOLD" -> "صندوق‌های طلا"
        "LEVERAGED" -> "صندوق‌های اهرمی"
        "STOCK" -> "صندوق‌های سهامی"
        "MIXED" -> "صندوق‌های مختلط"
        else -> "صندوق‌های درآمد ثابت"
    }

    /**
     * رتبه‌بندی درون هر گروه هم‌رده.
     * نمادی که برای یک معیار داده ندارد، در آن معیار رتبه نمی‌گیرد (null می‌ماند)
     * و در مخرج «از N» هم شمرده نمی‌شود.
     */
    private fun calculateGroupRanks(items: List<FundRankingItem>): List<FundRankingItem> {
        val enriched = mutableMapOf<String, FundRankingItem>()

        items.groupBy { groupKeyOf(it) }.forEach { (groupKey, group) ->
            val groupName = groupPersianName(groupKey)

            // برای هر معیار فقط نمادهای دارای داده رتبه‌بندی می‌شوند
            fun <T : Comparable<T>> rankMap(
                selector: (FundRankingItem) -> T?,
                descending: Boolean = true
            ): Map<String, Int> {
                val withData = group.filter { selector(it) != null }
                val sorted = if (descending) {
                    withData.sortedByDescending { selector(it)!! }
                } else {
                    withData.sortedBy { selector(it)!! }
                }
                return sorted.mapIndexed { i, f -> f.symbol to (i + 1) }.toMap()
            }

            val momentumRanks = rankMap({ it.momentumScore })
            val liquidityRanks = rankMap({ it.liquidityToman ?: it.tradeValueToman })
            val bubbleRanks = rankMap({ it.bubblePercent }, descending = false)
            val netAssetRanks = rankMap({ it.netAssetToman })
            val ageRanks = rankMap({ it.establishedDate.ifBlank { null } }, descending = false)

            group.forEach { fund ->
                enriched[fund.symbol] = fund.copy(
                    categoryPeerCount = group.size,
                    categoryPersianName = groupName,
                    totalInGroup = group.size,
                    rankMomentum = momentumRanks[fund.symbol],
                    rankLiquidity = liquidityRanks[fund.symbol],
                    rankLowBubble = bubbleRanks[fund.symbol],
                    rankNetAsset = netAssetRanks[fund.symbol],
                    rankAge = ageRanks[fund.symbol]
                )
            }
        }

        return items.map { enriched[it.symbol] ?: it }
    }

    private fun evaluateFund(
        symbol: String,
        inPortfolio: Boolean,
        userValRial: Double,
        userCategory: AssetCategory?,
        fundBaseData: FundBaseLiveData?,
        technical: FundTechnicalState,
        marketDataAgeMs: Long?,
        marketDataTimestamp: String
    ): FundRankingItem {
        val category = when {
            fundBaseData?.categoryId != null ->
                FundBaseApiService.mapFundBaseCategory(fundBaseData.categoryId, symbol)
            userCategory != null -> userCategory
            else -> DefaultEtfDatabase.getCategoryForSymbol(symbol)
        }

        val isLeveraged = category == AssetCategory.STOCK &&
                FundBaseApiService.isFundBaseLeveraged(fundBaseData?.categoryId, symbol)

        val hasLive = fundBaseData != null && fundBaseData.currentPriceRial > 0.0
        val isFresh = hasLive && MarketDataFreshness.isFresh(marketDataAgeMs)

        // ---- داده بازار: فقط اگر واقعاً آمده باشد ----
        val marketPrice = if (hasLive) fundBaseData!!.currentPriceRial else null
        val nav = if (hasLive && fundBaseData!!.navRial > 0) fundBaseData.navRial else null
        val bubblePercent = when {
            fundBaseData != null && nav != null && marketPrice != null ->
                if (fundBaseData.bubblePercent != 0.0) fundBaseData.bubblePercent
                else ((marketPrice - nav) / nav) * 100.0
            else -> null
        }
        val tradeValueToman = if (hasLive && fundBaseData!!.tradingValueRial > 0) {
            fundBaseData.tradingValueRial / 10.0
        } else null
        val liquidityToman = if (hasLive && fundBaseData!!.liquidityRial > 0) {
            fundBaseData.liquidityRial / 10.0
        } else null
        val netAssetToman = if (hasLive && fundBaseData!!.netAssetRial > 0) {
            fundBaseData.netAssetRial / 10.0
        } else null

        // ---- توصیف حباب ----
        val (bubbleDesc, bubbleStatus) = when {
            bubblePercent == null -> "داده حباب در دسترس نیست" to null
            bubblePercent < -0.8 -> "تخفیف نسبت به NAV (${fmt1(abs(bubblePercent))}٪ زیر NAV)" to StatusLevel.EXCELLENT
            bubblePercent <= 0.5 -> "منطبق بر NAV (${fmt1(bubblePercent)}٪)" to StatusLevel.GOOD
            bubblePercent <= 2.2 -> "حباب ملایم (${fmt1(bubblePercent)}٪)" to StatusLevel.FAIR
            else -> "حباب بالا (${fmt1(bubblePercent)}٪)" to StatusLevel.RISKY
        }

        // ---- توصیف نقدشوندگی ----
        val liqBase = liquidityToman ?: tradeValueToman
        val liquidityDesc = when {
            liqBase == null -> "داده نقدشوندگی در دسترس نیست"
            liqBase >= 50_000_000_000.0 -> "نقدشوندگی ممتاز (${formatTomanCompact(liqBase)})"
            liqBase >= 15_000_000_000.0 -> "نقدشوندگی بالا (${formatTomanCompact(liqBase)})"
            liqBase >= 3_000_000_000.0 -> "نقدشوندگی متوسط (${formatTomanCompact(liqBase)})"
            else -> "نقدشوندگی محدود (${formatTomanCompact(liqBase)})"
        }

        // ---- توصیف روند: فقط از داده تکنیکال واقعی ----
        val (trendDesc, trendStatus) = buildTrendDescription(technical)

        val stabilityDesc = when (category) {
            AssetCategory.FIXED_INCOME -> "بافر نقدینگی سبد (مناسب استراحت سرمایه)"
            AssetCategory.GOLD -> "پوشش تورمی و همبستگی با انس جهانی"
            AssetCategory.STOCK -> if (isLeveraged) "نوسان تشدیدشده نسبت به شاخص کل" else "نوسان همگام با شاخص کل"
            AssetCategory.MIXED -> "ریسک تعدیل‌شده (EML معادل یک سوم سهامی)"
        }

        // ---- امتیاز کل: فقط از معیارهای واقعیِ موجود ----
        val compositeScore = computeCompositeScore(
            bubblePercent = bubblePercent,
            liquidityToman = liqBase,
            netAssetToman = netAssetToman,
            momentum = technical.momentumScore
        )

        val gradeTier = when {
            compositeScore == null -> "بدون داده کافی"
            compositeScore >= 84 -> "طلایی (A+)"
            compositeScore >= 70 -> "نقره‌ای (A)"
            compositeScore >= 52 -> "برنزی (B)"
            else -> "احتیاط (C)"
        }

        // ---- برچسب‌ها ----
        val tags = mutableListOf<String>()
        if (isLeveraged) tags.add("اهرمی")
        if (bubblePercent != null && bubblePercent < 0) tags.add("تخفیف زیر NAV")
        if (bubblePercent != null && bubblePercent > 3.0) tags.add("حباب بالا")
        if (liqBase != null && liqBase >= 25_000_000_000.0) tags.add("نقدشوندگی بالا")
        if (technical.hasData) {
            if (technical.trendMonthly.equals("up", true)) tags.add("روند ماهانه صعودی")
            technical.rsi14?.let { if (it > 70) tags.add("RSI اشباع خرید") else if (it < 30) tags.add("RSI اشباع فروش") }
        } else {
            tags.add("بدون داده تکنیکال")
        }
        if (category == AssetCategory.FIXED_INCOME) tags.add("بافر نقدینگی")
        if (category == AssetCategory.GOLD) tags.add("سپر تورمی")

        return FundRankingItem(
            symbol = symbol,
            fundName = if (isLeveraged) "صندوق اهرمی $symbol" else "صندوق $symbol",
            category = category,
            inUserPortfolio = inPortfolio,
            userPortfolioValueRial = userValRial,
            navPriceRial = nav,
            marketPriceRial = marketPrice,
            bubblePercent = bubblePercent,
            tradeValueToman = tradeValueToman,
            tradeVolume = if (hasLive && fundBaseData!!.tradingVolume > 0) fundBaseData.tradingVolume else null,
            priceChangePercent = if (hasLive) fundBaseData!!.priceChangePercent else null,
            liquidityToman = liquidityToman,
            netAssetToman = netAssetToman,
            establishedDate = fundBaseData?.establishedDate ?: "",
            managerName = fundBaseData?.manager ?: "",
            technical = technical,
            returns = FundBaseApiService.getReturnsForFund(fundBaseData?.fundId),
            fundId = fundBaseData?.fundId ?: "",
            consistency = FundBaseApiService.getConsistency(fundBaseData?.fundId),
            hasLiveMarketData = hasLive,
            isMarketDataFresh = isFresh,
            dataSourceName = MarketDataFreshness.sourceLabel(hasLive, marketDataAgeMs),
            lastUpdatedTime = if (hasLive) marketDataTimestamp else "",
            isLeveraged = isLeveraged,
            bubbleTierDescription = bubbleDesc,
            bubbleStatus = bubbleStatus,
            liquidityDescription = liquidityDesc,
            trendDescription = trendDesc,
            trendStatus = trendStatus,
            stabilityDescription = stabilityDesc,
            compositeScore = compositeScore,
            gradeTier = gradeTier,
            descriptiveTags = tags,
            recommendationReason = buildRecommendationReason(category, bubblePercent, technical)
        )
    }

    /**
     * امتیاز کل فقط از معیارهایی که داده دارند ساخته می‌شود و به مقیاس ۰..۱۰۰
     * نرمال می‌گردد. اگر هیچ معیاری داده نداشته باشد، null برمی‌گردد.
     */
    private fun computeCompositeScore(
        bubblePercent: Double?,
        liquidityToman: Double?,
        netAssetToman: Double?,
        momentum: Int?
    ): Int? {
        var earned = 0.0
        var available = 0.0

        if (bubblePercent != null) {
            available += 35.0
            earned += when {
                bubblePercent < -1.0 -> 35.0
                bubblePercent < 0.0 -> 30.0
                bubblePercent < 1.0 -> 22.0
                bubblePercent < 2.5 -> 12.0
                else -> 2.0
            }
        }

        if (liquidityToman != null) {
            available += 25.0
            earned += when {
                liquidityToman >= 40_000_000_000.0 -> 25.0
                liquidityToman >= 15_000_000_000.0 -> 20.0
                liquidityToman >= 5_000_000_000.0 -> 14.0
                else -> 6.0
            }
        }

        if (netAssetToman != null) {
            available += 15.0
            earned += when {
                netAssetToman >= 5_000_000_000_000.0 -> 15.0
                netAssetToman >= 1_000_000_000_000.0 -> 11.0
                netAssetToman >= 200_000_000_000.0 -> 7.0
                else -> 3.0
            }
        }

        if (momentum != null) {
            available += 25.0
            earned += (momentum / 100.0) * 25.0
        }

        if (available < 25.0) return null // داده بسیار ناقص: امتیاز نمی‌دهیم
        return ((earned / available) * 100.0).toInt().coerceIn(0, 100)
    }

    private fun buildTrendDescription(t: FundTechnicalState): Pair<String, StatusLevel?> {
        if (!t.hasData) return "داده تکنیکال در دسترس نیست" to null

        val m = t.momentumScore ?: return "داده تکنیکال ناقص" to null
        val label = buildString {
            append("روند ماهانه ${FundTechnicalInsight.trendLabel(t.trendMonthly)}")
            append(" / هفتگی ${FundTechnicalInsight.trendLabel(t.trendWeekly)}")
            t.priceVsSma200Pct?.let { append(" — ${fmt1(it)}٪ نسبت به SMA۲۰۰") }
        }

        val status = when {
            m >= 75 -> StatusLevel.EXCELLENT
            m >= 60 -> StatusLevel.GOOD
            m >= 45 -> StatusLevel.FAIR
            else -> StatusLevel.RISKY
        }
        return label to status
    }

    private fun buildRecommendationReason(
        category: AssetCategory,
        bubble: Double?,
        t: FundTechnicalState
    ): String {
        if (bubble == null && !t.hasData) {
            return "برای این نماد داده کافی از FundBase دریافت نشد؛ قبل از تصمیم‌گیری آن را مستقیماً بررسی کنید."
        }

        val bubblePart = when {
            bubble == null -> ""
            bubble < 0.0 -> "با تخفیف نسبت به NAV معامله می‌شود. "
            bubble > 2.5 -> "حباب قیمتی قابل توجهی دارد؛ خرید در اصلاح منطقی‌تر است. "
            else -> "حباب در محدوده متعارف است. "
        }

        val trendPart = when {
            !t.hasData -> "داده تکنیکال برای این نماد موجود نیست."
            t.trendMonthly.equals("up", true) -> "روند ماهانه صعودی است."
            t.trendMonthly.equals("down", true) -> "روند ماهانه نزولی است؛ احتیاط کنید."
            else -> "روند ماهانه خنثی است."
        }

        val categoryPart = when (category) {
            AssetCategory.FIXED_INCOME -> " مناسب نقش بافر نقدینگی در ریبلنس."
            AssetCategory.GOLD -> " مناسب سهم طلای سبد."
            AssetCategory.MIXED -> " مناسب تعدیل نوسان کل سبد."
            AssetCategory.STOCK -> " مناسب پر کردن ظرفیت سهامی سبد."
        }

        return bubblePart + trendPart + categoryPart
    }

    private fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)

    /**
     * قالب‌بندی مبلغ تومانی.
     * اصلاح باگ: نسخه قبلی هر مقدار بالای یک میلیارد تومان را «همت» می‌نامید،
     * در حالی که ۱ همت = ۱۰۰۰ میلیارد تومان (خطای ۱۰۰۰ برابری).
     */
    fun formatTomanCompact(toman: Double): String {
        val trillion = 1_000_000_000_000.0   // ۱ همت
        val billion = 1_000_000_000.0        // ۱ میلیارد تومان
        val million = 1_000_000.0
        return when {
            toman >= trillion -> "${String.format(Locale.US, "%.2f", toman / trillion)} همت"
            toman >= billion -> "${String.format(Locale.US, "%.1f", toman / billion)} میلیارد تومان"
            toman >= million -> "${String.format(Locale.US, "%.0f", toman / million)} میلیون تومان"
            else -> "${String.format(Locale.US, "%,.0f", toman)} تومان"
        }
    }
}
