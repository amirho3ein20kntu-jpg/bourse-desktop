package com.example.data.model

/**
 * Asset classes based on PCMR methodology:
 * - GOLD (G): طلا (Gold ETFs: طلا، عیار، کهربا، زرفام، گوهر، زر...)
 * - STOCK (S): سهامی (Stock ETFs: کلاسیک، شاخصی، بخشی، اهرمی: آگاس، اهرم، کاریس، سرو، موج...)
 * - MIXED (M): مختلط (Mixed ETFs: زیتون...)
 * - FIXED_INCOME (F): درآمد ثابت (Fixed Income ETFs: کمند، افران، کارا، اعتماد...)
 */
enum class AssetCategory(
    val code: String,
    val persianName: String,
    val baseWeightMultiplier: Double // Base multiplier used in PCMR formula (30 for Gold, 60 for Stock, 10 for Mixed)
) {
    GOLD("G", "صندوق‌های طلا", 30.0),
    STOCK("S", "صندوق‌های سهامی و اهرمی", 60.0),
    MIXED("M", "صندوق‌های مختلط", 10.0),
    FIXED_INCOME("F", "صندوق‌های درآمد ثابت", 0.0);

    companion object {
        /**
         * دسته را از متن تشخیص می‌دهد، یا `null` اگر متن هیچ نشانه‌ای نداشته باشد.
         *
         * وقتی فایل دسته‌بندی کارگزاری یا کاربر وارد می‌شود، «نشناختم» باید از
         * «سهامی» قابل تفکیک باشد: این جدول بر حدس‌های داخلی اپ اولویت دارد، پس
         * یک ردیفِ ناخوانا که سهامی فرض شود، دستهٔ درستِ یک صندوق درآمد ثابت را
         * بازنویسی می‌کند و وزن‌های هدف را جابه‌جا می‌کند.
         */
        fun fromStringOrNull(value: String): AssetCategory? {
            val normalized = value.trim().lowercase()
            if (normalized.isEmpty()) return null
            return when {
                normalized.contains("طلا") || normalized == "g" || normalized.contains("gold") -> GOLD
                normalized.contains("سهام") || normalized.contains("اهرم") || normalized == "s" || normalized.contains("stock") -> STOCK
                normalized.contains("مختلط") || normalized == "m" || normalized.contains("mixed") -> MIXED
                normalized.contains("ثابت") || normalized.contains("درآمد") || normalized == "f" || normalized.contains("fixed") -> FIXED_INCOME
                else -> null
            }
        }

        /** مثل [fromStringOrNull] ولی برای متن ناشناخته «سهامی» برمی‌گرداند. */
        fun fromString(value: String): AssetCategory = fromStringOrNull(value) ?: STOCK
    }
}

enum class TimeHorizon(val months: Int, val persianTitle: String) {
    THREE_MONTHS(3, "۳ ماهه"),
    SIX_MONTHS(6, "۶ ماهه"),
    TWELVE_MONTHS(12, "۱۲ ماهه");

    companion object {
        fun fromMonths(months: Int): TimeHorizon {
            return entries.find { it.months == months } ?: SIX_MONTHS
        }
    }
}
