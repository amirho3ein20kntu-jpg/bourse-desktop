package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FundRankingItem
import com.example.data.model.ReturnPeriod
import com.example.domain.ranking.ReturnHeatmap
import com.example.ui.theme.SignalBuy
import com.example.ui.theme.SignalSell
import com.example.ui.util.Formatters

private val SYMBOL_COLUMN_WIDTH = 96.dp
private val VALUE_COLUMN_WIDTH = 78.dp
private val CONSISTENCY_COLUMN_WIDTH = 92.dp

/**
 * چند ردیف بدون درخواست کاربر نشان داده می‌شود.
 *
 * این جدول داخل یک `item` از LazyColumn است، یعنی همه ردیف‌هایش **یک‌جا**
 * ساخته می‌شوند و تنبل نیستند. با فیلتر «همه صندوق‌ها» این یعنی حدود ۳۰۰
 * ردیف × ۶ سلول در یک نوبت — که باز کردن تب دیده‌بان را کند می‌کرد. جدول
 * برای **مقایسه** است، نه فهرست کامل؛ فهرست کامل پایین‌تر در همان صفحه هست.
 */
private const val DEFAULT_VISIBLE_ROWS = 15

/**
 * جدول مقایسه بازدهی صندوق‌ها.
 *
 * رنگ‌ها **مقیاس دوسویه‌اند**: سبز برای سود، قرمز برای زیان، و نقطه میانی روی
 * صفر — نه روی میانگین ستون. در ستونی که همه بازدهی‌ها مثبت‌اند، ضعیف‌ترین
 * صندوق کم‌رنگِ سبز دیده می‌شود، نه قرمز.
 *
 * شدت رنگ درون **هر ستون** جداگانه حساب می‌شود؛ بازدهی سالانه و یک‌ماهه
 * هم‌مقیاس نیستند و رنگ‌آمیزی مشترک، مقایسه را بی‌معنا می‌کرد.
 *
 * عدد و علامتش در هر سلول نوشته می‌شود، پس رنگ فقط تقویت‌کننده است و جدول
 * برای کسی که کوررنگی قرمز-سبز دارد هم خواناست.
 */
