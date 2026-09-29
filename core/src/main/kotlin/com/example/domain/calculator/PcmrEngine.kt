package com.example.domain.calculator

import com.example.data.model.AssetAllocationBreakdown
import com.example.data.model.AssetCategory
import com.example.data.model.DepositWithdrawalOrder
import com.example.data.model.DepositWithdrawalResult
import com.example.data.model.LockedHoldSummary
import com.example.data.model.PortfolioCalculationResult
import com.example.data.model.PortfolioEntity
import com.example.data.model.RebalanceAction
import com.example.data.model.SettingsEntity
import com.example.data.model.SymbolRebalanceAlert
import com.example.data.model.TargetWeights
import com.example.data.model.TimeHorizon
import com.example.data.model.TransactionType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * توجه: مجموع وزن‌های هدف همیشه باید ۱۰۰٪ باشد. کل موتور — تخصیص وزن نمادها،
 * شاخص Tr، و ماشین‌حساب واریز/برداشت — روی همین فرض بنا شده است.
 * تنها تولیدکنندگان TargetWeights (چینش دستی، چینش خودکار، و گرد کردن به مضارب)
 * موظف‌اند این ضمانت را حفظ کنند.
 */

/**
 * موتور محاسباتی PCMR.
 *
 * مرجع فرمول‌ها: فایل `031_Fuormula.xlsx` دوره (شیت‌های «چینش اتومات» و «چینش دستی»)
 *   G = RT / EML_G × 30
 *   S = RT / EML_S × 60
 *   M = RT / EML_M × 10    که EML_M = EML_S × ۱/۳
 *
 * توجه: نسبت EML مختلط در اکسل دوره ۲/۳ بود و به درخواست کاربر به ۱/۳
 * تغییر کرده؛ یعنی وزن هدف طبقه مختلط نسبت به مرجع اصلی دو برابر است.
 * ضریب در SettingsEntity.MIXED_TO_STOCK_EML_RATIO نگهداری می‌شود.
 *   F = 100 − (G + S + M)
 *   Tr = (Σ|Va − Vr| / PV) × 100
 */
object PcmrEngine {

    /** حداقل خرید بورس: ۱۰۰ هزار تومان (مقادیر داخلی همه ریالی‌اند) */
    const val MIN_BUY_ORDER_RIAL = 1_000_000.0

    /** حداقل فروش بورس: ۵۰۰ هزار تومان */
    const val MIN_SELL_ORDER_RIAL = 5_000_000.0

    /**
     * کف اقتصادی هر صندوق: ۱۰ میلیون تومان (بخش ۴ مستند PCMR).
     * زیر این مبلغ، معامله برای ریبلنس صرفه اقتصادی ندارد.
     */
    const val MIN_ECONOMIC_POSITION_RIAL = 100_000_000.0

    /** سفارش کمتر از ۱۰۰ تومان عملاً یعنی «کاری نکن» */
    private const val NEGLIGIBLE_ORDER_RIAL = 1_000.0

    /**
     * هشدار حداقل سفارش بورس، یا null اگر مبلغ به کف رسیده باشد.
     * هم سیگنال‌های ریبلنس و هم ماشین‌حساب واریز/برداشت از همین قاعده استفاده
     * می‌کنند تا دو صفحه دو جواب متفاوت ندهند.
     */
    private fun minOrderNoticeFor(action: RebalanceAction, amountRial: Double): String? = when {
        action == RebalanceAction.BUY && amountRial < MIN_BUY_ORDER_RIAL ->
            "کمتر از حداقل خرید بورس (۱۰۰ هزار تومان)"
        action == RebalanceAction.SELL && amountRial < MIN_SELL_ORDER_RIAL ->
            "کمتر از حداقل فروش بورس (۵۰۰ هزار تومان)"
        else -> null
    }

    /** تعداد واحد تخمینی برای یک مبلغ ریالی؛ صفر یعنی قیمت روز در دسترس نیست. */
    private fun estimateUnits(amountRial: Double, unitPriceRial: Double): Long =
        if (unitPriceRial > 0.0) (amountRial / unitPriceRial).roundToLong() else 0L

    /**
     * فاز ۱: محاسبه وزن هدف طبقات دارایی.
     * عیناً مطابق شیت «چینش اتومات» — دست نخورد مگر با تغییر مستند.
     */
    fun calculateTargetWeights(settings: SettingsEntity): TargetWeights {
        val horizon = TimeHorizon.fromMonths(settings.timeHorizonMonths)
        val rt = settings.riskTolerance
        val emlS = settings.currentEmlStock(horizon)
        val emlG = settings.currentEmlGold(horizon)
        val emlM = settings.currentEmlMixed(horizon)

        // چینش دستی: کاربر درصدها را می‌دهد، اکسل RT برآمده از سبد را معکوس حساب می‌کند
        if (settings.isManualWeightsEnabled) {
            return manualWeights(settings, rt, emlG, emlS, emlM)
        }

        return automaticWeights(rt, emlG, emlS, emlM)
    }

