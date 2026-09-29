package com.example.data.model


data class SettingsEntity(
    val id: Int = 1,
    val riskTolerance: Double = 12.0, // RT (درصد ریسک‌پذیری) e.g. 12%
    val timeHorizonMonths: Int = 6, // 3, 6, 12
    // 3 Months EML
    val emlStock3M: Double = 18.0,
    val emlGold3M: Double = 12.0,
    // 6 Months EML
    val emlStock6M: Double = 25.0,
    val emlGold6M: Double = 18.0,
    // 12 Months EML
    val emlStock12M: Double = 35.0,
    val emlGold12M: Double = 25.0,
    // Trading rebalance threshold (TR) trigger, default 5.0%
    val rebalanceThresholdPercent: Double = 5.0,
    // Optional manual weight override
    val isManualWeightsEnabled: Boolean = false,
    val manualGoldWeight: Double = 20.0,
    val manualStockWeight: Double = 35.0,
    val manualMixedWeight: Double = 15.0,
    val manualFixedWeight: Double = 30.0,
    /**
     * وقتی روشن است، ارزش ردیف‌هایی که تعداد واحد دارند با قیمت زنده بازمحاسبه می‌شود.
     * ردیف‌هایی که کاربر ارزششان را دستی زده در هر حالت دست‌نخورده می‌مانند.
     */
    val isLivePriceSyncEnabled: Boolean = true,

    /**
     * وقتی روشن است، اپ روزانه در پس‌زمینه شاخص Tr را بررسی می‌کند و اگر از
     * آستانه رد شد نوتیفیکیشن می‌دهد.
     */
    val isRebalanceAlertEnabled: Boolean = true,

    /** آیا در آخرین بررسی پس‌زمینه، Tr بالای آستانه بود (برای تشخیص «عبور») */
    val wasAboveThreshold: Boolean = false,

    /** زمان آخرین نوتیفیکیشن ارسال‌شده؛ صفر یعنی هرگز */
    val lastAlertEpochMs: Long = 0L
) {
    fun currentEmlStock(horizon: TimeHorizon): Double = when (horizon) {
        TimeHorizon.THREE_MONTHS -> emlStock3M
        TimeHorizon.SIX_MONTHS -> emlStock6M
        TimeHorizon.TWELVE_MONTHS -> emlStock12M
    }

    fun currentEmlGold(horizon: TimeHorizon): Double = when (horizon) {
        TimeHorizon.THREE_MONTHS -> emlGold3M
        TimeHorizon.SIX_MONTHS -> emlGold6M
        TimeHorizon.TWELVE_MONTHS -> emlGold12M
    }

    /**
     * EML صندوق‌های مختلط از روی EML سهامی مشتق می‌شود.
     * ضریب در [MIXED_TO_STOCK_EML_RATIO] است — تنها جایی که این عدد نوشته شده.
     */
    fun currentEmlMixed(horizon: TimeHorizon): Double {
        return MIXED_TO_STOCK_EML_RATIO * currentEmlStock(horizon)
    }

    companion object {
        /**
         * نسبت EML مختلط به EML سهامی.
         *
         * تا پیش از این ۲/۳ بود (مطابق اکسل دوره). به درخواست کاربر به ۱/۳
         * تغییر کرد. **این عدد بی‌اثر نیست:** چون در مخرج فرمول
         * `M = RT / EML_M × 10` می‌نشیند، نصف شدنش وزن هدف طبقه مختلط را
         * دو برابر می‌کند.
         *
         * عمداً فقط همین یک جا نوشته شده؛ قبلاً یک نسخه دومش در صفحه تنظیمات
         * هاردکد بود و هر تغییری باید در دو جا تکرار می‌شد.
         */
        const val MIXED_TO_STOCK_EML_RATIO: Double = 1.0 / 3.0
    }
}
