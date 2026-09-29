package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.alert.TradingSystem
import com.example.ui.theme.SignalBuy
import com.example.ui.theme.SignalBuyBg
import com.example.ui.theme.SignalSell
import com.example.ui.theme.SignalSellBg
import com.example.ui.util.Formatters

@Composable
fun RebalanceGauge(
    trIndex: Double,
    thresholdLimit: Double,
    isTriggered: Boolean,
    balancedVolumeRial: Double,
    totalBuyRial: Double,
    totalSellRial: Double,
    modifier: Modifier = Modifier,
    /** بخشی از Tr که به خاطر قفل نگه‌داری با معامله پایین نمی‌آید */
    lockBoundTrIndex: Double = 0.0
) {
    val statusColor = if (isTriggered) SignalSell else SignalBuy
    val statusBg = if (isTriggered) SignalSellBg else SignalBuyBg
    val statusBorder = if (isTriggered) SignalSell.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.8.dp, statusBorder, RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: TR index and status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(statusBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isTriggered) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isTriggered) "آلارم فعال ریبلنس پورتفوی" else "پورتفوی در وضعیت تعادل",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = if (isTriggered) "انحراف بالاتر از مرز $thresholdLimit٪ است" else "انحراف مجاز (زیر $thresholdLimit٪)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // TR Number Display with Badge
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "شاخص TR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    PercentBadge(
                        percent = trIndex,
                        includeSign = false,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar showing TR vs Threshold
            val progress = (trIndex / (thresholdLimit * 2.0)).coerceIn(0.0, 1.0).toFloat()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Frequency classification tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "آستانه تحریک: ${Formatters.formatPercent(thresholdLimit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = TradingSystem.forThreshold(thresholdLimit).label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (lockBoundTrIndex > 0.0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (lockBoundTrIndex >= thresholdLimit) {
                        "${Formatters.formatPercent(lockBoundTrIndex)} از این انحراف به خاطر قفل نگه‌داری است و فقط با واریز پول نقد جبران می‌شود"
                    } else {
                        "${Formatters.formatPercent(lockBoundTrIndex)} از این انحراف به خاطر قفل نگه‌داری است؛ پس از اجرای سیگنال‌ها زیر آستانه می‌ماند"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isTriggered && balancedVolumeRial > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "حجم اجرای متوازن: min(خرید، فروش)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Formatters.formatToman(balancedVolumeRial),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "مجموع آلارم خرید: ${Formatters.formatToman(totalBuyRial)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalBuy
                            )
                            Text(
                                text = "مجموع آلارم فروش: ${Formatters.formatToman(totalSellRial)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalSell
                            )
                        }
                    }
                }
            }
        }
    }
}
