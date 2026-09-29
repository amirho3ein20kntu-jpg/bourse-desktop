package com.example.domain.portfolio

import com.example.data.model.AssetCategory
import com.example.data.model.PortfolioEntity

/**
 * قاعده‌ی ویرایش دستی یک ردیف سبد — بدون وابستگی به اندروید، تا قابل تست باشد.
 *
 * دیالوگ ویرایش فقط نماد، دسته، ارزش روز و تعداد واحد را می‌پرسد. دو فیلد دیگر
 * (`averagePrice`، `manualTargetPercent` و `isHoldLocked`) از جای دیگری می‌آیند و این دیالوگ
 * حق پاک کردنشان را ندارد. نسخه قبلی رکورد را از صفر می‌ساخت، پس یک ویرایش ساده
 * قیمت سر به سرِ واردشده از اکسل کارگزاری را صفر می‌کرد و سود/زیان کل سبد در
 * داشبورد بی‌صدا اشتباه می‌شد.
 */
object PortfolioEdit {

    /**
     * قیمت سر به سری که پس از ویرایش باید ذخیره شود.
     *
     *  ۱. اگر رکورد قبلی قیمت سر به سر واقعی دارد، همان حفظ می‌شود.
     *  ۲. نماد جدیدِ دارای تعداد واحد: قیمت روز مبنا می‌شود تا سود/زیان صفر
     *     گزارش شود، نه سودِ ساختگی به اندازه کل ارزش.
     *  ۳. بدون تعداد واحد، سر به سر معنایی ندارد و صفر می‌ماند؛ داشبورد این
     *     ردیف را از محاسبه سود/زیان کنار می‌گذارد.
     */
    fun resolveAveragePrice(
        existing: PortfolioEntity?,
        quantity: Long,
        lastPriceRial: Double
    ): Double = when {
        existing != null && existing.averagePrice > 0.0 -> existing.averagePrice
        quantity > 0L && lastPriceRial > 0.0 -> lastPriceRial
        else -> 0.0
    }

    /**
     * رکورد نهاییِ حاصل از ویرایش دستی کاربر.
     *
     * @param existing رکورد فعلی در دیتابیس؛ `null` یعنی نماد جدید.
     */
    fun applyManualEdit(
        existing: PortfolioEntity?,
        id: Long,
        symbol: String,
        category: AssetCategory,
        currentValueRial: Double,
        quantity: Long,
        lastPriceRial: Double
    ): PortfolioEntity = PortfolioEntity(
        id = id,
        symbol = symbol.trim(),
        assetCategory = category,
        currentValue = currentValueRial,
        quantity = quantity,
        lastPrice = lastPriceRial,
        averagePrice = resolveAveragePrice(existing, quantity, lastPriceRial),
        // درصد هدف اختصاصی نماد از این دیالوگ نمی‌آید — حفظ می‌شود.
        manualTargetPercent = existing?.manualTargetPercent,
        // ورودی دستی کاربر: اگر تعداد واحد داده شده باشد، ارزش قابل بازمحاسبه از
        // قیمت زنده است و قفل نمی‌شود؛ در غیر این صورت تنها منبع ارزش، عددِ کاربر است.
        isValueManuallySet = quantity <= 0L,
        isCategoryManuallySet = true,
        // قفل نگه‌داری جای دیگری روشن/خاموش می‌شود؛ ویرایش ساده حق پاک کردنش را ندارد.
        isHoldLocked = existing?.isHoldLocked == true,
        // نرخ کارمزد از فایل کارگزاری آمده و این دیالوگ آن را نمی‌پرسد، پس
        // مثل قیمت سر به سر باید زنده بماند.
        netValueRatio = existing?.netValueRatio ?: 1.0
    )
}
