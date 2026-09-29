package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FundRankingItem
import com.example.data.model.RebalanceAction
import com.example.data.model.SymbolRebalanceAlert
import com.example.ui.theme.SignalBuy
import com.example.ui.theme.SignalBuyBg
import com.example.ui.theme.SignalHold
import com.example.ui.theme.SignalHoldBg
import com.example.ui.theme.SignalSell
import com.example.ui.theme.SignalSellBg
import com.example.ui.util.Formatters

@Composable
fun SignalActionCard(
    alert: SymbolRebalanceAlert,
    modifier: Modifier = Modifier,
    /**
     * دکمه سفارش کارگزاری. اختیاری است تا این کارت بدون وابستگی به
     * ViewModel هم قابل استفاده و پیش‌نمایش بماند.
     */
    brokerAction: (@Composable () -> Unit)? = null,
    /**
     * روشن/خاموش کردن «قفل نگه‌داری». null یعنی کارت فقط نمایشی است
     * (پیش‌نمایش یا صفحه‌ای که اجازه ویرایش سبد ندارد).
     */
    onToggleLock: ((Boolean) -> Unit)? = null,
    /**
     * صندوقی از دیده‌بان با کمترین حباب در همین طبقه — فقط روی ردیف «پیشنهاد
     * ورود» نشان داده می‌شود. `null` یعنی داده کافی برای پیشنهاد نبود.
     */
    suggestedFund: FundRankingItem? = null
) {
    val actionColor = when (alert.action) {
        RebalanceAction.BUY -> SignalBuy
        RebalanceAction.SELL -> SignalSell
        RebalanceAction.HOLD -> SignalHold
    }

    val actionBg = when (alert.action) {
        RebalanceAction.BUY -> SignalBuyBg
        RebalanceAction.SELL -> SignalSellBg
        RebalanceAction.HOLD -> SignalHoldBg
    }

    val categoryColor = getCategoryColor(alert.category)

    // ردیف «پیشنهاد ورود»: طبقه‌ای که وزن هدف دارد ولی کاربر نمادی از آن ندارد
    val isSuggestion = alert.isSuggestedNewPosition
    val suggestionColor = MaterialTheme.colorScheme.primary

    val lockColor = MaterialTheme.colorScheme.tertiary

    val borderColor = when {
        isSuggestion -> suggestionColor.copy(alpha = 0.55f)
        alert.isHoldLocked -> lockColor.copy(alpha = 0.55f)
        alert.action != RebalanceAction.HOLD -> actionColor.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isSuggestion || alert.isHoldLocked ||
                    alert.action != RebalanceAction.HOLD
                ) 1.dp else 0.6.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Symbol Name, Category, Action Badge
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
                            .background(categoryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSuggestion) "؟" else alert.category.code,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = categoryColor
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (alert.isHoldLocked) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "قفل نگه‌داری",
                                    tint = lockColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = alert.symbol,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (isSuggestion) "نماد انتخاب نشده" else alert.category.persianName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSuggestion) suggestionColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSuggestion) suggestionColor.copy(alpha = 0.14f) else actionBg)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = when {
                            isSuggestion -> "پیشنهاد ورود"
                            alert.isHoldLocked && alert.suppressedSellAmount > 0.0 -> "نگهداری (قفل)"
                            else -> alert.action.persianLabel
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSuggestion) suggestionColor else actionColor,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Amount & Current vs Target Value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when {
                            isSuggestion -> "مبلغ لازم برای ورود به این طبقه"
                            alert.suppressedSellAmount > 0.0 -> "فروشی که قفل جلویش را گرفت"
                            alert.action == RebalanceAction.HOLD -> "وضعیت ریبلنس"
                            else -> "مبلغ پیشنهادی ${alert.action.persianLabel}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            alert.suppressedSellAmount > 0.0 ->
                                Formatters.formatToman(alert.suppressedSellAmount)
                            !isSuggestion && alert.action == RebalanceAction.HOLD -> "در محدوده تعادل"
                            else -> Formatters.formatToman(alert.actionAmount)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isSuggestion -> suggestionColor
                                alert.suppressedSellAmount > 0.0 -> lockColor
                                else -> actionColor
                            },
                            fontSize = 16.sp
                        )
                    )
                }

                if (alert.estimatedUnits > 0 && alert.suppressedSellAmount <= 0.0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "تعداد واحد تخمینی",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "~ ${Formatters.formatNumber(alert.estimatedUnits)} واحد",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Value details: Current Value -> Target Value with percent badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isSuggestion) {
                            "فعلی: در سبد شما نیست"
                        } else {
                            "فعلی: ${Formatters.formatCompactToman(alert.currentValue)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isSuggestion) {
                        Spacer(modifier = Modifier.width(4.dp))
                        PercentBadge(
                            percent = alert.currentWeightPercent,
                            includeSign = false,
                            fontSize = 10.sp
                        )
                    }
                }

                Text(
                    text = "وزن هدف: ${Formatters.formatPercent(alert.targetWeightPercent)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // یادداشت حداقل معامله / راهنمای انتخاب نماد
            if (alert.minOrderNotice != null) {
                // برای ردیف پیشنهادی این یک راهنماست، نه خطا
                val noticeColor = when {
                    isSuggestion -> suggestionColor
                    alert.isHoldLocked -> lockColor
                    else -> MaterialTheme.colorScheme.error
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = noticeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = alert.minOrderNotice.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = noticeColor
                    )
                }
            }

            // پیشنهاد صندوق ارزان‌تر (کمترین حباب) در همین طبقه — روش ۴ گزارش
            // بازتعادل: بین صندوق‌های هم‌طبقه، آن‌که حباب کمتری دارد ترجیح دارد.
            if (isSuggestion && suggestedFund != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = suggestionColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "پیشنهاد کم‌حباب‌ترین: ${suggestedFund.symbol} " +
                                "(حباب ${Formatters.formatPercent(suggestedFund.bubblePercent ?: 0.0)})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = suggestionColor
                    )
                }
            }

            // کلید قفل نگه‌داری. روی ردیف «پیشنهاد ورود» معنا ندارد چون هنوز
            // نمادی در سبد نیست که بشود قفلش کرد.
            if (onToggleLock != null && !isSuggestion) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onToggleLock(!alert.isHoldLocked) }
                        .background(
                            if (alert.isHoldLocked) {
                                lockColor.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (alert.isHoldLocked) {
                                Icons.Default.Lock
                            } else {
                                Icons.Default.LockOpen
                            },
                            contentDescription = null,
                            tint = if (alert.isHoldLocked) {
                                lockColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (alert.isHoldLocked) {
                                "قفل نگه‌داری روشن است"
                            } else {
                                "قفل نگه‌داری (این نماد را نفروش)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (alert.isHoldLocked) {
                                lockColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    // کلیک روی کل ردیف تغییرش می‌دهد؛ خودِ سوییچ فقط نمایش وضعیت است.
                    Switch(checked = alert.isHoldLocked, onCheckedChange = null)
                }
            }

            // فقط وقتی واقعاً سفارشی هست: ردیف «نگه‌دار» چیزی برای ثبت ندارد.
            if (brokerAction != null && alert.action != RebalanceAction.HOLD && !isSuggestion) {
                Spacer(modifier = Modifier.height(8.dp))
                brokerAction()
            }
        }
    }
}