    /**
     * چینش دستی با نرمال‌سازی.
     *
     * UI فقط وقتی جمع درصدها ۱۰۰ نیست هشدار قرمز می‌دهد ولی جلوی ذخیره را نمی‌گیرد.
     * کل موتور روی فرض «مجموع وزن‌ها = ۱۰۰٪» بنا شده — تخصیص وزن نمادها، شاخص Tr
     * و ماشین‌حساب واریز/برداشت همه از آن مشتق می‌شوند. اگر کاربر مثلاً ۱۱۰٪ وارد
     * کند و نرمال نشود، ماشین‌حساب واریز مبلغی بیشتر از پول موجود تخصیص می‌دهد.
     */
    private fun manualWeights(
        settings: SettingsEntity,
        rt: Double,
        emlG: Double,
        emlS: Double,
        emlM: Double
    ): TargetWeights {
        val rawG = max(0.0, settings.manualGoldWeight)
        val rawS = max(0.0, settings.manualStockWeight)
        val rawM = max(0.0, settings.manualMixedWeight)
        val rawF = max(0.0, settings.manualFixedWeight)
        val sum = rawG + rawS + rawM + rawF

        // چینش تماماً صفر معنایی ندارد؛ به فرمول خودکار برمی‌گردیم.
        if (sum <= 0.0) return automaticWeights(rt, emlG, emlS, emlM)

        val needsNormalizing = abs(sum - 100.0) > 0.05
        val scale = 100.0 / sum

        val gold = roundToSingleDecimal(rawG * scale)
        val stock = roundToSingleDecimal(rawS * scale)
        val mixed = roundToSingleDecimal(rawM * scale)
        // درآمد ثابت از باقیمانده حساب می‌شود تا جمع دقیقاً ۱۰۰ بماند
        val fixed = max(0.0, roundToSingleDecimal(100.0 - (gold + stock + mixed)))

        val calcRt = (gold / 100.0) * emlG + (stock / 100.0) * emlS + (mixed / 100.0) * emlM

        return TargetWeights(
            goldWeight = gold,
            stockWeight = stock,
            mixedWeight = mixed,
            fixedIncomeWeight = fixed,
            calculatedRt = roundToSingleDecimal(calcRt),
            isAggressive = rt > 25.0,
            isAdjustedDown = needsNormalizing
        )
    }

    private fun automaticWeights(
        rt: Double,
        emlG: Double,
        emlS: Double,
        emlM: Double
    ): TargetWeights {
        // نسبت‌های فریزشده برای ریسک‌پذیری بسیار بالا.
        // مستند: «RT بالای ۲۵» — اکیداً بزرگ‌تر از ۲۵، نه مساوی.
        if (rt > 25.0) {
            val gold = 20.0
            val stock = 70.0
            val mixed = 10.0
            val calcRt = (gold / 100.0) * emlG + (stock / 100.0) * emlS + (mixed / 100.0) * emlM

            return TargetWeights(
                goldWeight = gold,
                stockWeight = stock,
                mixedWeight = mixed,
                fixedIncomeWeight = 0.0,
                calculatedRt = roundToSingleDecimal(calcRt),
                isAggressive = true,
                isAdjustedDown = false
            )
        }

        var pGold = if (emlG > 0) (rt / emlG) * 30.0 else 0.0
        var pStock = if (emlS > 0) (rt / emlS) * 60.0 else 0.0
        var pMixed = if (emlM > 0) (rt / emlM) * 10.0 else 0.0

        // محافظ خارج از اکسل: اکسل اجازه می‌دهد F منفی شود؛ اینجا سه دارایی ریسکی
        // به نسبت کوچک می‌شوند تا جمع = ۱۰۰٪ و درآمد ثابت = ۰٪ شود.
        var isAdjusted = false
        val sumRisky = pGold + pStock + pMixed
        if (sumRisky > 100.0) {
            isAdjusted = true
            val scale = 100.0 / sumRisky
            pGold *= scale
            pStock *= scale
            pMixed *= scale
        }

        val calcRt = (pGold / 100.0) * emlG + (pStock / 100.0) * emlS + (pMixed / 100.0) * emlM

        // هر وزن جدا گرد می‌شود، پس جمعشان می‌توانست ۹۹٫۹ یا ۱۰۰٫۱ شود (در یک
        // چهارم ریسک‌پذیری‌ها). درآمد ثابت، مثل چینش دستی، از باقیمانده ساخته
        // می‌شود. اگر سه دارایی ریسکی باید کل سبد باشند (کوچک‌شده به ۱۰۰) یا پس
        // از گرد کردن از ۱۰۰ رد شوند، اختلاف روی بزرگ‌ترینشان می‌نشیند.
        var gold = roundToSingleDecimal(pGold)
        var stock = roundToSingleDecimal(pStock)
        var mixed = roundToSingleDecimal(pMixed)
        val drift = roundToSingleDecimal(gold + stock + mixed - 100.0)
        if (drift > 0.0 || (isAdjusted && drift != 0.0)) {
            when (maxOf(gold, stock, mixed)) {
                stock -> stock = roundToSingleDecimal(stock - drift)
                gold -> gold = roundToSingleDecimal(gold - drift)
                else -> mixed = roundToSingleDecimal(mixed - drift)
            }
        }
        val fixed = max(0.0, roundToSingleDecimal(100.0 - (gold + stock + mixed)))

        return TargetWeights(
            goldWeight = gold,
            stockWeight = stock,
            mixedWeight = mixed,
            fixedIncomeWeight = fixed,
            calculatedRt = roundToSingleDecimal(calcRt),
            isAggressive = false,
            isAdjustedDown = isAdjusted
        )
    }

