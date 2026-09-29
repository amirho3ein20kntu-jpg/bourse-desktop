package com.example.domain.portfolio

import com.example.data.model.PortfolioEntity
import com.example.data.model.RebalanceAction
import com.example.data.model.SymbolRebalanceAlert

/**
 * اعمال سفارش‌هایی که کاربر واقعاً در کارگزاری اجرا کرده — بدون وابستگی به
 * اندروید، تا قابل تست باشد.
 *
 * تا امروز چرخه این بود: اپ می‌گفت چه بخر/بفروش، کاربر معامله می‌کرد، بعد باید
 * **کل اکسل کارگزاری را دوباره وارد می‌کرد**. این ماژول آن قدم آخر را حذف می‌کند.
 *
 * **این کد داده واقعی سبد را تغییر می‌دهد.** پس دو قاعده سخت‌گیرانه دارد:
 *  ۱. چیزی از هوا ساخته نمی‌شود — سفارشی که تعداد واحد یا قیمت روز نداشته باشد
 *     اصلاً در فهرست نمی‌آید.
 *  ۲. بیشتر از آنچه دارید فروخته نمی‌شود.
 */
object OrderExecution {

    /**
     * یک سفارش قابل اعمال، به‌همراه نتیجه‌ی دقیقش.
     * همه اعداد از قبل حساب شده‌اند تا UI بتواند پیش از تأیید نشانشان دهد.
     */
    data class ExecutableOrder(
        val itemId: Long,
        val symbol: String,
        val action: RebalanceAction,
        /** تعداد واحدی که واقعاً اعمال می‌شود (برای فروش، سقفش موجودی است) */
        val units: Long,
        val unitPriceRial: Double,
        val previousQuantity: Long,
        val newQuantity: Long,
        val newValueRial: Double,
        /** مبلغ ریالی این سفارش با قیمت روز */
        val amountRial: Double,
        /** true یعنی تعداد به‌خاطر کمبود موجودی کم شد */
        val isCappedBySholding: Boolean
    )

    /**
     * سفارش‌های قابل اعمال را از سیگنال‌های ریبلنس می‌سازد.
     *
     * ردیف‌های زیر عمداً کنار گذاشته می‌شوند:
     *  - «نگهداری» (کاری برای انجام نیست)
     *  - «پیشنهاد ورود» به طبقه خالی (هنوز نمادی انتخاب نشده)
     *  - نمادی که قیمت روز یا تعداد واحد تخمینی ندارد
     */
    fun planFrom(
        alerts: List<SymbolRebalanceAlert>,
        portfolio: List<PortfolioEntity>
    ): List<ExecutableOrder> {
        val byId = portfolio.associateBy { it.id }

        return alerts.mapNotNull { alert ->
            if (alert.action == RebalanceAction.HOLD) return@mapNotNull null
            if (alert.isSuggestedNewPosition) return@mapNotNull null
            if (alert.estimatedUnits <= 0L) return@mapNotNull null
            if (alert.lastPrice <= 0.0) return@mapNotNull null

            val item = byId[alert.itemId] ?: return@mapNotNull null

            val requested = alert.estimatedUnits
            val units = if (alert.action == RebalanceAction.SELL) {
                minOf(requested, item.quantity)
            } else {
                requested
            }
            if (units <= 0L) return@mapNotNull null

            val newQuantity = when (alert.action) {
                RebalanceAction.BUY -> item.quantity + units
                RebalanceAction.SELL -> item.quantity - units
                RebalanceAction.HOLD -> item.quantity
            }

            ExecutableOrder(
                itemId = item.id,
                symbol = item.symbol,
                action = alert.action,
                units = units,
                unitPriceRial = alert.lastPrice,
                previousQuantity = item.quantity,
                newQuantity = newQuantity,
                // با همان مبنای ارزشِ بقیه اپ: خالص از کارمزد فروش.
                newValueRial = newQuantity * alert.lastPrice * item.netValueRatio,
                amountRial = units * alert.lastPrice,
                isCappedBySholding = units < requested
            )
        }
    }

    /**
     * سفارش‌های انتخاب‌شده را روی سبد اعمال می‌کند و **فقط ردیف‌های تغییرکرده**
     * را برمی‌گرداند.
     *
     * قیمت سر به سر در خرید به‌صورت میانگین وزنی به‌روز می‌شود — بدون این کار
     * سود/زیان بعد از هر خرید غلط می‌شد. اگر سر به سر قبلی نامعلوم باشد (صفر)،
     * نامعلوم می‌ماند؛ عدد ساختگی جای «نمی‌دانم» نمی‌نشیند.
     */
    fun apply(
        portfolio: List<PortfolioEntity>,
        orders: List<ExecutableOrder>
    ): List<PortfolioEntity> {
        if (orders.isEmpty()) return emptyList()
        val byId = portfolio.associateBy { it.id }

        return orders.mapNotNull { order ->
            val item = byId[order.itemId] ?: return@mapNotNull null

            val newAveragePrice = when {
                order.action != RebalanceAction.BUY -> item.averagePrice
                // سر به سر قبلی نامعلوم است: در خرید روی یک موجودی ناشناخته
                // نمی‌توان میانگین معنادار ساخت.
                item.averagePrice <= 0.0 && item.quantity > 0L -> 0.0
                // ورود از صفر: قیمت خرید همان قیمت روز است.
                item.quantity <= 0L -> order.unitPriceRial
                else -> {
                    val previousCost = item.quantity * item.averagePrice
                    val addedCost = order.units * order.unitPriceRial
                    if (order.newQuantity > 0L) {
                        (previousCost + addedCost) / order.newQuantity
                    } else {
                        item.averagePrice
                    }
                }
            }

            val updated = item.copy(
                quantity = order.newQuantity,
                currentValue = order.newValueRial,
                lastPrice = order.unitPriceRial,
                averagePrice = newAveragePrice,
                // حالا ارزش از تعداد × قیمت مشتق می‌شود، پس همگام‌سازی زنده
                // اجازه دارد نگهش دارد.
                isValueManuallySet = false
            )

            if (updated == item) null else updated
        }
    }
}
