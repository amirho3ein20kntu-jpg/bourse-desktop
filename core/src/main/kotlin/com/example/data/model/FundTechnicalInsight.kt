package com.example.data.model

/**
 * وضعیت تکنیکال واقعی یک نماد — همه فیلدها مستقیماً از جدول `ta_symbol_state`
 * در FundBase خوانده می‌شوند. هیچ مقداری در این کلاس ساخته یا تخمین زده نمی‌شود.
 *
 * اگر نمادی در آن جدول نباشد، `hasData = false` می‌ماند و UI باید
 * «داده در دسترس نیست» نشان دهد — نه عدد جایگزین.
 */
data class FundTechnicalState(
    val symbol: String,
    val hasData: Boolean = false,
    val lastPrice: Double? = null,
    /** تاریخ آخرین داده (میلادی) */
    val lastDate: String = "",
    val trendDaily: String = "",
    val trendWeekly: String = "",
    val trendMonthly: String = "",
    val rsi14: Double? = null,
    /** فاصله قیمت از میانگین متحرک ۵۰ روزه، بر حسب درصد */
    val priceVsSma50Pct: Double? = null,
    /** فاصله قیمت از میانگین متحرک ۲۰۰ روزه، بر حسب درصد */
    val priceVsSma200Pct: Double? = null,
    val nearestSupport: Double? = null,
    val nearestSupportDistPct: Double? = null,
    val nearestResistance: Double? = null,
    val nearestResistanceDistPct: Double? = null,
    /** برچسب وضعیت که خود FundBase تولید کرده، مثل «روند صعودی / RSI اشباع خرید» */
    val stateLabel: String = "",
    val lastSignalDirection: String = "",
    val lastSignalStrength: Int? = null,
    val lastSignalType: String = ""
) {
    /**
     * امتیاز مومنتوم ۰ تا ۱۰۰، فقط از روی داده واقعی.
     * وقتی داده‌ای نباشد null برمی‌گردد — جایگزین ساختگی تولید نمی‌شود.
     */
    val momentumScore: Int?
        get() {
            if (!hasData) return null
            var s = 50
            s += when (trendMonthly.lowercase()) { "up" -> 15; "down" -> -15; else -> 0 }
            s += when (trendWeekly.lowercase()) { "up" -> 10; "down" -> -10; else -> 0 }
            s += when (trendDaily.lowercase()) { "up" -> 5; "down" -> -5; else -> 0 }
            priceVsSma200Pct?.let {
                s += when {
                    it > 50.0 -> 15
                    it > 20.0 -> 10
                    it > 0.0 -> 5
                    else -> -10
                }
            }
            rsi14?.let {
                s += when {
                    it > 75.0 -> -8   // اشباع خرید: احتمال استراحت
                    it < 35.0 -> 5    // اشباع فروش: پتانسیل بازگشت
                    else -> 0
                }
            }
            return s.coerceIn(0, 100)
        }

    val rsiLabel: String
        get() = when {
            rsi14 == null -> "—"
            rsi14 > 70.0 -> "اشباع خرید"
            rsi14 < 30.0 -> "اشباع فروش"
            else -> "نرمال"
        }
}

/**
 * جمع‌بندی تحلیلی FundBase (جدول `ta_ai_interpretations`) به‌همراه روندها.
 */
data class FundTechnicalInsight(
    val symbol: String,
    val trendDaily: String = "side",
    val trendWeekly: String = "side",
    val trendMonthly: String = "side",
    val rsiDaily: Double? = null,
    val summary: String = "",
    val isLiveApi: Boolean = false,
    val isLoading: Boolean = false,
    val lastUpdated: String = ""
) {
    val isDailyBullish: Boolean get() = trendDaily.equals("up", ignoreCase = true)
    val isDailyBearish: Boolean get() = trendDaily.equals("down", ignoreCase = true)

    val isWeeklyBullish: Boolean get() = trendWeekly.equals("up", ignoreCase = true)
    val isWeeklyBearish: Boolean get() = trendWeekly.equals("down", ignoreCase = true)

    val isMonthlyBullish: Boolean get() = trendMonthly.equals("up", ignoreCase = true)
    val isMonthlyBearish: Boolean get() = trendMonthly.equals("down", ignoreCase = true)

    val dailyLabel: String get() = trendLabel(trendDaily)
    val weeklyLabel: String get() = trendLabel(trendWeekly)
    val monthlyLabel: String get() = trendLabel(trendMonthly)

    companion object {
        fun trendLabel(trend: String): String = when (trend.lowercase()) {
            "up" -> "صعودی"
            "down" -> "نزولی"
            "side" -> "خنثی"
            else -> "—"
        }
    }
}
