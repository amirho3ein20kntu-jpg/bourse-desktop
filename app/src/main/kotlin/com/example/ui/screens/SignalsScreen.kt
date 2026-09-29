package com.example.ui.screens

import com.example.platform.Uri
import com.example.platform.rememberLauncherForActivityResult
import com.example.platform.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AssetCategory
import com.example.data.model.RebalanceAction
import com.example.domain.calculator.PcmrEngine
import com.example.domain.portfolio.OrderExecution
import com.example.domain.ranking.FundSuggestion
import com.example.domain.broker.EasyTraderLink
import com.example.ui.components.BrokerOrderButton
import com.example.ui.components.SignalActionCard
import com.example.ui.components.getCategoryColor
import com.example.ui.theme.SignalBuy
import com.example.ui.theme.SignalSell
import com.example.ui.util.Formatters
import com.example.ui.viewmodel.PcmrUiState

enum class SignalFilter(val persianTitle: String) {
    ALL("همه نمادها"),
    ACTIVE_ONLY("فقط سیگنال‌های فعال"),
    BUY_ONLY("سیگنال‌های خرید"),
    SELL_ONLY("سیگنال‌های فروش")
}

@Composable
fun SignalsScreen(
    uiState: PcmrUiState,
    modifier: Modifier = Modifier,
    /** سفارش‌های قابل اعمال بر اساس سیگنال‌های فعلی */
    executableOrders: () -> List<OrderExecution.ExecutableOrder> = { emptyList() },
    onApplyExecutedOrders: (Set<Long>) -> Unit = {},
    onExportCsv: (Uri) -> Unit = {},
    onShareText: () -> Unit = {},
    exportFileName: () -> String = { "pcmr-signals.csv" },
    /** نگاشت نماد به ISIN برای لینک فرم سفارش کارگزاری. */
    symbolIsinMap: Map<String, String> = emptyMap(),
    /** ISIN را از متن چسبانده‌شده می‌خواند و ذخیره می‌کند؛ false یعنی نامعتبر بود. */
    onSaveIsin: (symbol: String, pastedText: String) -> Boolean = { _, _ -> false },
    /** روشن/خاموش کردن «قفل نگه‌داری» یک نماد سبد. */
    onToggleHoldLock: (itemId: Long, locked: Boolean) -> Unit = { _, _ -> }
) {
    val calc = uiState.calculationResult
    var activeFilter by rememberSaveable { mutableStateOf(SignalFilter.ALL) }
    var showApplyDialog by remember { mutableStateOf(false) }
    val applicableOrders = remember(calc.symbolAlerts, uiState.portfolioItems) { executableOrders() }
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> uri?.let { onExportCsv(it) } }

    val buyAlerts = remember(calc.symbolAlerts) { calc.symbolAlerts.filter { it.action == RebalanceAction.BUY } }
    val sellAlerts = remember(calc.symbolAlerts) { calc.symbolAlerts.filter { it.action == RebalanceAction.SELL } }

    val filteredAlerts = remember(calc.symbolAlerts, activeFilter) {
        when (activeFilter) {
            SignalFilter.ALL -> calc.symbolAlerts
            SignalFilter.ACTIVE_ONLY -> calc.symbolAlerts.filter { it.action != RebalanceAction.HOLD }
            SignalFilter.BUY_ONLY -> buyAlerts
            SignalFilter.SELL_ONLY -> sellAlerts
        }
    }

    // برنامه خرید با پول نقد، وقتی نمادی قفل نگه‌داری دارد و اضافه‌وزن است
    val lockedHold = calc.lockedHold
    val lockedCashPlan = remember(calc) { PcmrEngine.lockedHoldCashPlan(calc) }

    // طبقاتی که وزن هدف دارند ولی هیچ نمادی از آن‌ها در سبد نیست
    val suggestions = remember(calc.symbolAlerts) {
        calc.symbolAlerts.filter { it.isSuggestedNewPosition }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0-الف. هشدار وزن تخصیص‌نیافته.
        // موتور این عدد را حساب می‌کرد ولی هیچ‌جا نشان داده نمی‌شد. هر مقدار
        // غیرصفر یعنی جمع وزن‌های هدف ۱۰۰٪ نیست و در آن حالت نه شاخص Tr و نه
        // خروجی ماشین‌حساب واریز/برداشت قابل اتکا نیستند.
        if (kotlin.math.abs(calc.unallocatedWeightPercent) > 0.2) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (calc.unallocatedWeightPercent > 0) {
                                    "${Formatters.formatPercent(calc.unallocatedWeightPercent)} از وزن هدف به هیچ نمادی تخصیص نیافته"
                                } else {
                                    "${Formatters.formatPercent(-calc.unallocatedWeightPercent)} بیش از ۱۰۰٪ تخصیص داده شده"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "تا وقتی جمع وزن‌ها دقیقاً ۱۰۰٪ نشود، شاخص Tr و خروجی " +
                                        "ماشین‌حساب واریز/برداشت قابل اتکا نیستند. وزن‌های هدف را در " +
                                        "تنظیمات بررسی کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // 0-الف-۲. خروجی گرفتن از سفارش‌ها
        if (calc.symbolAlerts.any { it.action != RebalanceAction.HOLD }) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { csvLauncher.launch(exportFileName()) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("خروجی اکسل", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = onShareText,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اشتراک‌گذاری", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // 0-ب. اعمال سفارش‌های اجراشده
        if (applicableOrders.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "سفارش‌ها را در کارگزاری اجرا کردید؟",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "به‌جای وارد کردن دوباره اکسل کارگزاری، تعداد و ارزش نمادها " +
                                    "را همین‌جا به‌روز کنید. قبلش از صفحه بارگذاری یک پشتیبان بگیرید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showApplyDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اعمال ${applicableOrders.size} سفارش اجراشده")
                        }
                    }
                }
            }
        }

        // 0. هشدار طبقات خالی
        if (suggestions.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${suggestions.size} طبقه دارایی در سبد شما نماد ندارد",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = suggestions.joinToString("، ") { it.category.persianName },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "تا وقتی برای این طبقه‌ها صندوقی انتخاب نکنید، سبد به وزن هدف نمی‌رسد. " +
                                        "از صفحه دیده‌بان یک صندوق مناسب انتخاب و در بارگذاری اضافه کنید.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        // 1. Balanced Execution Rule Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "موتور معاملات متوازن PCMR",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "TR: ${Formatters.formatPercent(calc.trIndex)}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (calc.isRebalanceTriggered) SignalSell else SignalBuy
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("مجموع پیشنهادات خرید", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(Formatters.formatToman(calc.totalBuyAmount), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = SignalBuy))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("مجموع پیشنهادات فروش", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(Formatters.formatToman(calc.totalSellAmount), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = SignalSell))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "حجم متوازن قابل جابجایی: ${Formatters.formatToman(calc.balancedExecutionAmount)} (حداقلِ خرید و فروش)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 1-ب. قفل نگه‌داری: به‌جای فروش نماد قفل‌شده، چقدر نقد لازم است و با آن
        // چه بخرد. این کارت جواب مستقیم «طلا را نمی‌فروشم» است.
        if (lockedHold != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "قفل نگه‌داری: ${lockedHold.bindingSymbols.joinToString("، ")}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "به‌جای فروش ${Formatters.formatToman(lockedHold.suppressedSellAmount)} " +
                                "از این نمادها، سبد را با پول نقد بزرگ می‌کنیم تا وزنشان خودبه‌خود به هدف برسد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "پول نقد لازم",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = Formatters.formatToman(lockedHold.requiredCashRial),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "ارزش سبد پس از آن",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = Formatters.formatToman(lockedHold.targetPvRial),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        val cashBuys = lockedCashPlan?.orders
                            ?.filter { it.action == RebalanceAction.BUY }
                            .orEmpty()

                        if (cashBuys.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "این مبلغ را این‌طور خرج کن:",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            cashBuys.forEach { order ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (order.isSuggestedNewPosition) {
                                            "${order.symbol} (هنوز نمادی انتخاب نکرده‌ای)"
                                        } else {
                                            order.symbol
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (order.estimatedUnits > 0) {
                                            Formatters.formatToman(order.absoluteAmount) +
                                                "  (~ ${Formatters.formatNumber(order.estimatedUnits)} واحد)"
                                        } else {
                                            Formatters.formatToman(order.absoluteAmount)
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = SignalBuy
                                        )
                                    )
                                }
                            }
                        }

                        if (lockedHold.isUnreachableByCash) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "وزن هدف یکی از نمادهای قفل‌شده صفر است؛ آن یکی با " +
                                        "هیچ مقدار پول نقدی به تعادل نمی‌رسد و باید یا قفلش را " +
                                        "برداری یا چینش وزن‌ها را عوض کنی.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Cashflow Coverage Matrix (چگونه فروش‌ها خریدهای سبد را پوشش می‌دهند)
        if (buyAlerts.isNotEmpty() || sellAlerts.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تامین نقدینگی خریدها از محل فروش‌ها",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            val coveragePercent = if (calc.totalBuyAmount > 0) {
                                (calc.totalSellAmount / calc.totalBuyAmount * 100.0).coerceAtMost(100.0)
                            } else 100.0
                            Text(
                                text = "پوشش ${Formatters.formatPercent(coveragePercent)}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (coveragePercent >= 99.0) SignalBuy else MaterialTheme.colorScheme.secondary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress bar of coverage
                        val progressFloat = if (calc.totalBuyAmount > 0) {
                            (calc.totalSellAmount / calc.totalBuyAmount).coerceIn(0.0, 1.0).toFloat()
                        } else 1.0f

                        LinearProgressIndicator(
                            progress = { progressFloat },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (progressFloat >= 0.99f) SignalBuy else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Category Flow Breakdown
                        val catSells = sellAlerts.groupBy { it.category }
                            .mapValues { entry -> entry.value.sumOf { it.actionAmount } }
                        val catBuys = buyAlerts.groupBy { it.category }
                            .mapValues { entry -> entry.value.sumOf { it.actionAmount } }

                        Text(
                            text = "منبع نقدینگی (فروش‌های مازاد):",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SignalSell
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (catSells.isEmpty()) {
                            Text(
                                text = "هیچ مازادی برای فروش وجود ندارد (تامین نقدی از خارج سبد مورد نیاز است)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            catSells.forEach { (cat, amt) ->
                                val symbolsInCat = sellAlerts.filter { it.category == cat }.joinToString("، ") { it.symbol }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(getCategoryColor(cat))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${cat.persianName} ($symbolsInCat)",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Text(
                                        text = "-${Formatters.formatToman(amt)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = SignalSell
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "مقصد نقدینگی (خریدهای نیازمند شارژ):",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SignalBuy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (catBuys.isEmpty()) {
                            Text(
                                text = "هیچ کسری دارایی برای خرید وجود ندارد",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            catBuys.forEach { (cat, amt) ->
                                val symbolsInCat = buyAlerts.filter { it.category == cat }.joinToString("، ") { it.symbol }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(getCategoryColor(cat))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${cat.persianName} ($symbolsInCat)",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Text(
                                        text = "+${Formatters.formatToman(amt)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = SignalBuy
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val netBalance = calc.totalSellAmount - calc.totalBuyAmount
                        val balanceDescription = when {
                            netBalance > 10_000 -> "مازاد نقدینگی آزادشده پس از تسویه خریدها: ${Formatters.formatToman(netBalance)}"
                            netBalance < -10_000 -> "کسری نقدینگی کل (نیاز به واریز جدید یا بافر): ${Formatters.formatToman(kotlin.math.abs(netBalance))}"
                            else -> "تراز کامل: تمام مبالغ فروش دقیقا صرف خریدهای سبد می‌شود."
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = balanceDescription,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (netBalance >= 0) MaterialTheme.colorScheme.primary else SignalSell
                            )
                        }
                    }
                }
            }
        }

        // 2. Exchange Minimum Limits Notice
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "قوانین بورس: حداقل سفارش خرید ۱۰۰ هزار تومان و حداقل سفارش فروش ۵۰۰ هزار تومان است.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SignalFilter.entries, key = { it.name }) { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                        label = { Text(filter.persianTitle) }
                    )
                }
            }
        }

        // 4. Symbols List
        if (filteredAlerts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "هیچ نمادی با این فیلتر وجود ندارد",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredAlerts, key = { it.itemId }) { alert ->
                val suggestedFund = if (alert.isSuggestedNewPosition) {
                    remember(uiState.fundRankingList, alert.category) {
                        FundSuggestion.lowestBubbleFund(alert.category, uiState.fundRankingList)
                    }
                } else {
                    null
                }
                SignalActionCard(
                    alert = alert,
                    suggestedFund = suggestedFund,
                    onToggleLock = if (alert.isSuggestedNewPosition) {
                        null
                    } else {
                        { locked -> onToggleHoldLock(alert.itemId, locked) }
                    },
                    brokerAction = {
                        BrokerOrderButton(
                            symbol = alert.symbol,
                            side = if (alert.action == RebalanceAction.BUY) {
                                EasyTraderLink.Side.BUY
                            } else {
                                EasyTraderLink.Side.SELL
                            },
                            amountToman = alert.actionAmount / 10.0,
                            isin = symbolIsinMap[alert.symbol.trim()],
                            onSaveIsin = { pasted -> onSaveIsin(alert.symbol, pasted) }
                        )
                    }
                )
            }
        }
    }

    if (showApplyDialog) {
        ApplyOrdersDialog(
            orders = applicableOrders,
            onDismiss = { showApplyDialog = false },
            onConfirm = { selectedIds ->
                showApplyDialog = false
                onApplyExecutedOrders(selectedIds)
            }
        )
    }
}