@Composable
fun FundReturnComparison(
    funds: List<FundRankingItem>,
    asOfJalaliDate: String,
    modifier: Modifier = Modifier
) {
    var sortPeriod by rememberSaveable { mutableStateOf(ReturnPeriod.YEARLY) }
    var sortByConsistency by rememberSaveable { mutableStateOf(false) }

    val withReturns = remember(funds) {
        funds.filter { it.returns?.hasAnyReturn == true }
    }

    val sorted = remember(withReturns, sortPeriod, sortByConsistency) {
        // صندوق بدون مقدار در ستون فعال، ته جدول می‌رود — نه بالای آن با صفرِ
        // ساختگی.
        withReturns.sortedWith(
            compareByDescending<FundRankingItem> { fund ->
                if (sortByConsistency) {
                    fund.consistency?.score ?: Double.NEGATIVE_INFINITY
                } else {
                    sortPeriod.valueOf(fund.returns) ?: Double.NEGATIVE_INFINITY
                }
            }.thenBy { it.symbol }
        )
    }

    var showAllRows by rememberSaveable { mutableStateOf(false) }

    val visible = remember(sorted, showAllRows) {
        if (showAllRows) sorted else sorted.take(DEFAULT_VISIBLE_ROWS)
    }

    // مقیاس هر ستون از روی همان صندوق‌هایی که **نمایش داده می‌شوند** حساب
    // می‌شود، نه کل فهرست: وقتی فقط ۱۵ ردیف دیده می‌شود، رنگ‌آمیزی باید همان
    // ۱۵ تا را از هم تفکیک کند، نه اینکه همه‌شان در سایه یک صندوقِ نادیدهٔ
    // پرت، کم‌رنگ شوند.
    val scales = remember(visible) {
        ReturnPeriod.entries.associateWith { period ->
            ReturnHeatmap.scaleOf(visible.map { period.valueOf(it.returns) })
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "مقایسه عملکرد صندوق‌ها",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            if (withReturns.isEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "برای صندوق‌های این دسته داده بازدهی دریافت نشد. " +
                            "«بررسی لحظه‌ای» را بزنید یا دسته دیگری را انتخاب کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildString {
                    if (visible.size < withReturns.size) {
                        append("${visible.size} از ${withReturns.size} صندوق")
                    } else {
                        append("${withReturns.size} صندوق")
                    }
                    if (asOfJalaliDate.isNotBlank()) append(" • داده تا $asOfJalaliDate")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "برای مرتب‌سازی، روی عنوان هر ستون بزنید.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            val scroll = rememberScrollState()
            Column(modifier = Modifier.horizontalScroll(scroll)) {
                // ---- سربرگ ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderCell(
                        text = "نماد",
                        width = SYMBOL_COLUMN_WIDTH,
                        isActive = false,
                        align = TextAlign.Start
                    )
                    ReturnPeriod.entries.forEach { period ->
                        HeaderCell(
                            text = period.persianLabel,
                            width = VALUE_COLUMN_WIDTH,
                            isActive = !sortByConsistency && period == sortPeriod,
                            onClick = {
                                sortPeriod = period
                                sortByConsistency = false
                            }
                        )
                    }
                    HeaderCell(
                        text = "تداوم بازدهی",
                        width = CONSISTENCY_COLUMN_WIDTH,
                        isActive = sortByConsistency,
                        onClick = { sortByConsistency = true }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ---- ردیف‌ها ----
                visible.forEach { fund ->
                    Row(
                        modifier = Modifier.padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(SYMBOL_COLUMN_WIDTH)
                                .padding(end = 4.dp)
                        ) {
                            Text(
                                text = fund.symbol,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }

                        ReturnPeriod.entries.forEach { period ->
                            ReturnCell(
                                value = period.valueOf(fund.returns),
                                scale = scales[period]
                            )
                        }

                        ConsistencyCell(score = fund.consistency?.displayScore)
                    }
                }
            }

            if (sorted.size > DEFAULT_VISIBLE_ROWS) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (showAllRows) {
                        "نمایش ${DEFAULT_VISIBLE_ROWS} صندوق برتر"
                    } else {
                        "نمایش هر ${sorted.size} صندوق"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { showAllRows = !showAllRows }
                        .padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "تداوم بازدهی: رتبه صندوق در بازه‌های سه، شش و دوازده ماهه " +
                        "نسبت به صندوق‌های هم‌دسته‌اش، میانگین‌گرفته و روی مقیاس ۰ تا ۱۰۰ " +
                        "نشانده شده. امتیاز بالاتر یعنی بازدهی پایدارتر در دوره‌های مختلف، " +
                        "نه لزوماً بازدهی بیشتر. این عدد مستقیم از FundBase خوانده می‌شود. " +
                        "«—» یعنی سابقه صندوق کمتر از ۳۰ روز بوده و امتیازی برایش حساب نشده.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    isActive: Boolean,
    align: TextAlign = TextAlign.Center,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .width(width)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        contentAlignment = if (align == TextAlign.Start) Alignment.CenterStart else Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp
                ),
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = align
            )
            if (isActive) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "مرتب‌شده بر اساس این ستون",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(10.dp)
                )
            }
        }
    }
}

/**
 * سلول امتیاز تداوم بازدهی.
 *
 * برخلاف بازدهی، این عدد قطب ندارد — صفر «بدترینِ دسته» است، نه «زیان». پس
 * مقیاسش تک‌رنگ است و نه سبز/قرمز؛ رنگ‌آمیزی دوسویه اینجا امتیاز پایین را
 * شبیه ضرر نشان می‌داد که ادعای دیگری است.
 */
@Composable
private fun ConsistencyCell(score: Int?) {
    val alpha = ReturnHeatmap.sequentialAlpha(score)
    val fill = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .width(CONSISTENCY_COLUMN_WIDTH)
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(fill.copy(alpha = alpha))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = score?.let { Formatters.toPersianDigits(it.toString()) } ?: "—",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (score != null) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp
            ),
            color = if (score == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1
        )
    }
}

/**
 * یک سلول جدول. سلول بدون داده «—» می‌گیرد و هیچ رنگی نمی‌گیرد، تا از سلولی
 * که واقعاً بازدهی صفر داشته قابل تفکیک باشد.
 */
@Composable
private fun ReturnCell(value: Double?, scale: Double?) {
    val intensity = ReturnHeatmap.intensity(value, scale)
    val alpha = ReturnHeatmap.alphaFor(intensity)
    val isPositive = ReturnHeatmap.isPositive(value)
    val fill = if (isPositive) SignalBuy else SignalSell

    Box(
        modifier = Modifier
            .width(VALUE_COLUMN_WIDTH)
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(fill.copy(alpha = alpha))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (value == null) {
                "—"
            } else {
                val sign = if (value >= 0) "+" else "−"
                "$sign${Formatters.formatPercent(kotlin.math.abs(value))}"
            },
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (value != null) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp
            ),
            color = if (value == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1
        )
    }
}
