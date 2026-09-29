package com.example.data.model

import kotlin.math.roundToInt

/**
 * امتیاز «تداوم بازدهی» یک صندوق — **مستقیم از** `rpc/get_fund_return_consistency`
 * در FundBase خوانده می‌شود، نه محاسبه‌شده.
 *
 * نسخه قبلی این عدد را خودش حساب می‌کرد، بر اساس تعریفی که FundBase در ⓘ
 * ستون نوشته بود: «میانگین رتبه سه بازه ۳، ۶ و ۱۲ ماهه». آن محاسبه درست
 * پیاده شده بود ولی **با عددهای خود FundBase نمی‌خواند** — مثلاً «دوایکس»
 * که در هر سه بازه رتبه اول است طبق آن تعریف باید ۱۰۰ می‌گرفت، ولی سایت به
 * آن ۵۰ داده بود. یعنی متن آن توضیح، کاری که خودشان می‌کنند را توصیف
 * نمی‌کند. حالا که تابع واقعی پیدا شده، هیچ دلیلی برای بازسازی حدسی‌اش
 * نمانده.
 */
data class FundConsistency(
    val fundId: String,

    /** امتیاز ۰ تا ۱۰۰. FundBase آن را با یک رقم اعشار می‌دهد (مثلاً ۸۰.۵). */
    val score: Double,

    /** رتبه در دسته؛ ۱ یعنی بهترین. */
    val rankInCategory: Int? = null,

    /** تعداد کل صندوق‌های واجد شرط در آن دسته. */
    val totalFunds: Int? = null,

    /**
     * تعداد روز سابقه‌ای که امتیاز روی آن حساب شده.
     *
     * FundBase با `p_min_days = 30` صدا می‌زند، یعنی صندوق زیر ۳۰ روز اصلاً
     * امتیاز نمی‌گیرد. ولی ۳۵ روز با ۲۲۶ روز خیلی فرق دارد، و کاربر باید
     * بداند امتیاز روی چه مقدار تاریخچه‌ای بنا شده.
     */
    val sampleDays: Int? = null
) {
    /** همان عددی که سایت نشان می‌دهد — گرد شده. */
    val displayScore: Int get() = score.roundToInt().coerceIn(0, 100)

    /** «۳ از ۹»، یا null اگر رتبه نیامده باشد. */
    val rankLabel: String?
        get() {
            val rank = rankInCategory ?: return null
            val total = totalFunds ?: return null
            return "$rank از $total"
        }

    /**
     * برچسب کیفی. مرزها از روی رنگ‌بندی خود FundBase انتخاب شده‌اند: در
     * نمونه‌ای که دیدیم ۸۱ سبز پررنگ بود، ۵۰ تا ۶۳ سبز کم‌رنگ، ۴۰ و ۴۳ زرد،
     * و ۲۲ و ۲۸ نارنجی.
     */
    val label: String
        get() = when {
            displayScore >= 70 -> "پایدار"
            displayScore >= 50 -> "نسبتاً پایدار"
            displayScore >= 30 -> "متوسط"
            else -> "ناپایدار"
        }

    /**
     * true یعنی سابقه صندوق آن‌قدر کوتاه است که امتیاز را باید با احتیاط
     * خواند. ناداشتن [sampleDays] «کوتاه» حساب نمی‌شود — نادانسته است.
     */
    val isHistoryShort: Boolean
        get() = sampleDays != null && sampleDays < SHORT_HISTORY_DAYS

    companion object {
        /**
         * زیر این تعداد روز، امتیاز روی تاریخچه‌ای کوتاه‌تر از یک فصل بنا
         * شده و با صندوقی که دو سال سابقه دارد هم‌وزن نیست.
         */
        const val SHORT_HISTORY_DAYS: Int = 90
    }
}
