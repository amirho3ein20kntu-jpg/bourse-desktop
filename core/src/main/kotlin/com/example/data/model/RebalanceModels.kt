package com.example.data.model

enum class RebalanceAction(val persianLabel: String) {
    BUY("خرید"),
    SELL("فروش"),
    HOLD("نگهداری")
}

enum class TransactionType(val persianLabel: String) {
    DEPOSIT("واریز سرمایه جدید"),
    WITHDRAWAL("برداشت وجه نقد")
}

data class TargetWeights(
    val goldWeight: Double,
    val stockWeight: Double,
    val mixedWeight: Double,
    val fixedIncomeWeight: Double,
    val calculatedRt: Double,
    val isAggressive: Boolean = false,
    val isAdjustedDown: Boolean = false
) {
    val totalWeight: Double
        get() = goldWeight + stockWeight + mixedWeight + fixedIncomeWeight

    fun getWeight(category: AssetCategory): Double = when (category) {
        AssetCategory.GOLD -> goldWeight
        AssetCategory.STOCK -> stockWeight
        AssetCategory.MIXED -> mixedWeight
        AssetCategory.FIXED_INCOME -> fixedIncomeWeight
    }
}

data class AssetAllocationBreakdown(
    val category: AssetCategory,
    val currentValue: Double,
    val currentWeightPercent: Double,
    val targetWeightPercent: Double,
    val targetValue: Double,
    /** Va - Vr : مثبت = اضافه‌وزن (فروش) — توجه: علامت این فیلد برعکسِ سطح نماد است */
    val deltaValue: Double
)

data class SymbolRebalanceAlert(
    /**
     * شناسه پایدار برای کلید LazyColumn.
     * ردیف واقعی: `id` رکورد پورتفو. ردیف پیشنهاد ورود: عددی منفی بر اساس طبقه.
     *
     * بدون کلید، Compose هویت هر آیتم را از موقعیتش در لیست می‌گیرد؛ با تغییر
     * مرتب‌سازی یا فیلتر، حالت داخلی کارت‌ها به کارت اشتباه می‌چسبد.
     */
    val itemId: Long = 0L,
    val symbol: String,
    val category: AssetCategory,
    /** Va — ارزش فعلی */
    val currentValue: Double,
    /** Vr — ارزش ریبلنس‌شده (هدف) */
    val targetValue: Double,
    /** Vr - Va : مثبت = کسری (خرید)، منفی = مازاد (فروش) */
    val deltaValue: Double,
    val currentWeightPercent: Double,
    val targetWeightPercent: Double,
    val action: RebalanceAction,
    /** |deltaValue| */
    val actionAmount: Double,
    val currentQuantity: Long = 0L,
    val estimatedUnits: Long = 0L,
    /** قیمت روز هر واحد (ریال) — برای تبدیل مبلغ سفارش به تعداد واحد */
    val lastPrice: Double = 0.0,
    val minOrderNotice: String? = null,
    /**
     * true یعنی این ردیف یک نمادِ واقعی در سبد نیست، بلکه «پیشنهاد ورود» به طبقه‌ای است
     * که وزن هدف دارد ولی کاربر هیچ صندوقی از آن ندارد. کاربر باید نماد را خودش انتخاب کند.
     */
    val isSuggestedNewPosition: Boolean = false,
    /**
     * true یعنی کاربر «قفل نگه‌داری» این نماد را روشن کرده و موتور حق پیشنهاد
     * فروشش را ندارد. مقدار [deltaValue] دست‌نخورده می‌ماند — انحراف واقعی سبد
     * است و در شاخص Tr هم شمرده می‌شود — ولی [action] هرگز فروش نمی‌شود.
     */
    val isHoldLocked: Boolean = false,
    /**
     * مبلغ فروشی که فقط به خاطر قفل انجام نشده. صفر یعنی قفل در این لحظه
     * بی‌اثر بوده (نماد یا کم‌وزن است یا در محدوده تعادل).
     */
    val suppressedSellAmount: Double = 0.0
)

/**
 * جمع‌بندی اثر «قفل نگه‌داری» روی کل سبد.
 *
 * وقتی نمادی قفل است و اضافه‌وزن دارد، راه رسیدن به تعادل بدون فروشِ آن
 * فقط بزرگ‌تر کردن خودِ سبد است: اگر ارزش نماد قفل‌شده V و وزن هدفش w باشد،
 * کل سبد باید به `V ÷ w` برسد. مابه‌التفاوتش همان پول نقدی است که کاربر
 * باید بیاورد.
 */
data class LockedHoldSummary(
    /** نمادهای قفل‌شده‌ای که در این لحظه اضافه‌وزن دارند */
    val bindingSymbols: List<String>,
    /** مجموع فروشی که به خاطر قفل پیشنهاد نشد */
    val suppressedSellAmount: Double,
    /** پول نقد لازم برای رسیدن به تعادل بدون فروش نمادهای قفل‌شده */
    val requiredCashRial: Double,
    /** ارزش کل سبد پس از تزریق [requiredCashRial] */
    val targetPvRial: Double,
    /**
     * true یعنی وزن هدف یکی از نمادهای قفل‌شده صفر است؛ در این حالت با هیچ
     * مقدار پول نقدی وزنش به هدف نمی‌رسد و تنها راه، فروش یا تغییر چینش است.
     */
    val isUnreachableByCash: Boolean = false
)