    /** یک ردیف هدف: یا یک نماد واقعی، یا پیشنهاد ورود به طبقه‌ای که خالی است. */
    private data class TargetSlot(
        val symbol: String,
        val category: AssetCategory,
        val item: PortfolioEntity?,
        val targetWeightPercent: Double
    )

    /**
     * وزن هدف هر طبقه را بین ردیف‌های آن طبقه پخش می‌کند.
     *
     * قواعد:
     *  ۱. نمادی که `manualTargetPercent` دارد، همان درصد را می‌گیرد — مگر اینکه
     *     مجموع درصدهای دستیِ آن طبقه از وزن طبقه بیشتر شود، که در آن صورت همه
     *     به نسبت کوچک می‌شوند تا ضمانت «جمع = ۱۰۰٪» نشکند.
     *  ۲. باقیمانده وزن طبقه به نسبت ارزش فعلی بین بقیه نمادها پخش می‌شود.
     *  ۳. طبقه‌ای که هیچ نمادی ندارد ولی وزن هدف دارد، یک ردیف «پیشنهاد ورود» می‌گیرد
     *     تا وزنش گم نشود. بدون این قاعده مجموع وزن‌ها زیر ۱۰۰٪ می‌ماند و
     *     ماشین‌حساب واریز/برداشت خروجی بی‌معنا می‌دهد.
     */
    private fun buildTargetSlots(
        items: List<PortfolioEntity>,
        targetWeights: TargetWeights
    ): List<TargetSlot> {
        val byCategory = items.groupBy { it.assetCategory }
        val slots = mutableListOf<TargetSlot>()

        for (category in AssetCategory.entries) {
            val categoryWeight = targetWeights.getWeight(category)
            val categoryItems = byCategory[category] ?: emptyList()

            if (categoryItems.isEmpty()) {
                if (categoryWeight > 0.0) {
                    slots.add(
                        TargetSlot(
                            symbol = category.persianName,
                            category = category,
                            item = null,
                            targetWeightPercent = categoryWeight
                        )
                    )
                }
                continue
            }

            // درصدهای دستی نمی‌توانند بیشتر از وزن خودِ طبقه باشند. اگر کاربر مثلاً
            // در طبقه‌ای با وزن ۳۰٪ دو نماد ۲۵٪ و ۲۰٪ بدهد، بدون این محافظ مجموع
            // وزن‌ها از ۱۰۰٪ رد می‌شد و کل ضمانت موتور — و در نتیجه ماشین‌حساب
            // واریز/برداشت و شاخص Tr — بی‌معنا می‌شد. اینجا به نسبت کوچک می‌شوند.
            val rawManualSum = categoryItems.sumOf { max(0.0, it.manualTargetPercent ?: 0.0) }
            val manualScale = if (rawManualSum > categoryWeight && rawManualSum > 0.0) {
                categoryWeight / rawManualSum
            } else {
                1.0
            }
            val manualSum = rawManualSum * manualScale

            val autoItems = categoryItems.filter { it.manualTargetPercent == null }
            val remainingWeight = max(0.0, categoryWeight - manualSum)
            val autoValueSum = autoItems.sumOf { it.currentValue }

            for (item in categoryItems) {
                val share = when {
                    item.manualTargetPercent != null ->
                        max(0.0, item.manualTargetPercent) * manualScale
                    autoValueSum > 0.0 -> (item.currentValue / autoValueSum) * remainingWeight
                    autoItems.isNotEmpty() -> remainingWeight / autoItems.size.toDouble()
                    else -> 0.0
                }
                slots.add(TargetSlot(item.symbol, category, item, share))
            }
        }

        return slots
    }

