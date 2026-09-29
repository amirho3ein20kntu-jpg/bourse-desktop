package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InflationRateEntity
import com.example.data.model.SettingsEntity
import com.example.data.model.TimeHorizon
import com.example.data.parser.ExcelAndCsvParser
import com.example.domain.alert.TradingSystem
import com.example.domain.calculator.PcmrEngine
import com.example.ui.components.InflationRatesCard
import com.example.ui.theme.SignalBuy
import com.example.ui.theme.SignalSell
import com.example.ui.util.Formatters

@Composable
fun SettingsScreen(
    settings: SettingsEntity,
    onUpdateRt: (Double) -> Unit,
    onUpdateTimeHorizon: (TimeHorizon) -> Unit,
    onUpdateEml: (TimeHorizon, Double, Double) -> Unit,
    onUpdateThreshold: (Double) -> Unit,
    onSetManualWeights: (Boolean, Double, Double, Double, Double) -> Unit,
    onSetLivePriceSync: (Boolean) -> Unit,
    onRoundWeights: (Double) -> Unit = {},
    onSetRebalanceAlert: (Boolean) -> Unit = {},
    /** روی اندروید ۱۳ به بالا، بدون این مجوز هیچ نوتیفیکیشنی نمایش داده نمی‌شود. */
    hasNotificationPermission: Boolean = true,
    onOpenNotificationSettings: () -> Unit = {},
    inflationRates: List<InflationRateEntity> = emptyList(),
    isLoadingInflation: Boolean = false,
    onRefreshInflation: () -> Unit = {},
    onSaveManualInflation: (Int, Int, Double) -> Unit = { _, _, _ -> },
    onDeleteInflation: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedHorizon by remember(settings.timeHorizonMonths) {
        mutableStateOf(TimeHorizon.fromMonths(settings.timeHorizonMonths))
    }

    var rtText by remember(settings.riskTolerance) {
        mutableStateOf(settings.riskTolerance.toString())
    }

    // EML input fields for the currently selected horizon
    var emlStockText by remember(selectedHorizon, settings) {
        mutableStateOf(settings.currentEmlStock(selectedHorizon).toString())
    }
    var emlGoldText by remember(selectedHorizon, settings) {
        mutableStateOf(settings.currentEmlGold(selectedHorizon).toString())
    }

    // Threshold text
    var thresholdText by remember(settings.rebalanceThresholdPercent) {
        mutableStateOf(settings.rebalanceThresholdPercent.toString())
    }

    // Manual weights states
    var manualEnabled by remember(settings.isManualWeightsEnabled) {
        mutableStateOf(settings.isManualWeightsEnabled)
    }
    var manualGoldText by remember(settings.manualGoldWeight) { mutableStateOf(settings.manualGoldWeight.toString()) }
    var manualStockText by remember(settings.manualStockWeight) { mutableStateOf(settings.manualStockWeight.toString()) }
    var manualMixedText by remember(settings.manualMixedWeight) { mutableStateOf(settings.manualMixedWeight.toString()) }
    var manualFixedText by remember(settings.manualFixedWeight) { mutableStateOf(settings.manualFixedWeight.toString()) }

    // Track which field was last touched by user among manual weight inputs (default is fixed income being auto-balanced)
    var lastEditedField by rememberSaveable { mutableStateOf<String>("manual") }

    fun autoBalanceFourthField(currentField: String) {
        lastEditedField = currentField
        val gParsed = manualGoldText.trim().toDoubleOrNull()
        val sParsed = manualStockText.trim().toDoubleOrNull()
        val mParsed = manualMixedText.trim().toDoubleOrNull()
        val fParsed = manualFixedText.trim().toDoubleOrNull()

        // If user is editing one of gold, stock, mixed and fixed income is target:
        if (currentField != "fixed" && gParsed != null && sParsed != null && mParsed != null) {
            val rem = (100.0 - gParsed - sParsed - mParsed).coerceAtLeast(0.0)
            manualFixedText = if (rem % 1.0 == 0.0) rem.toInt().toString() else "%.1f".format(java.util.Locale.US, rem)
        } else if (currentField != "mixed" && gParsed != null && sParsed != null && fParsed != null) {
            val rem = (100.0 - gParsed - sParsed - fParsed).coerceAtLeast(0.0)
            manualMixedText = if (rem % 1.0 == 0.0) rem.toInt().toString() else "%.1f".format(java.util.Locale.US, rem)
        } else if (currentField != "stock" && gParsed != null && mParsed != null && fParsed != null) {
            val rem = (100.0 - gParsed - mParsed - fParsed).coerceAtLeast(0.0)
            manualStockText = if (rem % 1.0 == 0.0) rem.toInt().toString() else "%.1f".format(java.util.Locale.US, rem)
        } else if (currentField != "gold" && sParsed != null && mParsed != null && fParsed != null) {
            val rem = (100.0 - sParsed - mParsed - fParsed).coerceAtLeast(0.0)
            manualGoldText = if (rem % 1.0 == 0.0) rem.toInt().toString() else "%.1f".format(java.util.Locale.US, rem)
        }
    }

    val calculatedTarget = remember(settings, selectedHorizon) {
        PcmrEngine.calculateTargetWeights(settings)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Time Horizon Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "افق زمانی سرمایه‌گذاری (Time Horizon)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "پارامترهای حداکثر افت احتمالی (EML) متناسب با بازه زمانی انتخابی تنظیم می‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (horizon in TimeHorizon.entries) {
                            FilterChip(
                                selected = selectedHorizon == horizon,
                                onClick = {
                                    selectedHorizon = horizon
                                    onUpdateTimeHorizon(horizon)
                                },
                                label = { Text(horizon.persianTitle) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 2. Risk Tolerance (RT) Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "درصد تحمل ریسک سرمایه‌گذار (RT)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = Formatters.formatPercent(settings.riskTolerance),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "حداکثر درصد ضرری که در بدترین سناریو حاضر به تحمل آن از کل پورتفوی هستید (مثلاً ۱۰٪ یا ۱۵٪).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = settings.riskTolerance.toFloat(),
                        onValueChange = {
                            val rounded = kotlin.math.round(it.toDouble())
                            rtText = rounded.toInt().toString()
                            onUpdateRt(rounded)
                        },
                        valueRange = 5f..35f,
                        steps = 29
                    )

                    OutlinedTextField(
                        value = rtText,
                        onValueChange = {
                            rtText = it
                            val num = ExcelAndCsvParser.parseNumber(it)
                            if (num in 1.0..50.0) onUpdateRt(kotlin.math.round(num))
                        },
                        label = { Text("ورود عددی درصد ریسک‌پذیری (RT)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { Text("٪", modifier = Modifier.padding(end = 12.dp)) }
                    )
                }
            }
        }

        // 3. EML Parameters Table
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "پارامترهای EML در افق ${selectedHorizon.persianTitle}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "حداکثر افت تخمینی (Estimated Maximum Loss) با امکان آپدیت ماهانه.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = emlStockText,
                            onValueChange = { emlStockText = it },
                            label = { Text("EML_S (سهامی)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            trailingIcon = { Text("٪") }
                        )

                        OutlinedTextField(
                            value = emlGoldText,
                            onValueChange = { emlGoldText = it },
                            label = { Text("EML_G (طلا)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            trailingIcon = { Text("٪") }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // EML_M (Mixed) Read-only calculated field
                    val emlM = SettingsEntity.MIXED_TO_STOCK_EML_RATIO *
                            ExcelAndCsvParser.parseNumber(emlStockText)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "EML_M (مختلط = ۱/۳ سهامی):",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = Formatters.formatPercent(emlM),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val s = ExcelAndCsvParser.parseNumber(emlStockText)
                            val g = ExcelAndCsvParser.parseNumber(emlGoldText)
                            onUpdateEml(selectedHorizon, s, g)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ذخیره پارامترهای EML")
                    }
                }
            }
        }

        // 4. Optimal Target Percentages Display
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "درصدهای هدف بهینه استخراج‌شده از PCMR",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("صندوق‌های طلا (P_G)", style = MaterialTheme.typography.labelSmall)
                            Text(Formatters.formatPercent(calculatedTarget.goldWeight), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Column {
                            Text("سهامی و اهرمی (P_S)", style = MaterialTheme.typography.labelSmall)
                            Text(Formatters.formatPercent(calculatedTarget.stockWeight), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Column {
                            Text("مختلط (P_M)", style = MaterialTheme.typography.labelSmall)
                            Text(Formatters.formatPercent(calculatedTarget.mixedWeight), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Column {
                            Text("درآمد ثابت (P_F)", style = MaterialTheme.typography.labelSmall)
                            Text(Formatters.formatPercent(calculatedTarget.fixedIncomeWeight), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ریسک محاسبه‌شده پورتفوی: ${Formatters.formatPercent(calculatedTarget.calculatedRt)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 5. Manual Weights Override Mode
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // ── همگام‌سازی قیمت زنده ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "همگام‌سازی ارزش با قیمت زنده",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (settings.isLivePriceSyncEnabled) {
                                    "ارزش نمادهایی که تعداد واحدشان مشخص است، با قیمت روز بازمحاسبه می‌شود."
                                } else {
                                    "فقط قیمت روز به‌روز می‌شود؛ ارزش سبد همان چیزی می‌ماند که وارد کرده‌اید."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.isLivePriceSyncEnabled,
                            onCheckedChange = { onSetLivePriceSync(it) }
                        )
                    }

                    Text(
                        text = "ردیف‌هایی که ارزششان را دستی وارد کرده‌اید (بدون تعداد واحد) در هر دو حالت دست‌نخورده می‌مانند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // ── هشدار ریبلنس در پس‌زمینه ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "هشدار ریبلنس",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (settings.isRebalanceAlertEnabled) {
                                    "روزانه در پس‌زمینه بررسی می‌شود و فقط وقتی شاخص انحراف از آستانه رد شود خبر می‌دهد."
                                } else {
                                    "فقط وقتی خودتان اپ را باز کنید وضعیت را می‌بینید."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.isRebalanceAlertEnabled,
                            onCheckedChange = { onSetRebalanceAlert(it) }
                        )
                    }

                    if (settings.isRebalanceAlertEnabled && !hasNotificationPermission) {
                        Text(
                            text = "مجوز نمایش اعلان داده نشده — تا وقتی آن را ندهید هیچ هشداری نمایش داده نمی‌شود.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenNotificationSettings,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("باز کردن تنظیمات اعلان‌های این اپ")
                        }
                    }

                    if (settings.isRebalanceAlertEnabled) {
                        Text(
                            text = "توجه: در بعضی گوشی‌ها (شیائومی، سامسونگ، هواوی) مدیریت باتری کارهای پس‌زمینه را متوقف می‌کند. " +
                                    "اگر هشداری دریافت نکردید، این اپ را از بهینه‌سازی باتری معاف کنید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "چینش دستی درصدها",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "تغییر آزادانه درصد هر طبقه دارایی",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = manualEnabled,
                            onCheckedChange = {
                                manualEnabled = it
                                onSetManualWeights(
                                    it,
                                    ExcelAndCsvParser.parseNumber(manualGoldText),
                                    ExcelAndCsvParser.parseNumber(manualStockText),
                                    ExcelAndCsvParser.parseNumber(manualMixedText),
                                    ExcelAndCsvParser.parseNumber(manualFixedText)
                                )
                            }
                        )
                    }

                    if (manualEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualGoldText,
                                onValueChange = {
                                    manualGoldText = it
                                    autoBalanceFourthField("gold")
                                },
                                label = { Text("طلا ٪") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = manualStockText,
                                onValueChange = {
                                    manualStockText = it
                                    autoBalanceFourthField("stock")
                                },
                                label = { Text("سهامی ٪") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualMixedText,
                                onValueChange = {
                                    manualMixedText = it
                                    autoBalanceFourthField("mixed")
                                },
                                label = { Text("مختلط ٪") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = manualFixedText,
                                onValueChange = {
                                    manualFixedText = it
                                    autoBalanceFourthField("fixed")
                                },
                                label = { Text("درآمد ثابت ٪") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        val g = ExcelAndCsvParser.parseNumber(manualGoldText)
                        val s = ExcelAndCsvParser.parseNumber(manualStockText)
                        val m = ExcelAndCsvParser.parseNumber(manualMixedText)
                        val f = ExcelAndCsvParser.parseNumber(manualFixedText)
                        val totalManual = g + s + m + f

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مجموع درصدها: ${Formatters.formatPercent(totalManual)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (kotlin.math.abs(totalManual - 100.0) < 0.1) SignalBuy else MaterialTheme.colorScheme.error
                                )
                            )
                            Text(
                                text = "کادر چهارم برای رسیدن به ۱۰۰٪ خودکار تنظیم می‌شود",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val emlG = settings.currentEmlGold(selectedHorizon)
                        val emlS = settings.currentEmlStock(selectedHorizon)
                        val emlM = settings.currentEmlMixed(selectedHorizon)
                        val calcRisk = (g / 100.0) * emlG + (s / 100.0) * emlS + (m / 100.0) * emlM
                        val isOverRisk = calcRisk > settings.riskTolerance

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isOverRisk) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SignalSell.copy(alpha = 0.15f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = SignalSell, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "هشدار: ریسک محاسبه‌شده (${Formatters.formatPercent(calcRisk)}) بالاتر از ریسک‌پذیری اولیه (${Formatters.formatPercent(settings.riskTolerance)}) است!",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = SignalSell)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                onSetManualWeights(true, g, s, m, f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("اعمال چینش دستی")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "گرد کردن به مضارب، وزن‌ها را ساده‌تر و قابل‌اجراتر می‌کند و " +
                                    "تضمین می‌شود ریسک حاصل از ریسک‌پذیری شما بالاتر نرود.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onRoundWeights(5.0) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("گرد کردن به ۵٪")
                            }
                            OutlinedButton(
                                onClick = { onRoundWeights(10.0) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("گرد کردن به ۱۰٪")
                            }
                        }
                    }
                }
            }
        }

        // 6. Rebalancing Threshold (TR) Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "آستانه تحریک ریبلنس (Rebalance Threshold - TR)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "سیستم‌های معاملاتی:\n" + TradingSystem.entries.joinToString("\n") {
                            "• ${it.persianName} (${it.code}): ${it.rangeText}"
                        } + "\nسیستم فعلی شما: ${TradingSystem.forThreshold(settings.rebalanceThresholdPercent).persianName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val presets = listOf(3.0 to "۳٪", 5.0 to "۵٪", 10.0 to "۱۰٪", 20.0 to "۲۰٪")
                        for ((valTr, percentText) in presets) {
                            val title = "${TradingSystem.forThreshold(valTr).code} ($percentText)"
                            FilterChip(
                                selected = settings.rebalanceThresholdPercent == valTr,
                                onClick = {
                                    thresholdText = valTr.toString()
                                    onUpdateThreshold(valTr)
                                },
                                label = { Text(title) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = {
                            thresholdText = it
                            val num = ExcelAndCsvParser.parseNumber(it)
                            if (num in 1.0..50.0) onUpdateThreshold(num)
                        },
                        label = { Text("درصد آستانه دلخواه (TR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { Text("٪", modifier = Modifier.padding(end = 12.dp)) }
                    )
                }
            }
        }

        // 7. نرخ تورم بانک مرکزی
        item {
            InflationRatesCard(
                rates = inflationRates,
                isLoading = isLoadingInflation,
                onRefresh = onRefreshInflation,
                onSaveManual = onSaveManualInflation,
                onDelete = onDeleteInflation
            )
        }
    }
}
