package com.example.data.model


/**
 * عکس لحظه‌ای از وضعیت سبد در یک روز.
 *
 * اپ تا امروز هیچ تاریخچه‌ای نگه نمی‌داشت؛ «ماه پیش چقدر داشتم؟» جوابی نداشت.
 * هر بار که داده سبد واقعاً تغییر کند (بارگذاری اکسل، اعمال سفارش، همگام‌سازی
 * قیمت) یک نقطه ثبت می‌شود.
 *
 * کلید اصلی، **روز** است نه زمان دقیق: چند بار بارگذاری در یک روز فقط آخرین
 * وضعیت همان روز را نگه می‌دارد، وگرنه نمودار پر از نقاط تکراری می‌شد.
 */
data class PortfolioSnapshotEntity(
    /** تاریخ میلادی به شکل yyyy-MM-dd — یک ردیف برای هر روز */
    val day: String,

    /** زمان دقیق ثبت (برای مرتب‌سازی و نمایش) */
    val epochMs: Long,

    /** ارزش کل سبد به ریال */
    val totalValueRial: Double,

    /**
     * بهای تمام‌شده ردیف‌هایی که مبنای خرید دارند، همان
     * [com.example.domain.portfolio.PortfolioPnl.principalRial]؛ صفر یعنی نامعلوم.
     */
    val principalRial: Double = 0.0,

    /**
     * ارزش روزِ همان ردیف‌هایی که در [principalRial] آمده‌اند
     * ([com.example.domain.portfolio.PortfolioPnl.basisValueRial]).
     *
     * سود آن روز `basisValueRial − principalRial` است. [totalValueRial] جایش
     * نمی‌نشیند، چون ردیف بدون قیمت خرید را هم شامل می‌شود و کل ارزشش را سود
     * جلوه می‌دهد. صفر یعنی این عکس پیش از نسخه ۹ پایگاه‌داده ثبت شده و سودش
     * نامعلوم است.
     */
    val basisValueRial: Double = 0.0,

    /** شاخص انحراف سبد در آن روز */
    val trIndex: Double = 0.0,

    val symbolCount: Int = 0
)
