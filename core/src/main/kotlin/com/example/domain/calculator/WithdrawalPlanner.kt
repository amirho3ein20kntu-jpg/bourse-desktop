package com.example.domain.calculator

import com.example.data.model.AssetCategory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * «برای برداشت X تومان، از کدام صندوق‌ها و چقدر بفروشم؟»
 *
 * ماشین‌حساب قبلی مبلغ را به نسبت وزن بین **همه** ردیف‌ها پخش می‌کرد. از نظر
 * ریاضی درست بود ولی در عمل غیرقابل اجرا: روی یک سبد ۱۰ نمادی، برداشت ۱۰ میلیون
 * تومان یعنی ده سفارش یک‌میلیونی — که بیشترشان زیر کف فروش بورس‌اند و اصلاً ثبت
 * نمی‌شوند. کاربر می‌ماند با فهرستی که نمی‌تواند اجرا کند.
 *
 * این برنامه‌ریز به‌جای پخش یکنواخت، **از پُروزن‌ترین صندوق‌ها شروع می‌کند**؛
 * یعنی همان پولی که لازم دارید را از جایی برمی‌دارد که سبد را به تعادل نزدیک‌تر
 * می‌کند، و هر سفارش یا به کف بازار می‌رسد یا اصلاً ساخته نمی‌شود.
 */
object WithdrawalPlanner {

    /** یک ردیف سبد، به شکلی که این برنامه‌ریز لازم دارد. */
    data class Holding(
        val itemId: Long,
        val symbol: String,
        val category: AssetCategory,
        val currentValueRial: Double,
        val targetWeightPercent: Double,
        val unitPriceRial: Double,
        /** قفل نگه‌داری: از این ردیف هیچ فروشی پیشنهاد نمی‌شود. */
        val isHoldLocked: Boolean = false
    )

    data class WithdrawalLeg(
        val itemId: Long,
        val symbol: String,
        val category: AssetCategory,
        /** مبلغی که از این صندوق فروخته می‌شود (ریال) */
        val amountRial: Double,
        val estimatedUnits: Long,
        val valueBeforeRial: Double,
        val valueAfterRial: Double,
        /** true یعنی این ردیف کامل بسته می‌شود */
        val closesPosition: Boolean,
        /** true یعنی باقیمانده زیر کف اقتصادی ۱۰ میلیون تومان می‌ماند */
        val leavesStub: Boolean
    )

    data class Plan(
        val requestedRial: Double,
        /** مبلغی که واقعاً با سفارش‌های اجراشدنی پوشش داده شد */
        val coveredRial: Double,
        val legs: List<WithdrawalLeg>,
        /** شاخص انحراف سبد پس از اجرای این برنامه */
        val postTrIndex: Double,
        /** توضیح وقتی نتوانستیم کل مبلغ را پوشش دهیم */
        val shortfallNotice: String? = null
    ) {
        val isFullyCovered: Boolean get() = shortfallNotice == null
    }

