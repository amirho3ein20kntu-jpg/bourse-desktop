package com.example.data.model


/**
 * نرخ تورم نقطه‌به‌نقطه (۱۲ ماهه منتهی به ماه)، از بانک مرکزی.
 *
 * **این عدد از قبل سالانه است** — برخلاف بازدهی صندوق‌ها که ماهانه ذخیره
 * می‌شود، این مقدار نیازی به زنجیره‌کردن ندارد؛ مستقیماً همان نرخ اعلام‌شده
 * توسط بانک مرکزی برای آن ماه است.
 */
data class InflationRateEntity(
    val jalaliYear: Int,
    /** ۱ = فروردین ... ۱۲ = اسفند */
    val jalaliMonth: Int,
    val ratePercent: Double,
    /**
     * `true` یعنی کاربر خودش این عدد را وارد یا اصلاح کرده — دفعه بعد که
     * دریافت خودکار از cbi.ir انجام می‌شود، این ردیف را بی‌صدا رونویسی نمی‌کند.
     */
    val isManuallyEdited: Boolean = false,
    val updatedAtEpochMs: Long = 0L
)