    /**
     * فاز ۳: موتور بازتعادل سبد.
     * PV، Delta-V، شاخص Tr و سیگنال‌های خرید/فروش را تولید می‌کند.
     */
    fun calculatePortfolio(
        items: List<PortfolioEntity>,
        settings: SettingsEntity
    ): PortfolioCalculationResult {
        val result = calculatePortfolioCore(items, settings)
        if (result.lockedHold == null) return result
        return result.copy(lockBoundTrIndex = min(lockBoundTr(items, settings, result), result.trIndex))
    }

    /**
     * Tr پس از اجرای همه سفارش‌هایی که بدون پول نقد و بدون فروش نماد قفل‌شده
     * ممکن‌اند (برنامه واریزِ صفر)، با همان موتور دوباره اندازه گرفته می‌شود.
     *
     * بازاندازه‌گیری لازم است، نه `postTrIndex` همان برنامه: وزن هدف نمادهای
     * هم‌طبقه به نسبت ارزش روزشان پخش می‌شود، پس بعد از معامله هدف نماد قفل‌شده
     * خودش بالا می‌رود. روی سبد واقعی (طلا ۱۲٪ بالا، درخشان قفل) `postTrIndex`
     * عدد ۴٫۴ می‌داد و Tr واقعی پس از معامله ۴٫۰ بود.
     */
    private fun lockBoundTr(
        items: List<PortfolioEntity>,
        settings: SettingsEntity,
        result: PortfolioCalculationResult
    ): Double {
        val plan = calculateDepositWithdrawal(result, TransactionType.DEPOSIT, 0.0)
        // ردیف‌ها با شناسه به سفارششان وصل می‌شوند؛ اگر شناسه یکتا نباشد (ردیف
        // ذخیره‌نشده) این اتصال قابل اتکا نیست و به تخمین خودِ برنامه بسنده می‌شود.
        if (items.map { it.id }.toSet().size != items.size) return plan.postTrIndex

        val postValueById = plan.orders.associate { it.itemId to it.postTransactionValue }
        val afterTrades = items.map { item ->
            item.copy(currentValue = postValueById[item.id] ?: item.currentValue)
        } + plan.orders
            // خرید در طبقه‌ای که نمادی ندارد هم بخشی از همین برنامه است
            .filter { it.isSuggestedNewPosition && it.postTransactionValue > 0.0 }
            .map {
                PortfolioEntity(
                    id = it.itemId,
                    symbol = it.symbol,
                    assetCategory = it.category,
                    currentValue = it.postTransactionValue
                )
            }
        return calculatePortfolioCore(afterTrades, settings).trIndex
    }

