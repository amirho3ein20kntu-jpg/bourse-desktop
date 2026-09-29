package com.example.domain.portfolio

import com.example.data.model.PortfolioEntity

/**
 * سود و زیان سبد — بدون وابستگی به اندروید، تا قابل تست باشد.
 *
 * این محاسبه قبلاً سه جای مختلف تکرار شده بود (داشبورد، صفحه واریز و برداشت،
 * و ثبت تاریخچه) و هر سه یک اشکال مشترک داشتند: **صورت و مخرج روی یک مجموعه
 * ردیف بسته نمی‌شد.** ارزش روز از همه ردیف‌ها جمع می‌شد ولی اصل سرمایه فقط از
 * ردیف‌هایی عدد می‌گرفت که تعداد واحد و قیمت خرید داشتند. نتیجه این بود که یک
 * ردیفِ بدون قیمت خرید، تمام ارزشش به‌عنوان «سود» گزارش می‌شد.
 *
 * قاعده اینجا ساده است: ردیفی که مبنای خرید ندارد **از هر دو طرف** کنار
 * می‌رود، و [uncoveredValueRial] می‌گوید چقدر از سبد اصلاً وارد محاسبه نشده تا
 * UI بتواند صادقانه نشانش دهد.
 */
data class PortfolioPnl(
    /** ارزش روز کل سبد، شامل ردیف‌های بدون مبنای خرید. */
    val totalValueRial: Double,
    /** ارزش روزِ فقط ردیف‌هایی که مبنای خرید دارند. */
    val basisValueRial: Double,
    /** بهای تمام‌شده همان ردیف‌ها. */
    val principalRial: Double,
    val profitRial: Double,
    val profitPercent: Double,
    /** ارزش ردیف‌هایی که مبنای خرید نداشتند و کنار گذاشته شدند. */
    val uncoveredValueRial: Double,
    /** تعداد ردیف‌هایی که در محاسبه آمدند. */
    val coveredCount: Int,
    /** تعداد کل ردیف‌های سبد. */
    val totalCount: Int
) {
    /**
     * false یعنی هیچ ردیفی مبنای خرید ندارد. در این حالت عدد سود «صفر» نیست،
     * **نامعلوم** است، و UI باید همین را بگوید نه «۰ تومان» سبز.
     */
    val hasBasis: Boolean get() = principalRial > 0.0

    /** true یعنی بخشی از سبد در محاسبه نیامده و باید به کاربر گفته شود. */
    val isPartial: Boolean get() = coveredCount < totalCount

    companion object {

        /** ردیفی مبنای خرید دارد که هم تعداد واحد داشته باشد و هم قیمت خرید. */
        fun hasBasis(item: PortfolioEntity): Boolean =
            item.quantity > 0L && item.averagePrice > 0.0

        fun calculate(items: List<PortfolioEntity>): PortfolioPnl {
            val totalValue = items.sumOf { it.currentValue }
            val covered = items.filter { hasBasis(it) }

            val basisValue = covered.sumOf { it.currentValue }
            val principal = covered.sumOf { it.quantity.toDouble() * it.averagePrice }
            val profit = if (principal > 0.0) basisValue - principal else 0.0

            return PortfolioPnl(
                totalValueRial = totalValue,
                basisValueRial = basisValue,
                principalRial = principal,
                profitRial = profit,
                profitPercent = if (principal > 0.0) (profit / principal) * 100.0 else 0.0,
                uncoveredValueRial = totalValue - basisValue,
                coveredCount = covered.size,
                totalCount = items.size
            )
        }
    }
}
