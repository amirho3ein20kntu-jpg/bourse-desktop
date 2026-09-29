package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SignalBuy
import com.example.ui.theme.SignalBuyBg
import com.example.ui.theme.SignalHold
import com.example.ui.theme.SignalHoldBg
import com.example.ui.theme.SignalSell
import com.example.ui.theme.SignalSellBg
import com.example.ui.util.Formatters

/**
 * EasyTrader-style badge for displaying percentage values.
 * Positive = Soft green background with bright green bold text.
 * Negative = Soft red background with bright red bold text.
 * Zero = Soft grey background with neutral slate text.
 */
@Composable
fun PercentBadge(
    percent: Double,
    modifier: Modifier = Modifier,
    includeSign: Boolean = true,
    fontSize: TextUnit = 11.5.sp
) {
    val isPositive = percent > 0.001
    val isNegative = percent < -0.001

    val bgColor = when {
        isPositive -> SignalBuyBg
        isNegative -> SignalSellBg
        else -> SignalHoldBg
    }

    val textColor = when {
        isPositive -> SignalBuy
        isNegative -> SignalSell
        else -> SignalHold
    }

    val sign = if (includeSign && isPositive) "+" else ""
    val text = "$sign${Formatters.formatPercent(percent)}"

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize
                ),
                color = textColor
            )
        }
    }
}

/**
 * EasyTrader-style value chip (e.g. for profit or change in Toman)
 */
@Composable
fun ValueChangeBadge(
    amountToman: Double,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.5.sp
) {
    val isPositive = amountToman > 0.0
    val isNegative = amountToman < 0.0

    val bgColor = when {
        isPositive -> SignalBuyBg
        isNegative -> SignalSellBg
        else -> SignalHoldBg
    }

    val textColor = when {
        isPositive -> SignalBuy
        isNegative -> SignalSell
        else -> SignalHold
    }

    val sign = if (isPositive) "+" else ""
    val text = "$sign${Formatters.formatTomanDirect(amountToman)}"

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize
                ),
                color = textColor
            )
        }
    }
}
