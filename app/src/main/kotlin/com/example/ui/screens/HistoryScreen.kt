package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.domain.portfolio.PortfolioHistory
import com.example.ui.components.PortfolioGrowthChart
import com.example.ui.theme.SignalBuy
import com.example.ui.theme.SignalSell
import com.example.ui.util.Formatters
import com.example.ui.util.JalaliDate
import com.example.ui.viewmodel.PcmrUiState
import kotlin.math.abs

/** بیشترین تعداد نمادی که در نمودار ترکیب رنگ جدا می‌گیرند؛ بقیه «سایر» می‌شوند. */
private const val MAX_COMPOSITION_SYMBOLS = 7

private const val OTHERS_LABEL = "سایر"

/**
 * رنگ‌های نمودار ترکیب. عمداً رنگ دسته دارایی نیست: دو صندوق سهامی کنار هم
 * باید از هم قابل تشخیص باشند.
 */
private val compositionPalette = listOf(
    Color(0xFF2563EB),
    Color(0xFFF59E0B),
    Color(0xFF10B981),
    Color(0xFF8B5CF6),
    Color(0xFFEF4444),
    Color(0xFF0EA5E9),
    Color(0xFFEC4899)
)
private val othersColor = Color(0xFF9CA3AF)

/**
 * تاریخچه سبد: روند سود و زیان، روند ارزش، ترکیب صندوق‌ها در طول زمان و
 * فهرست تغییرات (نماد اضافه، حذف یا تعداد عوض‌شده).
 *
 * هر بار که اکسل بارگذاری یا قیمت‌ها همگام شوند یک عکس از همان روز ثبت
 * می‌شود؛ این صفحه فقط از همان عکس‌ها می‌خواند.
 */
@Composable
fun HistoryScreen(
    uiState: PcmrUiState,
    modifier: Modifier = Modifier
) {
    val pnlSeries = remember(uiState.snapshots) { PortfolioHistory.pnlSeries(uiState.snapshots) }
    val composition = remember(uiState.holdingSnapshots) {
        PortfolioHistory.composition(uiState.holdingSnapshots)
    }
    val changes = remember(uiState.holdingSnapshots) {
        PortfolioHistory.changes(uiState.holdingSnapshots)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { PnlTrendCard(pnlSeries) }
        item { PortfolioGrowthChart(snapshots = uiState.snapshots) }
        item { CompositionCard(composition) }
        item {
            HistoryCard(title = "تغییرات ترکیب سبد") {
                if (changes.isEmpty()) {
                    HintText(
                        if (composition.size < 2) {
                            "برای دیدن تغییرات، دست‌کم دو روز بارگذاری لازم است."
                        } else {
                            "از اولین روز ثبت‌شده، نمادی اضافه یا حذف نشده و تعداد واحدها عوض نشده."
                        }
                    )
                }
            }
        }
        items(changes, key = { it.toDay }) { change -> ChangeCard(change) }
    }
}

// ----------------------------------------------------------------------
// سود و زیان
// ----------------------------------------------------------------------

@Composable
private fun PnlTrendCard(series: List<PortfolioHistory.PnlPoint>) {
    HistoryCard(title = "روند سود و زیان") {
        if (series.size < 2) {
            HintText(
                if (series.isEmpty()) {
                    "هنوز روزی با قیمت خرید ثبت نشده. سود از ستون «میانگین خرید با لحاظ کارمزد» " +
                            "اکسل کارگزاری حساب می‌شود؛ با هر بارگذاری یک نقطه اضافه می‌شود."
                } else {
                    "فقط سود یک روز ثبت شده. برای رسم روند، حداقل دو روز لازم است."
                }
            )
            return@HistoryCard
        }

        val last = series.last()
        val first = series.first()
        val lastColor = if (last.profitRial >= 0.0) SignalBuy else SignalSell
        val move = last.profitRial - first.profitRial

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${signed(last.profitRial)} • ${signedPercent(last.profitPercent)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = lastColor
            )
            Text(
                text = JalaliDate.formatLong(last.day),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "${if (move >= 0.0) "بهتر" else "بدتر"} به اندازه " +
                    "${Formatters.formatCompactToman(abs(move))} نسبت به ${JalaliDate.formatLong(first.day)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LineChart(
            values = series.map { it.profitRial },
            color = lastColor,
            showZeroLine = true
        )

        AxisLabels(
            start = JalaliDate.formatShort(first.day),
            end = JalaliDate.formatShort(last.day)
        )
        HintText(
            "سود روی کاغذ دارایی‌های همان روز است؛ سود فروش‌های قبلی و واریز و برداشت در آن نیست."
        )
    }
}