    private fun calculatePortfolioCore(
        items: List<PortfolioEntity>,
        settings: SettingsEntity
    ): PortfolioCalculationResult {
        val targetWeights = calculateTargetWeights(settings)
        val totalPv = items.sumOf { it.currentValue }

        if (items.isEmpty() || totalPv <= 0.0) {
            return PortfolioCalculationResult(
                totalPv = 0.0,
                trIndex = 0.0,
                isRebalanceTriggered = false,
                thresholdLimit = settings.rebalanceThresholdPercent,
                calculatedRt = targetWeights.calculatedRt,
                investorRt = settings.riskTolerance,
                targetWeights = targetWeights,
                categoryBreakdowns = emptyList(),
                symbolAlerts = emptyList(),
                totalBuyAmount = 0.0,
                totalSellAmount = 0.0,
                balancedExecutionAmount = 0.0,
                unallocatedWeightPercent = 0.0
            )
        }

        val itemsByCategory = items.groupBy { it.assetCategory }

        val categoryBreakdowns = AssetCategory.entries.map { category ->
            val categoryItems = itemsByCategory[category] ?: emptyList()
            val catVal = categoryItems.sumOf { it.currentValue }
            val currentPercent = (catVal / totalPv) * 100.0
            val targetPercent = targetWeights.getWeight(category)
            val targetVal = totalPv * (targetPercent / 100.0)

            AssetAllocationBreakdown(
                category = category,
                currentValue = catVal,
                currentWeightPercent = roundToSingleDecimal(currentPercent),
                targetWeightPercent = roundToSingleDecimal(targetPercent),
                targetValue = targetVal,
                deltaValue = catVal - targetVal
            )
        }

        val slots = buildTargetSlots(items, targetWeights)
        val allocatedWeight = slots.sumOf { it.targetWeightPercent }

        // آستانه بی‌تفاوتی: انحراف‌های ناچیز نباید سیگنال بسازند.
        val actionTolerance = totalPv * (settings.rebalanceThresholdPercent / 100.0) * 0.05

        var totalAbsDelta = 0.0
        var sumBuys = 0.0
        var sumSells = 0.0
        val symbolAlerts = mutableListOf<SymbolRebalanceAlert>()
        // ردیف‌های قفل‌شده‌ای که فروششان سرکوب شد، با وزن هدفِ گردنشده — پول نقد
        // لازم از تقسیم بر همین وزن درمی‌آید و گرد کردن، خطا را چند برابر می‌کند.
        val bindingLocks = mutableListOf<LockedPosition>()

        for (slot in slots) {
            val currentValue = slot.item?.currentValue ?: 0.0
            val targetValue = totalPv * (slot.targetWeightPercent / 100.0)
            // مستند بخش ۴: Delta-V = Vr − Va  (مثبت = کسری = خرید)
            val deltaValue = targetValue - currentValue
            val actionAmount = abs(deltaValue)
            val isLocked = slot.item?.isHoldLocked == true

            totalAbsDelta += actionAmount

            val action: RebalanceAction
            var minNotice: String? = null
            var suppressedSell = 0.0

            if (deltaValue > actionTolerance) {
                action = RebalanceAction.BUY
                sumBuys += deltaValue
                minNotice = minOrderNoticeFor(action, actionAmount)
            } else if (deltaValue < -actionTolerance) {
                if (isLocked) {
                    // قفل نگه‌داری: انحراف واقعی است و در Tr شمرده می‌شود، ولی
                    // سیگنال فروش صادر نمی‌شود. راه رسیدن به تعادل، تزریق نقد است.
                    action = RebalanceAction.HOLD
                    suppressedSell = actionAmount
                    bindingLocks.add(
                        LockedPosition(slot.symbol, currentValue, slot.targetWeightPercent)
                    )
                    minNotice =
                        "قفل نگه‌داری روشن است؛ به‌جای فروش، کسری بقیه از پول نقد تأمین می‌شود"
                } else {
                    action = RebalanceAction.SELL
                    sumSells += actionAmount
                    minNotice = minOrderNoticeFor(action, actionAmount)
                }
            } else {
                action = RebalanceAction.HOLD
            }

            if (slot.item == null) {
                // ردیف پیشنهاد ورود: کف اقتصادی ۱۰ میلیون تومان (بخش ۴ مستند)
                minNotice = if (targetValue < MIN_ECONOMIC_POSITION_RIAL) {
                    "وزن این طبقه زیر کف اقتصادی ۱۰ میلیون تومان است؛ فعلاً ورود به آن صرفه ندارد"
                } else {
                    "این طبقه در سبد شما نماد ندارد — یک صندوق از دیده‌بان انتخاب کنید"
                }
            } else if (totalPv >= 1_000_000_000.0 &&
                targetValue < MIN_ECONOMIC_POSITION_RIAL &&
                minNotice == null
            ) {
                minNotice = "ارزش هدف کمتر از ۱۰ میلیون تومان بهینه است"
            }

            val lastPrice = slot.item?.lastPrice ?: 0.0
            val estimatedUnits = estimateUnits(actionAmount, lastPrice)

            symbolAlerts.add(
                SymbolRebalanceAlert(
                    // ردیف واقعی: id مثبت از دیتابیس. پیشنهاد ورود: -۱ تا -۴ بر اساس
                    // طبقه. چون هر طبقه حداکثر یک ردیف پیشنهادی دارد، برخوردی رخ نمی‌دهد.
                    itemId = slot.item?.id ?: -(slot.category.ordinal + 1).toLong(),
                    symbol = slot.symbol,
                    category = slot.category,
                    currentValue = currentValue,
                    targetValue = targetValue,
                    deltaValue = deltaValue,
                    currentWeightPercent = roundToSingleDecimal((currentValue / totalPv) * 100.0),
                    targetWeightPercent = roundToSingleDecimal(slot.targetWeightPercent),
                    action = action,
                    actionAmount = actionAmount,
                    currentQuantity = slot.item?.quantity ?: 0L,
                    estimatedUnits = estimatedUnits,
                    lastPrice = lastPrice,
                    minOrderNotice = minNotice,
                    isSuggestedNewPosition = slot.item == null,
                    isHoldLocked = isLocked,
                    suppressedSellAmount = suppressedSell
                )
            )
        }

        // Tr = (Σ|Va − Vr| / PV) × 100  — مستند PCMR
        // مقایسه با آستانه روی همان عدد گردشده‌ای انجام می‌شود که کاربر می‌بیند و
        // هشدار پس‌زمینه هم با آن تصمیم می‌گیرد. قبلاً اینجا عدد خام مقایسه می‌شد:
        // Tr خام ۴٫۹۶ روی داشبورد «۵٪ — زیر آستانه ۵٪» نشان داده می‌شد، در حالی
        // که نوتیفیکیشن با همان ۵٫۰ گردشده می‌گفت «از آستانه گذشت».
        val trIndex = roundToSingleDecimal((totalAbsDelta / totalPv) * 100.0)

        return PortfolioCalculationResult(
            totalPv = totalPv,
            trIndex = trIndex,
            isRebalanceTriggered = trIndex >= settings.rebalanceThresholdPercent,
            thresholdLimit = settings.rebalanceThresholdPercent,
            calculatedRt = targetWeights.calculatedRt,
            investorRt = settings.riskTolerance,
            targetWeights = targetWeights,
            categoryBreakdowns = categoryBreakdowns,
            symbolAlerts = symbolAlerts.sortedByDescending { it.actionAmount },
            totalBuyAmount = sumBuys,
            totalSellAmount = sumSells,
            balancedExecutionAmount = min(sumBuys, sumSells),
            unallocatedWeightPercent = roundToSingleDecimal(100.0 - allocatedWeight),
            lockedHold = summarizeLockedHold(bindingLocks, totalPv)
        )
    }

