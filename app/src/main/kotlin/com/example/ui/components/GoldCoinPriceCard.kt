package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AlanchandQuotes
import com.example.ui.util.Formatters

/**
 * کارت قیمت نقدی طلا، سکه و دلار آزاد — از آلان‌چند، نه از FundBase.
 *
 * این قیمت‌ها خودِ فلز/ارزند، نه NAV یا قیمت صندوق؛ فقط برای مرجع سریع کاربر
 * در دیده‌بان است و در هیچ محاسبه PCMR استفاده نمی‌شود.
 */
@Composable
fun GoldCoinPriceCard(
    quotes: AlanchandQuotes?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MonetizationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.width(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "طلا، سکه و دلار آزاد",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (quotes == null && isLoading) {
                Text(
                    text = "در حال دریافت از آلان‌چند...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Card
            }

            if (quotes == null || !quotes.hasAnyValue) {
                Text(
                    text = "دریافت نشد — اتصال اینترنت را بررسی کنید",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                return@Card
            }

            Spacer(modifier = Modifier.height(4.dp))

            GoldCoinPriceRow("دلار آزاد (فروش)", quotes.usdSellToman)
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            GoldCoinPriceRow("گرم طلای ۱۸ عیار", quotes.gram18Toman)
            GoldCoinPriceRow("سکه تمام (امامی)", quotes.fullCoinToman)
            GoldCoinPriceRow("نیم سکه", quotes.halfCoinToman)
            GoldCoinPriceRow("ربع سکه", quotes.quarterCoinToman)

            val updateText = quotes.goldUpdateText ?: quotes.currencyUpdateText
            if (updateText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "منبع: آلان‌چند — به‌روزرسانی سایت: $updateText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GoldCoinPriceRow(label: String, priceToman: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (priceToman != null) Formatters.formatTomanDirect(priceToman) else "—",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (priceToman != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