@Composable
private fun LineChart(values: List<Double>, color: Color, showZeroLine: Boolean) {
    val zeroLineColor = MaterialTheme.colorScheme.outline
    val minValue = if (showZeroLine) minOf(values.min(), 0.0) else values.min()
    val maxValue = if (showZeroLine) maxOf(values.max(), 0.0) else values.max()
    // وقتی همه مقادیر برابرند، دامنه صفر می‌شود و تقسیم بر صفر پیش می‌آید
    val span = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(top = 12.dp)
    ) {
        fun yOf(value: Double) = size.height - ((value - minValue) / span).toFloat() * size.height

        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val points = values.mapIndexed { index, value -> Offset(index * stepX, yOf(value)) }

        if (showZeroLine) {
            val zeroY = yOf(0.0)
            drawLine(
                color = zeroLineColor,
                start = Offset(0f, zeroY),
                end = Offset(size.width, zeroY),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )
        }

        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path = line, color = color, style = Stroke(width = 3f))
        drawCircle(color = color, radius = 6f, center = points.last())
    }
}

// ----------------------------------------------------------------------
// ترکیب صندوق‌ها
// ----------------------------------------------------------------------

private data class CompositionSeries(val label: String, val color: Color, val weights: List<Double>)

/**
 * پرارزش‌ترین نمادها (بر اساس بیشترین وزنی که در کل تاریخچه داشته‌اند) رنگ
 * خودشان را می‌گیرند و بقیه در «سایر» جمع می‌شوند. معیار «بیشترین وزن» است نه
 * «وزن امروز»، تا صندوقی که فروخته شده هم در نمودار دیده شود.
 */
private fun buildCompositionSeries(days: List<PortfolioHistory.CompositionDay>): List<CompositionSeries> {
    val allSymbols = days.flatMap { it.weights.keys }.distinct()
    val ranked = allSymbols.sortedByDescending { symbol -> days.maxOf { it.weights[symbol] ?: 0.0 } }
    val featured = ranked.take(MAX_COMPOSITION_SYMBOLS)
    val rest = ranked.drop(MAX_COMPOSITION_SYMBOLS).toSet()

    val series = featured.mapIndexed { index, symbol ->
        CompositionSeries(
            label = symbol,
            color = compositionPalette[index % compositionPalette.size],
            weights = days.map { it.weights[symbol] ?: 0.0 }
        )
    }
    if (rest.isEmpty()) return series
    return series + CompositionSeries(
        label = OTHERS_LABEL,
        color = othersColor,
        weights = days.map { day -> rest.sumOf { day.weights[it] ?: 0.0 } }
    )
}