    /**
     * @param holdings ردیف‌های واقعی سبد (ردیف «پیشنهاد ورود» را نفرستید).
     * @param withdrawalRial مبلغ درخواستی برداشت.
     */
    fun plan(holdings: List<Holding>, withdrawalRial: Double): Plan {
        val totalPv = holdings.sumOf { it.currentValueRial }
        val requested = max(0.0, withdrawalRial)

        if (holdings.isEmpty() || totalPv <= 0.0 || requested <= 0.0) {
            return Plan(requested, 0.0, emptyList(), 0.0, null)
        }

        // برداشت بیشتر از دارایی ممکن نیست
        val target = min(requested, totalPv)
        val newPv = totalPv - target

        // هر صندوق پس از برداشت چقدر «باید» داشته باشد
        val idealAfter = holdings.associate { holding ->
            holding.itemId to newPv * (holding.targetWeightPercent / 100.0)
        }

        // مازاد هر صندوق نسبت به وضعیت مطلوبِ پس از برداشت.
        // برداشتن از این‌ها هم پول را تأمین می‌کند هم سبد را متعادل‌تر می‌کند.
        val priority = holdings.sortedWith(
            compareByDescending<Holding> {
                max(0.0, it.currentValueRial - (idealAfter[it.itemId] ?: 0.0))
            }.thenByDescending { it.currentValueRial }
        )

        val allocation = LinkedHashMap<Long, Double>()
        var remaining = target

        /**
         * یک سفارش فقط وقتی ساخته می‌شود که **قابل ثبت** باشد: یا به کف فروش
         * بورس برسد، یا کل موجودی آن نماد را ببندد. سفارش زیر کف اصلاً ساخته
         * نمی‌شود تا مبلغش برای نماد بعدی باقی بماند و آنجا به یک سفارش
         * درست‌وحسابی تبدیل شود.
         */
        fun take(holding: Holding, cap: Double) {
            if (remaining <= 0.0) return
            // نماد قفل‌شده فروخته نمی‌شود، هرچقدر هم اضافه‌وزن باشد. پیش از این
            // دقیقاً همین ردیف (مثلاً طلای قفل‌شده) چون بیشترین مازاد را داشت
            // اولین پیشنهاد فروش برنامه برداشت می‌شد.
            if (holding.isHoldLocked) return
            val already = allocation[holding.itemId] ?: 0.0
            val room = min(max(0.0, cap - already), holding.currentValueRial - already)
            if (room <= 0.0) return

            val chunk = min(room, remaining)
            if (chunk <= 0.0) return

            val closesPosition = already + chunk >= holding.currentValueRial - 1.0
            if (chunk < PcmrEngine.MIN_SELL_ORDER_RIAL && !closesPosition) return

            allocation[holding.itemId] = already + chunk
            remaining -= chunk
        }

        // فاز ۱: فقط تا حد مازاد — این پول را از جایی برمی‌دارد که سبد را
        // به تعادل نزدیک‌تر می‌کند.
        for (holding in priority) {
            if (remaining <= 0.0) break
            take(holding, idealAfter[holding.itemId]?.let { holding.currentValueRial - it } ?: 0.0)
        }

        // فاز ۲: اگر مازادها کافی نبود (یا هیچ‌کدام به کف بازار نمی‌رسیدند)،
        // از همان ترتیب اولویت ولی این بار تا سقف کل موجودی برداشته می‌شود.
        for (holding in priority) {
            if (remaining <= 0.0) break
            take(holding, holding.currentValueRial)
        }

        val byId = holdings.associateBy { it.itemId }
        val feasible = allocation

        val legs = feasible.mapNotNull { (itemId, amount) ->
            val holding = byId[itemId] ?: return@mapNotNull null
            if (amount <= 0.0) return@mapNotNull null

            val after = max(0.0, holding.currentValueRial - amount)
            val closes = after < 1.0

            WithdrawalLeg(
                itemId = itemId,
                symbol = holding.symbol,
                category = holding.category,
                amountRial = amount,
                estimatedUnits = if (holding.unitPriceRial > 0.0) {
                    Math.round(amount / holding.unitPriceRial)
                } else 0L,
                valueBeforeRial = holding.currentValueRial,
                valueAfterRial = after,
                closesPosition = closes,
                leavesStub = !closes && after < PcmrEngine.MIN_ECONOMIC_POSITION_RIAL
            )
        }.sortedByDescending { it.amountRial }

        val covered = legs.sumOf { it.amountRial }
        val actualNewPv = totalPv - covered

        // انحراف سبد پس از اجرای همین برنامه — نه یک ادعای نظری
        val postTr = if (actualNewPv > 0.0) {
            val absDelta = holdings.sumOf { holding ->
                val sold = legs.firstOrNull { it.itemId == holding.itemId }?.amountRial ?: 0.0
                val after = holding.currentValueRial - sold
                val ideal = actualNewPv * (holding.targetWeightPercent / 100.0)
                abs(after - ideal)
            }
            (absDelta / actualNewPv) * 100.0
        } else {
            0.0
        }

        val shortfall = target - covered
        val notice = when {
            requested > totalPv ->
                "مبلغ درخواستی از کل دارایی بیشتر است؛ حداکثر قابل برداشت اعمال شد."
            shortfall > 1_000.0 ->
                "با رعایت حداقل فروش بورس، ${formatToman(shortfall)} از مبلغ درخواستی " +
                        "قابل تأمین نبود. مبلغ برداشت را کمی تغییر دهید یا یک نماد را کامل ببندید."
            else -> null
        }

        return Plan(
            requestedRial = requested,
            coveredRial = covered,
            legs = legs,
            postTrIndex = Math.round(postTr * 10.0) / 10.0,
            shortfallNotice = notice
        )
    }

    private fun formatToman(rial: Double): String {
        val toman = rial / 10.0
        return when {
            toman >= 1_000_000 -> "${Math.round(toman / 1_000_000)} میلیون تومان"
            toman >= 1_000 -> "${Math.round(toman / 1_000)} هزار تومان"
            else -> "${Math.round(toman)} تومان"
        }
    }
}
