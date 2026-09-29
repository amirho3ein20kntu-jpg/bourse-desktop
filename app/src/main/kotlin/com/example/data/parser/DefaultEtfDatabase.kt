package com.example.data.parser

import com.example.data.model.AssetCategory
import com.example.data.model.FundCategoryEntity

object DefaultEtfDatabase {
    val defaultFundMappings: Map<String, AssetCategory> = mapOf(
        // Gold (طلا و مسکوکات طلا)
        "طلا" to AssetCategory.GOLD,
        "عیار" to AssetCategory.GOLD,
        "کهربا" to AssetCategory.GOLD,
        "زرفام" to AssetCategory.GOLD,
        "گوهر" to AssetCategory.GOLD,
        "زر" to AssetCategory.GOLD,
        "ناب" to AssetCategory.GOLD,
        "زرد" to AssetCategory.GOLD,
        "تابش" to AssetCategory.GOLD,
        "سحرخیز" to AssetCategory.GOLD,
        "جواهر" to AssetCategory.GOLD,
        "درخشان" to AssetCategory.GOLD,
        "آلتون" to AssetCategory.GOLD,
        "گنج" to AssetCategory.GOLD,
        "نفیس" to AssetCategory.GOLD,
        "خزان" to AssetCategory.GOLD,
        "تابان" to AssetCategory.GOLD,
        "متین" to AssetCategory.GOLD,
        "پیروز" to AssetCategory.GOLD,
        "مرجان" to AssetCategory.GOLD,
        "زرفشان" to AssetCategory.GOLD,

        // Stock & Leveraged (سهامی و اهرمی)
        "اهرم" to AssetCategory.STOCK,
        "شتاب" to AssetCategory.STOCK,
        "موج" to AssetCategory.STOCK,
        "جهش" to AssetCategory.STOCK,
        "توان" to AssetCategory.STOCK,
        "نارنج" to AssetCategory.STOCK,
        "نارنج اهرم" to AssetCategory.STOCK,
        "بیدار" to AssetCategory.STOCK,
        "پیشران" to AssetCategory.STOCK,
        "دوایکس" to AssetCategory.STOCK,
        "آرامش" to AssetCategory.STOCK,
        "آگاس" to AssetCategory.STOCK,
        "سرو" to AssetCategory.STOCK,
        "کاریس" to AssetCategory.STOCK,
        "اطلس" to AssetCategory.STOCK,
        "دارایکم" to AssetCategory.STOCK,
        "پالایش" to AssetCategory.STOCK,
        "آرام" to AssetCategory.STOCK,
        "ثروتم" to AssetCategory.STOCK,
        "انار" to AssetCategory.STOCK,
        "پیشگام" to AssetCategory.STOCK,
        "بهین" to AssetCategory.STOCK,
        "صنم" to AssetCategory.STOCK,
        "آوا" to AssetCategory.STOCK,
        "فراز" to AssetCategory.STOCK,
        "ثمین" to AssetCategory.STOCK,
        "تمشک" to AssetCategory.STOCK,
        "همای" to AssetCategory.STOCK,

        // Mixed (مختلط)
        "زیتون" to AssetCategory.MIXED,
        "سپهر" to AssetCategory.MIXED,
        "تجربه" to AssetCategory.MIXED,
        "آرمان" to AssetCategory.MIXED,
        "الماس" to AssetCategory.MIXED,
        "نیکی" to AssetCategory.MIXED,
        "زاگرس" to AssetCategory.MIXED,

        // Fixed Income (درآمد ثابت و بافر نقدینگی)
        "کمند" to AssetCategory.FIXED_INCOME,
        "افران" to AssetCategory.FIXED_INCOME,
        "کارا" to AssetCategory.FIXED_INCOME,
        "اعتماد" to AssetCategory.FIXED_INCOME,
        "حامی" to AssetCategory.FIXED_INCOME,
        "یاقوت" to AssetCategory.FIXED_INCOME,
        "لبخند" to AssetCategory.FIXED_INCOME,
        "اونیکس" to AssetCategory.FIXED_INCOME,
        "سپر" to AssetCategory.FIXED_INCOME,
        "صایند" to AssetCategory.FIXED_INCOME,
        "گنجینه" to AssetCategory.FIXED_INCOME,
        "پاداش" to AssetCategory.FIXED_INCOME,
        "کیان" to AssetCategory.FIXED_INCOME,
        "امین یکم" to AssetCategory.FIXED_INCOME,
        "خاتم" to AssetCategory.FIXED_INCOME,
        "باران" to AssetCategory.FIXED_INCOME,
        "پارند" to AssetCategory.FIXED_INCOME,
        "کاردان" to AssetCategory.FIXED_INCOME,
        "ثبات" to AssetCategory.FIXED_INCOME,
        "ماهان" to AssetCategory.FIXED_INCOME,
        "ترنج" to AssetCategory.FIXED_INCOME
    )