data class PortfolioCalculationResult(
    val totalPv: Double,
    /** Tr = (Σ|Va − Vr| / PV) × 100  — طبق مستند PCMR */
    val trIndex: Double,
    val isRebalanceTriggered: Boolean,
    val thresholdLimit: Double,
    val calculatedRt: Double,
    val investorRt: Double,
    val targetWeights: TargetWeights,
    val categoryBreakdowns: List<AssetAllocationBreakdown>,
    val symbolAlerts: List<SymbolRebalanceAlert>,
    val totalBuyAmount: Double,
    val totalSellAmount: Double,
    val balancedExecutionAmount: Double,
    /**
     * درصدی از وزن هدف که به هیچ ردیفی تخصیص نیافته. در حالت سالم باید ۰ باشد؛
     * هر مقدار غیرصفر یعنی محاسبات واریز/برداشت و Tr قابل اتکا نیستند.
     */
    val unallocatedWeightPercent: Double = 0.0,
    /**
     * خلاصه اثر قفل نگه‌داری، یا null اگر هیچ نماد قفل‌شده‌ای اضافه‌وزن نداشته باشد.
     */
    val lockedHold: LockedHoldSummary? = null,
    /**
     * بخشی از [trIndex] که فقط به خاطر قفل نگه‌داری سر جایش می‌ماند: Tr پس از
     * اجرای همه سفارش‌هایی که بدون فروش نماد قفل‌شده و بدون پول نقد ممکن‌اند.
     *
     * Tr خودش عمداً کامل می‌ماند (قفل انحراف را پنهان نمی‌کند)، ولی اگر این
     * عدد به آستانه برسد، هشدار ریبلنس با معامله برطرف نمی‌شود و تنها راهش
     * واریز [LockedHoldSummary.requiredCashRial] است. صفر یعنی قفلی مانع نیست.
     */
    val lockBoundTrIndex: Double = 0.0
)

data class DepositWithdrawalOrder(
    /** شناسه پایدار برای کلید LazyColumn — از همان ردیف سیگنال می‌آید */
    val itemId: Long = 0L,
    val symbol: String,
    val category: AssetCategory,
    val orderAmount: Double,
    val action: RebalanceAction,
    val absoluteAmount: Double,
    val currentWeight: Double,
    val targetWeight: Double,
    val postTransactionValue: Double,
    /**
     * تعداد واحد تخمینی این سفارش.
     *
     * سفارش در کارگزاری بر حسب **تعداد واحد** ثبت می‌شود، نه مبلغ. بدون این عدد
     * کاربر باید خودش مبلغ را بر قیمت روز تقسیم کند. صفر یعنی قیمت روز در دسترس
     * نبوده و تخمینی نمی‌شود زد.
     */
    val estimatedUnits: Long = 0L,
    /** هشدار حداقل سفارش بورس، اگر مبلغ زیر کف باشد. */
    val minOrderNotice: String? = null,
    val isSuggestedNewPosition: Boolean = false,
    /**
     * true یعنی این ردیف قفل نگه‌داری دارد. اگر سهمش از برداشت منفی می‌شد،
     * ارزشش ثابت نگه داشته شده و آن سهم روی ردیف‌های آزاد سرشکن شده است.
     */
    val isHoldLocked: Boolean = false
)

data class DepositWithdrawalResult(
    val type: TransactionType,
    /** مبلغی که کاربر وارد کرده */
    val inputAmount: Double,
    /** مبلغی که واقعاً اعمال شد (برداشت به سقف PV محدود می‌شود) */
    val effectiveAmount: Double = inputAmount,
    /** true یعنی مبلغ درخواستی از دارایی بیشتر بود و محدود شد */
    val isAmountClamped: Boolean = false,
    val previousPv: Double,
    val newPv: Double,
    val orders: List<DepositWithdrawalOrder>,
    /** Tr پس از اجرای سفارش‌ها — واقعاً محاسبه می‌شود، نه فرض */
    val postTrIndex: Double = 0.0,
    /**
     * نمادهای قفل‌شده‌ای که در این محاسبه ارزششان ثابت نگه داشته شد، یعنی
     * سهمشان از برداشت روی بقیه ردیف‌ها سرشکن شده است.
     */
    val frozenSymbols: List<String> = emptyList(),
    /**
     * true یعنی برداشت درخواستی بیشتر از بخش آزاد سبد بود و به سقفِ
     * «کل سبد منهای ارزش نمادهای قفل‌شده» محدود شد.
     */
    val isLimitedByLockedPositions: Boolean = false
)
