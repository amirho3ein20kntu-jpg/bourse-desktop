package com.example.data.model

/**
 * بازدهی دوره‌ای یک صندوق — مستقیماً از جدول `fund_return_data` در FundBase.
 *
 * **هیچ مقداری اینجا ساخته یا تخمین زده نمی‌شود.** فیلدی که API `null` بدهد
 * `null` می‌ماند و UI موظف است «—» نشان دهد، نه صفر. صفر یعنی «بازدهی نداشت»
 * که ادعای متفاوتی است از «نمی‌دانیم».
 *
 * در نمونه‌های واقعی، `return_daily` و `return_weekly` اغلب خالی‌اند.
 */
data class FundReturns(
    /** کلید اتصال به `fund_live_data.fund_id` */
    val fundId: String,

    /** تاریخ میلادی محاسبه، مثل 2026-09-15 */
    val date: String = "",

    /** همان تاریخ به شمسی، مستقیماً از API — خودمان تبدیل نمی‌کنیم */
    val jalaliDate: String = "",

    val daily: Double? = null,
    val weekly: Double? = null,
    val monthly: Double? = null,
    val quarterly: Double? = null,
    /** شش‌ماهه */
    val biannual: Double? = null,
    val yearly: Double? = null
) {
    /** true یعنی حداقل یکی از بازه‌ها عدد دارد و نمایش این ردیف ارزش دارد. */
    val hasAnyReturn: Boolean
        get() = listOf(daily, weekly, monthly, quarterly, biannual, yearly).any { it != null }
}

/** بازه‌های زمانی جدول مقایسه، به همان ترتیبی که نمایش داده می‌شوند. */
enum class ReturnPeriod(val persianLabel: String, val shortLabel: String) {
    MONTHLY("یک ماهه", "۱م"),
    QUARTERLY("سه ماهه", "۳م"),
    BIANNUAL("شش ماهه", "۶م"),
    YEARLY("سالانه", "۱س");

    fun valueOf(returns: FundReturns?): Double? = when (this) {
        MONTHLY -> returns?.monthly
        QUARTERLY -> returns?.quarterly
        BIANNUAL -> returns?.biannual
        YEARLY -> returns?.yearly
    }
}
