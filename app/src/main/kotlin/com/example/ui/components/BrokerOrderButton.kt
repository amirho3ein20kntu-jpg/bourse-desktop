package com.example.ui.components

import com.example.platform.openUrl
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.broker.EasyTraderLink
import kotlin.math.roundToLong

/**
 * دکمه «سفارش در ایزی‌تریدر» کنار هر سیگنال.
 *
 * چه می‌کند:
 *  ۱. مبلغ سفارش را (بدون جداکننده، آماده چسباندن) در کلیپ‌بورد می‌گذارد
 *  ۲. فرم سفارش همان نماد را با سمت درست (خرید/فروش) باز می‌کند
 *
 * چون اپ ایزی‌تریدر یک Trusted Web Activity است، این آدرس داخل خود اپ
 * کارگزاری باز می‌شود، نه در مرورگر.
 *
 * **هیچ سفارشی ثبت نمی‌شود.** فرم باز می‌شود و قیمت، تعداد و تأیید نهایی
 * با کاربر است. مرحله آخر را خودکار نمی‌کنیم؛ با پول واقعی ارزشش را ندارد.
 *
 * اگر ISIN نماد هنوز ثبت نشده باشد، یک‌بار از کاربر خواسته می‌شود آدرس فرم
 * سفارش را بچسباند. برنامه این کد را از هیچ‌جا نمی‌گیرد و حدس هم نمی‌زند.
 */
@Composable
fun BrokerOrderButton(
    symbol: String,
    side: EasyTraderLink.Side,
    amountToman: Double,
    isin: String?,
    onSaveIsin: (pastedText: String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var askForIsin by remember { mutableStateOf(false) }

    // مبلغ بدون جداکننده و بدون اعشار: چیزی که مستقیم در ماشین‌حساب
    // کارگزاری چسبانده می‌شود. کاما و «تومان» آنجا پذیرفته نمی‌شود.
    val pasteableAmount = remember(amountToman) {
        kotlin.math.abs(amountToman).roundToLong().toString()
    }

    fun openOrderForm(code: String) {
        val url = EasyTraderLink.orderFormUrl(code, side) ?: return

        runCatching { clipboard.setText(AnnotatedString(pasteableAmount)) }

        openUrl(url)
    }

    Button(
        onClick = { if (isin.isNullOrBlank()) askForIsin = true else openOrderForm(isin) },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp,
            vertical = 4.dp
        ),
        modifier = modifier
    ) {
        Text(
            text = if (isin.isNullOrBlank()) "اتصال به ایزی‌تریدر" else "سفارش در ایزی‌تریدر",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        )
    }

    if (askForIsin) {
        IsinPromptDialog(
            symbol = symbol,
            onDismiss = { askForIsin = false },
            onConfirm = { pasted ->
                if (onSaveIsin(pasted)) {
                    askForIsin = false
                    EasyTraderLink.extractIsin(pasted)?.let { openOrderForm(it) }
                }
            }
        )
    }
}

/**
 * یک‌بار برای هر نماد: کاربر آدرس فرم سفارش را می‌چسباند تا ISIN استخراج شود.
 */
@Composable
private fun IsinPromptDialog(
    symbol: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pasted by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "اتصال «$symbol» به ایزی‌تریدر",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                Text(
                    text = "برای این نماد فقط یک‌بار لازم است:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "۱. در ایزی‌تریدر نماد «$symbol» را باز کنید و روی خرید بزنید\n" +
                            "۲. آدرس صفحه را کپی کنید\n" +
                            "۳. همین‌جا بچسبانید",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    placeholder = {
                        Text(
                            text = "https://m.easytrader.ir/order-form/…",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "این کد فقط روی گوشی خودتان ذخیره می‌شود و جایی فرستاده نمی‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pasted) },
                enabled = pasted.isNotBlank()
            ) {
                Text("ذخیره و باز کردن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("بعداً") }
        }
    )
}
