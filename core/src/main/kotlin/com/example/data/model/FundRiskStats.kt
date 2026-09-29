package com.example.data.model

/**
 * آمار ریسک یک صندوق در یک بازه زمانی — از `rpc/get_fund_risk_stats` در
 * FundBase. هیچ مقداری اینجا ساخته یا تخمین زده نمی‌شود؛ فیلدی که API
 * نداده `null` می‌ماند و UI باید «—» نشان دهد.
 *
 * **واحدها:** API نوسان و افت را **کسری** می‌دهد (`-0.2245`)، نه درصد.
 * تبدیل به درصد سر مرز شبکه انجام می‌شود و این کلاس همیشه درصد نگه می‌دارد،
 * تا هیچ‌جای دیگری لازم نباشد یادش بماند در ۱۰۰ ضرب کند. نسبت شارپ نسبت است
 * و اصلاً تبدیل نمی‌خواهد — ضرب‌کردنش عدد را صدبرابر می‌کرد.
 */
data class FundRiskStats(
    val fundId: String,

    /** بازه‌ای که آمار برایش خواسته شده (پیش‌فرض FundBase: ۹۰ روز). */
    val windowDays: Int = DEFAULT_WINDOW_DAYS,

    /**
     * تعداد روزهای معاملاتی که واقعاً در محاسبه آمده‌اند.
     *
     * با [windowDays] یکی نیست: برای پنجره ۹۰ روزه، ۸۹ نمونه چیز عادی است،
     * ولی ۱۲ نمونه یعنی صندوق تازه باز شده و آمارش هنوز معنای زیادی ندارد.
     */
    val sampleSize: Int? = null,

    /**
     * حداکثر افت: بزرگ‌ترین ریزش از سقف تا کفِ بعدی در این بازه، **بر حسب
     * درصد** و با علامت منفی (مثلاً ‎−۲۲.۴۵‎).
     */
    val maxDrawdownPct: Double? = null,

    /** نسبت شارپ: بازدهی مازاد به ازای هر واحد ریسک. بدون واحد. */
    val sharpeRatio: Double? = null,

    /** نوسان سالانه‌شده، **بر حسب درصد**. */
    val annualVolatilityPct: Double? = null
) {
    val hasAnyStat: Boolean
        get() = maxDrawdownPct != null || sharpeRatio != null || annualVolatilityPct != null

    /**
     * true یعنی نمونه‌ها آن‌قدر کم‌اند که عددها را باید با احتیاط خواند.
     * `null` بودن تعداد نمونه، «کم» حساب نمی‌شود — نادانسته است، نه کم.
     */
    val isSampleThin: Boolean
        get() = sampleSize != null && sampleSize < MIN_RELIABLE_SAMPLE

    companion object {
        const val DEFAULT_WINDOW_DAYS: Int = 90

        /**
         * زیر این تعداد روز معاملاتی، آمار ریسک را «کم‌نمونه» می‌خوانیم.
         * حدود یک ماه معاملاتی — کمتر از آن، یک هفته پرنوسان کل عدد را
         * تعیین می‌کند.
         */
        const val MIN_RELIABLE_SAMPLE: Int = 20
    }
}