@Composable
private fun CompositionCard(days: List<PortfolioHistory.CompositionDay>) {
    HistoryCard(title = "ترکیب صندوق‌ها در طول زمان") {
        if (days.isEmpty()) {
            HintText("هنوز ترکیبی ثبت نشده. با بارگذاری بعدی اکسل، اولین روز ثبت می‌شود.")
            return@HistoryCard
        }

        val series = remember(days) { buildCompositionSeries(days) }
        val gap = MaterialTheme.colorScheme.surface

        Text(
            text = "هر ستون یک روز بارگذاری است؛ ارتفاع هر رنگ، وزن آن صندوق از کل سبد.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(top = 12.dp)
        ) {
            val slot = size.width / days.size
            val barWidth = (slot * 0.7f).coerceAtMost(48.dp.toPx())
            days.indices.forEach { dayIndex ->
                val left = dayIndex * slot + (slot - barWidth) / 2f
                var bottom = size.height
                series.forEach { s ->
                    val h = (s.weights[dayIndex] / 100.0).toFloat() * size.height
                    if (h > 0f) {
                        drawRect(color = s.color, topLeft = Offset(left, bottom - h), size = Size(barWidth, h))
                        // خط نازک بین قطعه‌ها تا دو رنگ نزدیک در هم نروند
                        drawLine(gap, Offset(left, bottom - h), Offset(left + barWidth, bottom - h), 1.5f)
                        bottom -= h
                    }
                }
            }
        }

        AxisLabels(
            start = JalaliDate.formatShort(days.first().day),
            end = JalaliDate.formatShort(days.last().day)
        )

        Spacer(modifier = Modifier.height(8.dp))
        series.forEach { s -> CompositionLegendRow(s) }
    }
}

@Composable
private fun CompositionLegendRow(series: CompositionSeries) {
    val first = series.weights.first()
    val last = series.weights.last()
    val delta = last - first

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(series.color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = series.label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = when {
                last == 0.0 && first > 0.0 -> "از سبد خارج شد"
                else -> Formatters.formatPercent(last)
            },
            style = MaterialTheme.typography.bodySmall
        )
        if (series.weights.size > 1 && abs(delta) >= 0.05) {
            Text(
                text = "  (${if (delta > 0) "+" else "−"}${Formatters.toPersianDigits(
                    String.format(java.util.Locale.US, "%.1f", abs(delta))
                )} واحد درصد)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ----------------------------------------------------------------------
// فهرست تغییرات
// ----------------------------------------------------------------------

@Composable
private fun ChangeCard(change: PortfolioHistory.Change) {
    HistoryCard(title = JalaliDate.formatLong(change.toDay)) {
        Text(
            text = "نسبت به ${JalaliDate.formatLong(change.fromDay)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (change.added.isNotEmpty()) {
            ChangeLine("اضافه شد: ${change.added.joinToString("، ")}", SignalBuy)
        }
        if (change.removed.isNotEmpty()) {
            ChangeLine("حذف شد: ${change.removed.joinToString("، ")}", SignalSell)
        }
        change.quantityChanges.forEach { q ->
            val up = q.toQuantity > q.fromQuantity
            ChangeLine(
                "${q.symbol}: ${Formatters.formatNumber(q.fromQuantity)} ← ${Formatters.formatNumber(q.toQuantity)} واحد",
                if (up) SignalBuy else SignalSell
            )
        }
    }
}

@Composable
private fun ChangeLine(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// ----------------------------------------------------------------------
// اجزای مشترک
// ----------------------------------------------------------------------

@Composable
private fun HistoryCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            content()
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/**
 * برچسب اول و آخر محور زمان. نمودارها زمان را از چپ به راست می‌کشند، پس این
 * ردیف هم باید چپ‌به‌راست باشد؛ در چیدمان راست‌به‌چپ اپ، تاریخ قدیمی زیر نقطه
 * جدید می‌افتاد.
 */
@Composable
private fun AxisLabels(start: String, end: String) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AxisLabelsRow(start, end)
    }
}

@Composable
private fun AxisLabelsRow(start: String, end: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = start,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = end,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun signed(amountRial: Double): String =
    (if (amountRial >= 0.0) "+" else "−") + Formatters.formatCompactToman(abs(amountRial))

private fun signedPercent(percent: Double): String =
    (if (percent >= 0.0) "+" else "−") + Formatters.formatPercent(abs(percent))