    /** یک ردیف قفل‌شده که فروشش سرکوب شده، با وزن هدف گردنشده. */
    private data class LockedPosition(
        val symbol: String,
        val currentValue: Double,
        val targetWeightPercent: Double
    )

    /**
     * پول نقدی که باید وارد سبد شود تا نمادهای قفل‌شده **بدون فروش** به وزن
     * هدفشان برسند.
     *
     * برای یک نماد با ارزش V و وزن هدف w، وزن فعلی‌اش وقتی به هدف می‌رسد که کل
     * سبد به `V ÷ w` رسیده باشد. با چند نماد قفل‌شده، بزرگ‌ترینِ این مقادیر
     * تعیین‌کننده است؛ بقیه در آن نقطه کم‌وزن می‌شوند و خودشان پیشنهاد خرید
     * می‌گیرند، که با قفل تناقضی ندارد — قفل فقط جلوی فروش را می‌گیرد.
     *
     * وزن هدف صفر حالت مرزی است: هیچ مقدار پول نقدی وزنِ یک نماد را به صفر
     * نمی‌رساند، پس آن ردیف علامت‌گذاری می‌شود تا UI صادقانه بگوید این یکی با
     * نقد حل نمی‌شود.
     */
    private fun summarizeLockedHold(
        locks: List<LockedPosition>,
        totalPv: Double
    ): LockedHoldSummary? {
        if (locks.isEmpty()) return null

        var requiredPv = totalPv
        var unreachable = false
        for (lock in locks) {
            val weight = lock.targetWeightPercent / 100.0
            if (weight <= 0.0) {
                unreachable = true
                continue
            }
            requiredPv = max(requiredPv, lock.currentValue / weight)
        }

        return LockedHoldSummary(
            bindingSymbols = locks.map { it.symbol },
            suppressedSellAmount = locks.sumOf { it.currentValue } -
                locks.sumOf { totalPv * (it.targetWeightPercent / 100.0) },
            requiredCashRial = max(0.0, requiredPv - totalPv),
            targetPvRial = requiredPv,
            isUnreachableByCash = unreachable
        )
    }

    /**
     * برنامه خرید با پول نقدِ لازم برای قفل‌ها: «این مبلغ را بیاور و با آن
     * فلان صندوق‌ها را بخر».
     *
     * همان ماشین‌حساب واریز فاز ۴ است، فقط مبلغش را خودِ موتور تعیین می‌کند.
     * چون مبلغ دقیقاً طوری انتخاب شده که ردیف قفل‌شده به وزن هدفش برسد،
     * سفارش آن ردیف در این برنامه صفر درمی‌آید و همه مبلغ صرف بقیه می‌شود.
     *
     * null یعنی قفلی در کار نیست یا مبلغ لازم ناچیز است.
     */
    fun lockedHoldCashPlan(result: PortfolioCalculationResult): DepositWithdrawalResult? {
        val locked = result.lockedHold ?: return null
        if (locked.requiredCashRial <= NEGLIGIBLE_ORDER_RIAL) return null
        return calculateDepositWithdrawal(
            portfolioResult = result,
            type = TransactionType.DEPOSIT,
            transactionAmount = locked.requiredCashRial
        )
    }