/**
 * تأیید سفارش‌هایی که کاربر واقعاً اجرا کرده.
 *
 * همه از پیش تیک‌خورده‌اند چون حالت رایج «همه را زدم» است؛ ولی هر سفارشی که
 * اجرا نشده با یک تیک برداشته می‌شود. این کار داده واقعی سبد را تغییر می‌دهد،
 * پس نتیجه هر ردیف پیش از تأیید نشان داده می‌شود.
 */
@Composable
private fun ApplyOrdersDialog(
    orders: List<OrderExecution.ExecutableOrder>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit
) {
    var selected by remember(orders) {
        mutableStateOf(orders.map { it.itemId }.toSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کدام سفارش‌ها را اجرا کردید؟") },
        text = {
            Column {
                Text(
                    text = "تیک هر سفارشی که اجرا نکرده‌اید را بردارید. این تغییر روی سبد " +
                            "شما اعمال می‌شود و برگشت‌پذیر نیست.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(orders, key = { it.itemId }) { order ->
                        val isChecked = order.itemId in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (isChecked) {
                                        selected - order.itemId
                                    } else {
                                        selected + order.itemId
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isChecked, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "${order.action.persianLabel} ${order.symbol} — " +
                                            "${Formatters.formatNumber(order.units)} واحد",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (order.action == RebalanceAction.BUY) SignalBuy else SignalSell
                                )
                                Text(
                                    text = "تعداد پس از اجرا: " +
                                            "${Formatters.formatNumber(order.previousQuantity)} ← " +
                                            "${Formatters.formatNumber(order.newQuantity)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (order.isCappedBySholding) {
                                    Text(
                                        text = "به اندازه موجودی محدود شد",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected) },
                enabled = selected.isNotEmpty()
            ) {
                Text("اعمال ${selected.size} سفارش")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
