package com.example.domain.sync

import com.example.data.model.PortfolioEntity

/** خلاصه‌ی آنچه در همگام‌سازی قیمت واقعاً اتفاق افتاد. */
data class PriceSyncResult(
    /** فقط ردیف‌هایی که واقعاً تغییر کرده‌اند */
    val items: List<PortfolioEntity> = emptyList(),
    /** تعداد نمادهایی که قیمت روزشان به‌روز شد */
    val pricedCount: Int = 0,
    /** تعداد نمادهایی که ارزششان بازمحاسبه شد */
    val revaluedCount: Int = 0,
    /** ردیف‌هایی که ارزش دستی داشتند و محافظت شدند */
    val keptManual: Int = 0,
    /** نمادهایی که قیمت زنده‌ای برایشان نبود */
    val notFound: List<String> = emptyList()
) {
    val hasChanges: Boolean get() = items.isNotEmpty()
}

/**
 * قاعده‌ی همگام‌سازی قیمت — بدون وابستگی به اندروید یا شبکه، تا قابل تست باشد.
 *
 * این منطق جای باگی را گرفته که در هر رفرش، `currentValue` را با
 * `quantity × قیمت زنده` بازنویسی می‌کرد؛ اگر تعداد واحد از اکسل کارگزاری
 * غلط پارس شده بود، ارزش سبد کاربر بی‌صدا خراب می‌شد.
 *
 * بازمحاسبه، کارمزد فروشِ هر نماد را هم لحاظ می‌کند تا ارزش با همان مبنایی
 * ساخته شود که ستون «ارزش فعلی» کارگزاری دارد. رجوع به [PortfolioEntity.netValueRatio].
 */
object PortfolioPriceSync {

    /**
     * @param priceLookup قیمت زنده هر نماد به ریال؛ null یعنی داده‌ای نیست.
     * @param revalueFromQuantity کلید کاربر برای بازمحاسبه ارزش.
     */
    fun apply(
        portfolio: List<PortfolioEntity>,
        revalueFromQuantity: Boolean,
        priceLookup: (String) -> Double?
    ): PriceSyncResult {
        val changed = mutableListOf<PortfolioEntity>()
        val notFound = mutableListOf<String>()
        var priced = 0
        var revalued = 0
        var keptManual = 0

        for (item in portfolio) {
            val livePrice = priceLookup(item.symbol)
            if (livePrice == null || livePrice <= 0.0) {
                notFound.add(item.symbol)
                continue
            }

            // ارزش فقط وقتی بازمحاسبه می‌شود که هر سه شرط برقرار باشد
            val mayRevalue = revalueFromQuantity &&
                    item.quantity > 0L &&
                    !item.isValueManuallySet

            if (item.isValueManuallySet && item.quantity > 0L) keptManual++

            // `netValueRatio` کارمزد فروش را برمی‌گرداند، همان چیزی که ستون
            // «ارزش فعلی» اکسل کارگزاری از قبل کم کرده بود. بدون آن، ارزش سبد
            // بعد از اولین رفرش به اندازه کارمزد بالا می‌پرید و سود سبد بی‌دلیل
            // مثبت‌تر از عدد کارگزاری می‌شد.
            val newValue = if (mayRevalue) {
                item.quantity * livePrice * item.netValueRatio
            } else {
                item.currentValue
            }

            val priceChanged = livePrice != item.lastPrice
            val valueChanged = newValue != item.currentValue
            if (!priceChanged && !valueChanged) continue

            if (priceChanged) priced++
            if (valueChanged) revalued++

            changed.add(item.copy(lastPrice = livePrice, currentValue = newValue))
        }

        return PriceSyncResult(
            items = changed,
            pricedCount = priced,
            revaluedCount = revalued,
            keptManual = keptManual,
            notFound = notFound
        )
    }
}