    /**
     * فاز ۴: ماشین‌حساب واریز و برداشت.
     *   واریز:   Order_i = (D × Weight_i) + Delta_V_i
     *   برداشت:  Order_i = Delta_V_i − (W × Weight_i)
     * مثبت = خرید، منفی = فروش.
     *
     * وقتی مجموع وزن‌ها ۱۰۰٪ باشد، ارزش هر ردیف پس از تراکنش دقیقاً
     * (PV ± مبلغ) × Weight_i می‌شود، یعنی Tr پس از اجرا صفر است.
     * این ادعا دیگر فرض نمی‌شود؛ `postTrIndex` واقعاً محاسبه می‌گردد.
     */
    fun calculateDepositWithdrawal(
        portfolioResult: PortfolioCalculationResult,
        type: TransactionType,
        transactionAmount: Double
    ): DepositWithdrawalResult {
        val currentPv = portfolioResult.totalPv
        val requested = max(0.0, transactionAmount)
        val alerts = portfolioResult.symbolAlerts

        // برداشت بیشتر از دارایی ممکن نیست — و بخش قفل‌شده هم قابل برداشت نیست،
        // چون از نماد قفل‌شده فروشی پیشنهاد نمی‌شود.
        val lockedValue = alerts.filter { it.isHoldLocked }.sumOf { it.currentValue }
        val withdrawCeiling = max(0.0, currentPv - lockedValue)

        val effective = when (type) {
            TransactionType.DEPOSIT -> requested
            TransactionType.WITHDRAWAL -> min(requested, withdrawCeiling)
        }
        val isClamped = effective < requested
        val isLimitedByLocked =
            type == TransactionType.WITHDRAWAL && requested > withdrawCeiling && lockedValue > 0.0

        val newPv = when (type) {
            TransactionType.DEPOSIT -> currentPv + effective
            TransactionType.WITHDRAWAL -> currentPv - effective
        }

        val frozenIds = resolveFrozenRows(alerts, newPv)
        val frozenValue = alerts.filter { it.itemId in frozenIds }.sumOf { it.currentValue }
        val frozenWeight = alerts.filter { it.itemId in frozenIds }.sumOf { it.targetWeightPercent }
        // «دیگ» و «بودجه وزنی» باقیمانده، پس از کنار گذاشتن ردیف‌های منجمد
        val pot = max(0.0, newPv - frozenValue)
        val budget = 100.0 - frozenWeight

        val orders = mutableListOf<DepositWithdrawalOrder>()
        var postAbsDelta = 0.0

        for (alert in alerts) {
            val isFrozen = alert.itemId in frozenIds
            // ردیف منجمد سر جایش می‌ماند؛ بقیه سهمشان را از دیگ باقیمانده
            // می‌گیرند. بدون قفل، frozen خالی است و این دقیقاً همان
            // `newPv × Weight_i` فرمول فاز ۴ می‌شود.
            val plannedValue = when {
                isFrozen -> alert.currentValue
                budget > 0.0 -> pot * (alert.targetWeightPercent / budget)
                else -> 0.0
            }
            val orderAmount = plannedValue - alert.currentValue

            val action = when {
                abs(orderAmount) < NEGLIGIBLE_ORDER_RIAL -> RebalanceAction.HOLD
                orderAmount > 0 -> RebalanceAction.BUY
                else -> RebalanceAction.SELL
            }

            val postValue = alert.currentValue + orderAmount
            // مرجع همان وزن هدف اصلی است، نه وزن بازتوزیع‌شده: اگر قفلی در کار
            // باشد Tr پس از اجرا صفر نمی‌شود و گزارش باید همین را بگوید.
            postAbsDelta += abs(postValue - (newPv * (alert.targetWeightPercent / 100.0)))

            val absAmount = abs(orderAmount)

            orders.add(
                DepositWithdrawalOrder(
                    itemId = alert.itemId,
                    symbol = alert.symbol,
                    category = alert.category,
                    orderAmount = orderAmount,
                    action = action,
                    absoluteAmount = absAmount,
                    currentWeight = alert.currentWeightPercent,
                    targetWeight = alert.targetWeightPercent,
                    postTransactionValue = postValue,
                    // سفارش در کارگزاری بر حسب تعداد واحد ثبت می‌شود، نه مبلغ.
                    estimatedUnits = estimateUnits(absAmount, alert.lastPrice),
                    minOrderNotice = minOrderNoticeFor(action, absAmount),
                    isSuggestedNewPosition = alert.isSuggestedNewPosition,
                    isHoldLocked = alert.isHoldLocked
                )
            )
        }

        val postTr = if (newPv > 0.0) (postAbsDelta / newPv) * 100.0 else 0.0

        return DepositWithdrawalResult(
            type = type,
            inputAmount = requested,
            effectiveAmount = effective,
            isAmountClamped = isClamped,
            previousPv = currentPv,
            newPv = newPv,
            orders = orders.sortedByDescending { it.absoluteAmount },
            postTrIndex = roundToSingleDecimal(postTr),
            frozenSymbols = alerts.filter { it.itemId in frozenIds }.map { it.symbol },
            isLimitedByLockedPositions = isLimitedByLocked
        )
    }

