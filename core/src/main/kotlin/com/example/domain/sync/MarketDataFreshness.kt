package com.example.domain.sync

/**
 * تازگی داده بازار — بدون وابستگی به اندروید یا شبکه، تا قابل تست باشد.
 *
 * این منطق جای باگی را گرفته که هر ردیف دیده‌بان را با **ساعتِ همان لحظه** و
 * برچسب «زنده» نشان می‌داد، حتی وقتی درخواست شبکه شکست خورده بود و داده از کش
 * چند ساعت پیش می‌آمد. در اپی که کاربر بر اساس آن خرید و فروش می‌کند، کهنه بودن
 * داده باید صریح اعلام شود.
 */
object MarketDataFreshness {

    /** تا این سن، داده «زنده» حساب می‌شود. کش بازار ۱۵ ثانیه‌ای است. */
    const val FRESH_LIMIT_MS = 120_000L

    /** null یعنی هنوز هیچ دریافت موفقی انجام نشده. */
    fun isFresh(ageMs: Long?): Boolean = ageMs != null && ageMs in 0..FRESH_LIMIT_MS

    /**
     * برچسب منبع داده که کنار هر نماد نمایش داده می‌شود.
     *
     * @param hasLiveData آیا اصلاً داده‌ای برای این نماد وجود دارد.
     * @param ageMs فاصله زمانی تا آخرین دریافت موفق از API.
     */
    fun sourceLabel(hasLiveData: Boolean, ageMs: Long?): String = when {
        !hasLiveData -> "بدون داده"
        isFresh(ageMs) -> "FundBase (زنده)"
        ageMs == null -> "FundBase (زمان نامشخص)"
        else -> "FundBase (کش — ${describeAge(ageMs)})"
    }

    /** توصیف سن داده به فارسی: «۳ دقیقه پیش»، «۲ ساعت پیش». */
    fun describeAge(ageMs: Long): String {
        val safeAge = if (ageMs < 0L) 0L else ageMs
        val minutes = safeAge / 60_000L
        val hours = minutes / 60L
        return when {
            minutes < 1L -> "کمتر از یک دقیقه پیش"
            minutes < 60L -> "$minutes دقیقه پیش"
            hours < 24L -> "$hours ساعت پیش"
            else -> "${hours / 24L} روز پیش"
        }
    }
}