    val knownLeveragedFunds: Set<String> = setOf(
        "اهرم", "شتاب", "موج", "جهش", "توان", "نارنج", "نارنج اهرم", "بیدار", "پیشران", "دوایکس"
    )

    fun isLeveragedFund(symbol: String): Boolean {
        val clean = symbol.trim().replace(" ", "").replace("‌", "")
        if (clean.contains("اهرم") || clean.contains("دوایکس") || clean.contains("پیشران")) return true
        for (lev in knownLeveragedFunds) {
            val cleanLev = lev.trim().replace(" ", "").replace("‌", "")
            if (clean == cleanLev || clean.contains(cleanLev)) return true
        }
        return false
    }

    /**
     * از کجا این دسته آمده. هر چه پایین‌تر، غیرقابل‌اتکاتر.
     *
     * دسته‌بندی اشتباه بی‌ضرر نیست: کل تخصیص وزن طبقات و در نتیجه سفارش‌های
     * خرید/فروش را جابه‌جا می‌کند. پس هر تطبیقی که «حدس» است باید قابل تشخیص
     * باشد تا UI بتواند از کاربر تأیید بگیرد.
     */
    enum class MatchSource {
        /** نام نماد دقیقاً در جدول صندوق‌ها بود. */
        EXACT,

        /** خود نام کلیدواژه‌ای صریح داشت («طلا»، «اهرم»، «مختلط»، «ثابت»...). */
        KEYWORD,

        /** تطبیق جزئی با یک نام شناخته‌شده — ممکن است اشتباه باشد. */
        FUZZY,

        /** هیچ تطبیقی نبود و «سهامی» فرض شد. */
        FALLBACK
    }

    data class CategoryMatch(
        val category: AssetCategory,
        val source: MatchSource
    ) {
        /** true یعنی این دسته حدس است و باید از کاربر تأیید گرفته شود. */
        val isGuess: Boolean
            get() = source == MatchSource.FUZZY || source == MatchSource.FALLBACK
    }

    /**
     * دسته نماد را به‌همراه اینکه از کجا آمده برمی‌گرداند.
     * برای وقتی که فقط خود دسته لازم است، [getCategoryForSymbol] را صدا بزنید.
     */
    fun matchCategoryForSymbol(symbol: String): CategoryMatch {
        val clean = normalize(symbol)
        if (clean.isEmpty()) return CategoryMatch(AssetCategory.STOCK, MatchSource.FALLBACK)

        // 1. Exact direct key match first
        for ((key, category) in defaultFundMappings) {
            if (clean == normalize(key)) {
                return CategoryMatch(category, MatchSource.EXACT)
            }
        }

        // 2. Keyword heuristic checks
        if (clean.contains("طلا") || clean.contains("سکه") || clean.contains("شمش")) {
            return CategoryMatch(AssetCategory.GOLD, MatchSource.KEYWORD)
        }
        if (clean.contains("اهرم") || clean.contains("سهام") || clean.contains("شاخص")) {
            return CategoryMatch(AssetCategory.STOCK, MatchSource.KEYWORD)
        }
        if (clean.contains("مختلط")) {
            return CategoryMatch(AssetCategory.MIXED, MatchSource.KEYWORD)
        }
        if (clean.contains("ثابت") || clean.contains("اوراق") || clean.contains("مرابحه") || clean.contains("بافر")) {
            return CategoryMatch(AssetCategory.FIXED_INCOME, MatchSource.KEYWORD)
        }

        // 3. Multi-character submatch for distinct fund names (length >= 3)
        for ((key, category) in defaultFundMappings) {
            val cleanKey = normalize(key)
            if (cleanKey.length >= 3 && (clean.contains(cleanKey) || cleanKey.contains(clean))) {
                return CategoryMatch(category, MatchSource.FUZZY)
            }
        }

        // 4. Short key match (e.g., "زر", "ناب")
        for ((key, category) in defaultFundMappings) {
            val cleanKey = normalize(key)
            if (clean == cleanKey || (clean.startsWith(cleanKey) && clean.length <= 4)) {
                return CategoryMatch(category, MatchSource.FUZZY)
            }
        }

        return CategoryMatch(AssetCategory.STOCK, MatchSource.FALLBACK)
    }

    fun getCategoryForSymbol(symbol: String): AssetCategory =
        matchCategoryForSymbol(symbol).category

    private fun normalize(text: String): String =
        text.trim().replace(" ", "").replace("‌", "")

    fun toEntities(): List<FundCategoryEntity> {
        return defaultFundMappings.map { (sym, cat) ->
            FundCategoryEntity(symbol = sym, category = cat, fundName = "صندوق $sym")
        }
    }
}
