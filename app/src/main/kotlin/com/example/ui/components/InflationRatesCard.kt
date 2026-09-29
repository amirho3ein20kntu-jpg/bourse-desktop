package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.InflationRateEntity
import com.example.ui.util.Formatters

private val PERSIAN_MONTH_NAMES = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)

/**
 * کارت نرخ تورم نقطه‌به‌نقطه — از بانک مرکزی (فقط با IP ایران) یا ورود دستی.
 *
 * هر ردیف را کاربر می‌تواند دستی هم اصلاح کند؛ ردیف دستی‌شده با دریافت
 * خودکار بعدی رونویسی نمی‌شود (`isManuallyEdited`).
 */
@Composable
fun InflationRatesCard(
    rates: List<InflationRateEntity>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSaveManual: (year: Int, month: Int, ratePercent: Double) -> Unit,
    onDelete: (year: Int, month: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var yearInput by remember { mutableStateOf("") }
    var monthIndex by remember { mutableStateOf(1) }
    var rateInput by remember { mutableStateOf("") }

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
                    text = "نرخ تورم (بانک مرکزی)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "به‌روزرسانی از cbi.ir")
                    }
                }
            }
            Text(
                text = "دریافت خودکار فقط با IP ایران کار می‌کند؛ در غیر این صورت دستی وارد کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (rates.isEmpty()) {
                Text(
                    text = "هنوز نرخی ثبت نشده.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                rates.take(6).forEach { rate ->
                    val monthName = PERSIAN_MONTH_NAMES.getOrNull(rate.jalaliMonth - 1) ?: "؟"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$monthName ${Formatters.toPersianDigits(rate.jalaliYear.toString())}" +
                                    if (rate.isManuallyEdited) " (دستی)" else "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${Formatters.formatDecimal(rate.ratePercent)}٪",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { onDelete(rate.jalaliYear, rate.jalaliMonth) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "حذف",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.width(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ورود دستی نرخ (مثلاً از همان جدول cbi.ir):",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = yearInput,
                    onValueChange = { yearInput = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("سال") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = rateInput,
                    onValueChange = { rateInput = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' } },
                    label = { Text("نرخ (٪)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            var monthMenuExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { monthMenuExpanded = true }) {
                    Text(PERSIAN_MONTH_NAMES[monthIndex - 1])
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = monthMenuExpanded,
                    onDismissRequest = { monthMenuExpanded = false }
                ) {
                    PERSIAN_MONTH_NAMES.forEachIndexed { index, name ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                monthIndex = index + 1
                                monthMenuExpanded = false
                            }
                        )
                    }
                }

                val year = yearInput.toIntOrNull()
                val rate = rateInput.toDoubleOrNull()
                Button(
                    onClick = {
                        if (year != null && rate != null) {
                            onSaveManual(year, monthIndex, rate)
                            yearInput = ""
                            rateInput = ""
                        }
                    },
                    enabled = year != null && rate != null
                ) {
                    Text("ذخیره")
                }
            }
        }
    }
}
