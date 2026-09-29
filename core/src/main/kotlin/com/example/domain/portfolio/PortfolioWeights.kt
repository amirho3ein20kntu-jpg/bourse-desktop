package com.example.domain.portfolio

import com.example.data.model.PortfolioEntity
import com.example.data.model.SymbolRebalanceAlert

/**
 * وزن هر نماد در سبد، برای نمایش کنار همان نماد در داشبورد.
 *
 * وزن فعلی از خود ردیف‌ها حساب می‌شود (ارزش نماد ÷ ارزش کل سبد) تا با هر عددی
 * که بالای داشبورد به‌عنوان «دارایی کل» آمده بخواند. وزن هدف از خروجی موتور
 * PCMR می‌آید و فقط وقتی هست که موتور برای آن ردیف هدف ساخته باشد.
 */
data class SymbolWeight(
    /** درصد ارزش این نماد از کل سبد، ۰ تا ۱۰۰. */
    val currentPercent: Double,
    /** درصد هدف موتور برای این نماد؛ null یعنی هدفی در دست نیست. */
    val targetPercent: Double?
)

object PortfolioWeights {

    /** کلید نتیجه، `id` ردیف سبد است. سبد خالی یا بی‌ارزش، نقشه خالی می‌دهد. */
    fun forItems(
        items: List<PortfolioEntity>,
        alerts: List<SymbolRebalanceAlert>
    ): Map<Long, SymbolWeight> {
        val total = items.sumOf { it.currentValue.coerceAtLeast(0.0) }
        if (total <= 0.0) return emptyMap()

        // ردیف‌های «پیشنهاد ورود» id منفی دارند و به هیچ ردیف واقعی نمی‌خورند.
        val targetById = alerts
            .filter { !it.isSuggestedNewPosition }
            .associate { it.itemId to it.targetWeightPercent }

        return items.associate { item ->
            item.id to SymbolWeight(
                currentPercent = item.currentValue.coerceAtLeast(0.0) / total * 100.0,
                targetPercent = targetById[item.id]
            )
        }
    }
}