    /**
     * ردیف‌هایی که باید ارزششان ثابت بماند: نمادهای قفل‌شده‌ای که سهم عادی‌شان
     * از سبدِ پس از تراکنش کمتر از ارزش فعلی‌شان است، یعنی فرمول عادی برایشان
     * فروش می‌نوشت.
     *
     * منجمد کردن یک ردیف، دیگِ باقیمانده را نسبت به بودجه وزنی کوچک‌تر می‌کند
     * و می‌تواند ردیف قفل‌شده دیگری را هم اضافه‌وزن کند. پس تا رسیدن به نقطه
     * ثابت تکرار می‌شود — حداکثر به تعداد ردیف‌های قفل‌شده.
     */
    private fun resolveFrozenRows(
        alerts: List<SymbolRebalanceAlert>,
        newPv: Double
    ): Set<Long> {
        val lockedRows = alerts.filter { it.isHoldLocked }
        if (lockedRows.isEmpty()) return emptySet()

        val frozen = mutableSetOf<Long>()
        repeat(lockedRows.size) {
            val frozenValue = alerts.filter { it.itemId in frozen }.sumOf { it.currentValue }
            val frozenWeight = alerts.filter { it.itemId in frozen }.sumOf { it.targetWeightPercent }
            val pot = newPv - frozenValue
            val budget = 100.0 - frozenWeight
            if (budget <= 0.0) return frozen

            val next = lockedRows.firstOrNull { row ->
                row.itemId !in frozen &&
                    pot * (row.targetWeightPercent / budget) < row.currentValue
            } ?: return frozen
            frozen.add(next.itemId)
        }
        return frozen
    }

    /**
     * گرد کردن وزن‌ها به مضارب ۵٪ یا ۱۰٪، با تضمین RT_Final ≤ RT_Initial.
     */
    fun roundWeightsToMultiples(
        raw: TargetWeights,
        step: Double = 5.0,
        investorRt: Double,
        emlG: Double,
        emlS: Double,
        emlM: Double
    ): TargetWeights {
        var rGold = (Math.round(raw.goldWeight / step) * step).coerceIn(0.0, 100.0)
        var rStock = (Math.round(raw.stockWeight / step) * step).coerceIn(0.0, 100.0)
        var rMixed = (Math.round(raw.mixedWeight / step) * step).coerceIn(0.0, 100.0)

        while (rGold + rStock + rMixed > 100.0) {
            if (rStock >= step) rStock -= step
            else if (rGold >= step) rGold -= step
            else if (rMixed >= step) rMixed -= step
            else break
        }

        var calcRt = (rGold / 100.0) * emlG + (rStock / 100.0) * emlS + (rMixed / 100.0) * emlM
        while (calcRt > investorRt && (rGold > 0 || rStock > 0 || rMixed > 0)) {
            if (rStock >= step) rStock -= step
            else if (rGold >= step) rGold -= step
            else if (rMixed >= step) rMixed -= step
            else break
            calcRt = (rGold / 100.0) * emlG + (rStock / 100.0) * emlS + (rMixed / 100.0) * emlM
        }

        return TargetWeights(
            goldWeight = rGold,
            stockWeight = rStock,
            mixedWeight = rMixed,
            fixedIncomeWeight = max(0.0, 100.0 - (rGold + rStock + rMixed)),
            calculatedRt = roundToSingleDecimal(calcRt),
            isAggressive = raw.isAggressive,
            isAdjustedDown = true
        )
    }

    private fun roundToSingleDecimal(value: Double): Double {
        return (value * 10.0).roundToLong() / 10.0
    }
}
