package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PortfolioSnapshotEntity
import com.example.ui.theme.SignalBuy
import com.example.ui.theme.SignalSell
import com.example.ui.util.Formatters
import com.example.ui.util.JalaliDate

/**
 * نمودار رشد ارزش سبد در طول زمان.
 *
 * داده از عکس‌های روزانه می‌آید که با هر بارگذاری اکسل ثبت می‌شوند. تا وقتی
 * حداقل دو روز داده نباشد، نمودار رسم نمی‌شود و به‌جایش صریحاً گفته می‌شود
 * چرا — یک خط صاف از یک نقطه، «رشد صفر» را القا می‌کند که دروغ است.
 */
@Composable
fun PortfolioGrowthChart(
    snapshots: List<PortfolioSnapshotEntity>,
    modifier: Modifier = Modifier,
    /** اگر داده شود، دکمه «تاریخچه کامل» کنار عنوان ظاهر می‌شود. */
    onOpenHistory: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "روند ارزش سبد",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (onOpenHistory != null) {
                    TextButton(onClick = onOpenHistory) {
                        Text("تاریخچه کامل")
                    }
                }
            }

            if (snapshots.size < 2) {
                Text(
                    text = if (snapshots.isEmpty()) {
                        "هنوز داده‌ای ثبت نشده. هر بار که اکسل سبد را بارگذاری کنید یک نقطه اضافه می‌شود."
                    } else {
                        "فقط داده یک روز ثبت شده. برای رسم روند، حداقل دو روز لازم است."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                return@Column
            }

            val values = snapshots.map { it.totalValueRial }
            val first = values.first()
            val last = values.last()
            val changeRial = last - first
            val changePercent = if (first > 0.0) (changeRial / first) * 100.0 else 0.0
            val isUp = changeRial >= 0.0
            val lineColor = if (isUp) SignalBuy else SignalSell

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${Formatters.toPersianDigits(snapshots.size.toString())} روز ثبت‌شده • از ${JalaliDate.formatLong(snapshots.first().day)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${if (isUp) "+" else "−"}${Formatters.formatPercent(kotlin.math.abs(changePercent))}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = lineColor
                )
            }

            Text(
                text = "${if (isUp) "رشد" else "افت"} ${Formatters.formatCompactToman(kotlin.math.abs(changeRial))} " +
                        "نسبت به اولین روز",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val minValue = values.min()
            val maxValue = values.max()
            // وقتی همه مقادیر برابرند، دامنه صفر می‌شود و تقسیم بر صفر پیش می‌آید
            val span = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(top = 12.dp)
            ) {
                val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
                val points = values.mapIndexed { index, value ->
                    Offset(
                        x = index * stepX,
                        y = size.height - ((value - minValue) / span).toFloat() * size.height
                    )
                }

                // سایه زیر خط
                val fill = Path().apply {
                    moveTo(points.first().x, size.height)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, size.height)
                    close()
                }
                drawPath(
                    path = fill,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
                    )
                )

                val line = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path = line, color = lineColor, style = Stroke(width = 3f))

                // فقط نقطه آخر علامت می‌خورد تا نمودار شلوغ نشود
                drawCircle(color = lineColor, radius = 6f, center = points.last())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Formatters.formatCompactToman(minValue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Formatters.formatCompactToman(maxValue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
