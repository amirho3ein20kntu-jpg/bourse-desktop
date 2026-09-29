package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PortfolioEntity
import com.example.domain.portfolio.PortfolioPnl
import com.example.domain.portfolio.SymbolWeight
import com.example.ui.theme.SignalBuy
import com.example.ui.theme.SignalSell
import com.example.ui.util.Formatters
import kotlin.math.abs

/**
 * EasyTrader-inspired portfolio holding card.
 *
 * RTL Layout:
 * - Top Right: Symbol Name (Bold) + small colored status bullet
 * - Bottom Right: Quantity (تعداد واحد)
 * - Top Left: Current Value (Bold, large)
 * - Bottom Left: Profit/Loss in Toman + Percentage Badge (both green or red based on sign)
 * - Last line (optional): وزن نماد از کل سبد، با نوار و نشانگر وزن هدف
 */
@Composable
fun PortfolioSymbolCard(
    item: PortfolioEntity,
    modifier: Modifier = Modifier,
    weight: SymbolWeight? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val categoryColor = getCategoryColor(item.assetCategory)

    // Calculate profit and loss
    // قاعده «مبنای خرید دارد یا نه» یکی است و در PortfolioPnl زندگی می‌کند، تا
    // جمع کارت‌های تک‌نماد همیشه با عدد بالای داشبورد بخواند.
    val hasPrincipal = PortfolioPnl.hasBasis(item)
    val principal = if (hasPrincipal) item.quantity.toDouble() * item.averagePrice else 0.0
    val profitRial = if (hasPrincipal) item.currentValue - principal else 0.0
    val profitPercent = if (hasPrincipal) (profitRial / principal) * 100.0 else 0.0
    val isPositive = profitRial >= 0.0
    val profitColor = if (isPositive) SignalBuy else SignalSell

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Right: Colored dot + Symbol Name + Category tag
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dot indicator
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(categoryColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.symbol,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (item.isHoldLocked) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "قفل نگه‌داری",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Subtle category badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(categoryColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.assetCategory.persianName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = categoryColor
                            )
                        )
                    }
                }

                // Left: Current Value (Bold, large)
                Text(
                    text = Formatters.formatToman(item.currentValue),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Right: Quantity (تعداد واحد)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (item.quantity > 0) {
                            "${Formatters.formatNumber(item.quantity)} واحد"
                        } else {
                            "ارزش روز"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.lastPrice > 0.0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "•  قیمت: ${Formatters.formatToman(item.lastPrice)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                // Left: Profit/Loss value + Percentage Badge (or edit/delete buttons if present)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (hasPrincipal) {
                        val sign = if (isPositive) "+" else "-"
                        Text(
                            text = "$sign${Formatters.formatToman(abs(profitRial))}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = profitColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        PercentBadge(
                            percent = profitPercent,
                            includeSign = true,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = Formatters.formatToman(item.currentValue),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (onEdit != null || onDelete != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        onEdit?.let {
                            IconButton(onClick = it, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "ویرایش",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        onDelete?.let {
                            IconButton(onClick = it, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (weight != null) {
                Spacer(modifier = Modifier.height(10.dp))
                PortfolioWeightRow(symbolWeight = weight, color = categoryColor)
            }
        }
    }
}

/**
 * «۲۳٫۴٪ از سبد» با یک نوار باریک؛ اگر موتور وزن هدف داده باشد، یک خط عمودی
 * روی نوار جای هدف را نشان می‌دهد تا کم‌وزن یا پُروزن بودن با یک نگاه دیده شود.
 */
@Composable
private fun PortfolioWeightRow(symbolWeight: SymbolWeight, color: Color) {
    val current = symbolWeight.currentPercent.coerceIn(0.0, 100.0)
    val target = symbolWeight.targetPercent?.coerceIn(0.0, 100.0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${Formatters.formatPercent(current)} از سبد",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            if (current > 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((current / 100.0).toFloat())
                        .fillMaxHeight()
                        .background(color)
                )
            }
            if (target != null && target > 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((target / 100.0).toFloat())
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.onSurface)
                    )
                }
            }
        }
        if (target != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "هدف ${Formatters.formatPercent(target)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
