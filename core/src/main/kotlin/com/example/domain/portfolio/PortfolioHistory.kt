package com.example.domain.portfolio

import com.example.data.model.AssetCategory
import com.example.data.model.HoldingSnapshotEntity
import com.example.data.model.PortfolioEntity
import com.example.data.model.PortfolioSnapshotEntity

/**
 * تاریخچه سبد — ساختن عکس روزانه و خواندن روندها از آن، بدون وابستگی به اندروید.
 *
 * سود هر روز از [PortfolioPnl] می‌آید، نه از یک فرمول تازه: همان عددی که
 * داشبورد آن روز نشان داده، همان عددی است که بعداً روی نمودار دیده می‌شود.
 */
object PortfolioHistory {

    /** عکس یک روز: جمع کل و ترکیب نمادها. */
    data class Capture(
        val snapshot: PortfolioSnapshotEntity,
        val holdings: List<HoldingSnapshotEntity>
    )

    /**
     * از ردیف‌های فعلی سبد عکس می‌سازد، یا null برای سبد خالی — یک پاک کردن
     * موقت نباید در نمودار مثل افت واقعی دیده شود.
     *
     * ردیف‌های تکراری یک نماد جمع می‌شوند، چون کلید جدول (روز، نماد) است و
     * برای کاربر هم «وزن کمند» یک عدد است، نه دو.
     */
    fun capture(
        items: List<PortfolioEntity>,
        day: String,
        epochMs: Long,
        trIndex: Double
    ): Capture? {
        val total = items.sumOf { it.currentValue }
        if (items.isEmpty() || total <= 0.0) return null

        val pnl = PortfolioPnl.calculate(items)

        val holdings = items
            .groupBy { it.symbol.trim() }
            .filterKeys { it.isNotEmpty() }
            .map { (symbol, rows) ->
                HoldingSnapshotEntity(
                    day = day,
                    symbol = symbol,
                    // دسته ردیف پرارزش‌تر؛ ردیف‌های تکراری معمولاً یک دسته دارند
                    assetCategory = rows.maxBy { it.currentValue }.assetCategory,
                    quantity = rows.sumOf { it.quantity },
                    valueRial = rows.sumOf { it.currentValue },
                    principalRial = rows
                        .filter { PortfolioPnl.hasBasis(it) }
                        .sumOf { it.quantity.toDouble() * it.averagePrice }
                )
            }
            .sortedByDescending { it.valueRial }

        return Capture(
            snapshot = PortfolioSnapshotEntity(
                day = day,
                epochMs = epochMs,
                totalValueRial = total,
                principalRial = pnl.principalRial,
                basisValueRial = pnl.basisValueRial,
                trIndex = trIndex,
                symbolCount = holdings.size
            ),
            holdings = holdings
        )
    }

    // ------------------------------------------------------------------
    // روند سود و زیان
    // ------------------------------------------------------------------

    data class PnlPoint(
        val day: String,
        val profitRial: Double,
        val profitPercent: Double,
        val principalRial: Double
    )

    /**
     * سود و زیان روی کاغذ در هر روز ثبت‌شده.
     *
     * روزهایی که مبنای خرید نداشتند (یا پیش از ثبت [PortfolioSnapshotEntity.basisValueRial]
     * ذخیره شدند) کنار می‌روند: سود آن‌ها نامعلوم است، نه صفر.
     */
    fun pnlSeries(snapshots: List<PortfolioSnapshotEntity>): List<PnlPoint> =
        snapshots
            .sortedBy { it.day }
            .filter { it.principalRial > 0.0 && it.basisValueRial > 0.0 }
            .map {
                val profit = it.basisValueRial - it.principalRial
                PnlPoint(
                    day = it.day,
                    profitRial = profit,
                    profitPercent = (profit / it.principalRial) * 100.0,
                    principalRial = it.principalRial
                )
            }

    // ------------------------------------------------------------------
    // ترکیب سبد
    // ------------------------------------------------------------------

    /** وزن هر نماد (درصد از کل سبد) در یک روز. */
    data class CompositionDay(
        val day: String,
        val totalValueRial: Double,
        /** نماد → وزن درصدی؛ به ترتیب ارزش، بزرگ‌ترین اول */
        val weights: Map<String, Double>
    )

    fun composition(holdings: List<HoldingSnapshotEntity>): List<CompositionDay> =
        holdings
            .groupBy { it.day }
            .toSortedMap()
            .map { (day, rows) ->
                val total = rows.sumOf { it.valueRial }
                CompositionDay(
                    day = day,
                    totalValueRial = total,
                    weights = rows
                        .sortedByDescending { it.valueRial }
                        .associate {
                            it.symbol to if (total > 0.0) it.valueRial / total * 100.0 else 0.0
                        }
                )
            }

    /** دسته آخرین باری که هر نماد در سبد دیده شده، برای رنگ نمودار. */
    fun latestCategories(holdings: List<HoldingSnapshotEntity>): Map<String, AssetCategory> =
        holdings
            .sortedBy { it.day }
            .associate { it.symbol to it.assetCategory }

    // ------------------------------------------------------------------
    // تغییرات بین دو عکس
    // ------------------------------------------------------------------

    data class QuantityChange(val symbol: String, val fromQuantity: Long, val toQuantity: Long)

    /** آنچه بین دو روز ثبت‌شده پشت‌سرهم در سبد عوض شد. */
    data class Change(
        val fromDay: String,
        val toDay: String,
        val added: List<String>,
        val removed: List<String>,
        val quantityChanges: List<QuantityChange>
    ) {
        val isEmpty: Boolean
            get() = added.isEmpty() && removed.isEmpty() && quantityChanges.isEmpty()
    }

    /**
     * تغییرات سبد، جدیدترین اول. روزهایی که ترکیب عوض نشده (فقط قیمت‌ها تکان
     * خورده) حذف می‌شوند تا فهرست فقط تصمیم‌های واقعی را نشان دهد.
     *
     * تغییر تعداد فقط وقتی گزارش می‌شود که هر دو روز تعداد داشته باشند؛ ردیفی
     * که دستی و بدون تعداد وارد شده، «از صفر به ۱۰۰» تغییر ساختگی نمی‌سازد.
     */
    fun changes(holdings: List<HoldingSnapshotEntity>): List<Change> {
        val byDay = holdings.groupBy { it.day }.toSortedMap()
        val days = byDay.keys.toList()
        if (days.size < 2) return emptyList()

        return days.zipWithNext { fromDay, toDay ->
            val before = byDay.getValue(fromDay).associateBy { it.symbol }
            val after = byDay.getValue(toDay).associateBy { it.symbol }

            Change(
                fromDay = fromDay,
                toDay = toDay,
                added = (after.keys - before.keys).sortedByDescending { after.getValue(it).valueRial },
                removed = (before.keys - after.keys).sortedByDescending { before.getValue(it).valueRial },
                quantityChanges = (before.keys intersect after.keys)
                    .mapNotNull { symbol ->
                        val from = before.getValue(symbol).quantity
                        val to = after.getValue(symbol).quantity
                        if (from > 0L && to > 0L && from != to) QuantityChange(symbol, from, to) else null
                    }
                    .sortedByDescending { after.getValue(it.symbol).valueRial }
            )
        }
            .filterNot { it.isEmpty }
            .reversed()
    }
}
