package com.example.data.model


/**
 * وضعیت یک نماد در عکس روزانه سبد.
 *
 * [PortfolioSnapshotEntity] فقط جمع کل را نگه می‌دارد، پس «کدام صندوق اضافه یا
 * کم شد» و «وزن هر صندوق در طول زمان» از آن در نمی‌آید. این جدول برای هر روز،
 * یک ردیف به‌ازای هر نماد دارد و با همان کلید روز عوض می‌شود: بارگذاری دوباره
 * در همان روز، ردیف‌های آن روز را کامل جایگزین می‌کند تا نمادی که از سبد رفته
 * در عکس امروز باقی نماند.
 */
data class HoldingSnapshotEntity(
    /** همان کلید روز در [PortfolioSnapshotEntity.day] */
    val day: String,
    val symbol: String,
    val assetCategory: AssetCategory,
    /** تعداد واحد؛ ردیف‌های تکراری یک نماد با هم جمع شده‌اند */
    val quantity: Long = 0L,
    /** ارزش روز به ریال */
    val valueRial: Double,
    /** بهای تمام‌شده به ریال؛ صفر یعنی این نماد مبنای خرید نداشت */
    val principalRial: Double = 0.0
)
