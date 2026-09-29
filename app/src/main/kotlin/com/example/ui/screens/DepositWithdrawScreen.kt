package com.example.ui.screens

import com.example.platform.Uri
import com.example.platform.rememberLauncherForActivityResult
import com.example.platform.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DepositWithdrawalOrder
import com.example.data.model.RebalanceAction
import com.example.data.model.TransactionType
import com.example.data.parser.ExcelAndCsvParser
import com.example.domain.portfolio.PortfolioPnl
import com.example.ui.components.getCategoryColor
import com.example.ui.theme.SignalBuy
import com.example.ui.theme.SignalSell
import com.example.ui.util.Formatters
import com.example.ui.viewmodel.PcmrUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DepositWithdrawScreen(
    uiState: PcmrUiState,
    onTypeChange: (TransactionType) -> Unit,
    onAmountChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    onExportPlanCsv: (Uri) -> Unit = {},
    onSharePlan: () -> Unit = {},
    planFileName: () -> String = { "pcmr-withdrawal.csv" }
) {
    val type = uiState.calcTransactionType
    val amountToman = uiState.calcAmountToman
    val result = uiState.depositWithdrawResult
    val items = uiState.portfolioItems

    val pnl = remember(items) { PortfolioPnl.calculate(items) }
    val totalPrincipal: Double = pnl.principalRial
    val totalProfit: Double = pnl.profitRial
    val profitPercent: Double = pnl.profitPercent

    val planCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> uri?.let { onExportPlanCsv(it) } }

    var textInput by remember(amountToman) {
        mutableStateOf(if (amountToman > 0) Formatters.formatNumber(amountToman.toLong()) else "")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Transaction Type Toggle (Deposit vs Withdrawal)
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = type == TransactionType.DEPOSIT,
                    onClick = { onTypeChange(TransactionType.DEPOSIT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                ) {
                    Text("واریز سرمایه جدید (D)", fontWeight = FontWeight.Bold)
                }

                SegmentedButton(
                    selected = type == TransactionType.WITHDRAWAL,
                    onClick = { onTypeChange(TransactionType.WITHDRAWAL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                ) {
                    Text("برداشت وجه نقد (W)", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 1.5. Profit & Capital Status Card (نمایش مقدار سود قابل برداشت و اصل سرمایه)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (totalProfit > 0.0) SignalBuy.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (totalProfit > 0.0) SignalBuy.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
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
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (totalProfit > 0.0) SignalBuy.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (totalProfit > 0.0) Icons.Default.TrendingUp else Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = if (totalProfit > 0.0) SignalBuy else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (type == TransactionType.WITHDRAWAL) "حداکثر سود انباشته قابل برداشت" else "وضعیت سودآوری کل سبد",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (totalProfit > 0.0) "برداشت تا سقف این مبلغ به اصل سرمایه آسیبی نمی‌زند" else "هنوز سودی شناسایی نشده یا کل سبد روی اصل سرمایه است",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "کل سود کسب‌شده",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (totalProfit > 0.0) "+${Formatters.formatToman(totalProfit)} (${Formatters.formatPercent(profitPercent)})" else Formatters.formatToman(totalProfit),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (totalProfit > 0.0) SignalBuy else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "اصل سرمایه اولیه",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = Formatters.formatToman(totalPrincipal),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }

                    if (type == TransactionType.WITHDRAWAL && totalProfit > 0.0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        SuggestionChip(
                            onClick = {
                                textInput = Formatters.formatNumber(totalProfit.toLong())
                                onAmountChange(totalProfit)
                            },
                            label = {
                                Text(
                                    text = "برداشت ۱۰۰٪ سود (${Formatters.formatToman(totalProfit)})",
                                    fontWeight = FontWeight.Bold,
                                    color = SignalBuy
                                )
                            },
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }

        // 2. Amount Input & Quick Chips
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
                        text = if (type == TransactionType.DEPOSIT) "مبلغ واریزی جدید (تومان)" else "مبلغ برداشتی (تومان)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { input ->
                            textInput = input
                            val num = ExcelAndCsvParser.parseNumber(input)
                            onAmountChange(num)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        placeholder = { Text("مثلاً ۵۰,۰۰۰,۰۰۰ تومان") },
                        trailingIcon = {
                            Text(
                                text = "تومان",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickAmounts = listOf(10_000_000.0, 50_000_000.0, 100_000_000.0, 200_000_000.0)
                        for (amt in quickAmounts) {
                            SuggestionChip(
                                onClick = {
                                    textInput = Formatters.formatNumber(amt.toLong())
                                    onAmountChange(amt)
                                },
                                label = { Text("${Formatters.formatNumber((amt / 1_000_000).toLong())} میلیون تومان") }
                            )
                        }
                    }

                    // Dynamic Withdrawal Breakdown (آیا این برداشت از سود است یا اصل سرمایه؟)
                    if (type == TransactionType.WITHDRAWAL && amountToman > 0) {
                        Spacer(modifier = Modifier.height(14.dp))
                        val safeProfit = totalProfit.coerceAtLeast(0.0)
                        val withdrawnFromProfit = amountToman.coerceAtMost(safeProfit)
                        val withdrawnFromPrincipal = (amountToman - withdrawnFromProfit).coerceAtLeast(0.0)
                        val profitSharePercent = (withdrawnFromProfit / amountToman) * 100.0
                        val principalSharePercent = 100.0 - profitSharePercent

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "تحلیل منبع برداشت وجه",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = if (withdrawnFromPrincipal <= 0) "۱۰۰٪ از سود" else "${Formatters.formatPercent(profitSharePercent)} سود | ${Formatters.formatPercent(principalSharePercent)} اصل سرمایه",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (withdrawnFromPrincipal <= 0) SignalBuy else MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val progressFloat = (profitSharePercent / 100.0).toFloat().coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progressFloat },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = SignalBuy,
                                    trackColor = SignalSell.copy(alpha = 0.6f)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "برداشت از سود: ${Formatters.formatToman(withdrawnFromProfit)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SignalBuy
                                    )
                                    Text(
                                        text = "برداشت از اصل: ${Formatters.formatToman(withdrawnFromPrincipal)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (withdrawnFromPrincipal > 0) SignalSell else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                val statusNote = if (withdrawnFromPrincipal <= 0) {
                                    " خیالتان راحت! این برداشت تماماً از محل سود پورتفوی شماست و اصل سرمایه اولیه دست‌نخورده باقی می‌ماند."
                                } else {
                                    "توجه: مبلغ برداشت بیشتر از کل سود است. مبلغ ${Formatters.formatToman(withdrawnFromPrincipal)} از اصل سرمایه اولیه شما کسر خواهد شد."
                                }

                                Text(
                                    text = statusNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (withdrawnFromPrincipal <= 0) SignalBuy else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Golden Formula Concept Box
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Functions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (type == TransactionType.DEPOSIT) "فرمول طلایی واریز سرمایه" else "فرمول طلایی برداشت وجه",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // ΔV در موتور برابر (Vr − Va) است؛ علامت‌ها باید با همان قرارداد بخوانند.
                    val formulaText = if (type == TransactionType.DEPOSIT) {
                        "Order_i = (D × Weight_i) + ΔV_i\nسرمایه جدید به نسبت اوزان هدف تقسیم شده و انحراف فعلی سبد به صورت خودکار تراز می‌شود."
                    } else {
                        "Order_i = ΔV_i − (W × Weight_i)\nبرداشت وجه ابتدا از صندوق‌های با اضافه وزن کسر می‌شود تا ترکیب پورتفوی بدون اختلال حفظ گردد."
                    }

                    Text(
                        text = formulaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // ترشولد پس از تراکنش دیگر فرض نمی‌شود؛ از موتور خوانده می‌شود.
                    val postTr = result?.postTrIndex ?: 0.0
                    val isBalanced = postTr < 0.05

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isBalanced) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isBalanced) SignalBuy else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBalanced) {
                                "نتیجه محاسبات: انحراف پورتفوی پس از این تراکنش صفر (TR = ۰٪) خواهد شد."
                            } else {
                                "توجه: انحراف پس از تراکنش ${Formatters.formatPercent(postTr)} باقی می‌ماند."
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isBalanced) SignalBuy else MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }
        }

        // 4. Results Header & PV Delta
        if (result != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "دستورات خرید و فروش اختصاصی",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "ارزش جدید پورتفوی: ${Formatters.formatToman(result.newPv)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val trOk = result.postTrIndex < 0.05
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (trOk) SignalBuy.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "ترشولد بعد از معامله: ${Formatters.formatPercent(result.postTrIndex)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (trOk) SignalBuy else MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }

            // هشدار: مبلغ برداشت از دارایی بیشتر بوده و محدود شده است
            if (result.isAmountClamped) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (result.isLimitedByLockedPositions) {
                                        "مبلغ برداشت از بخش آزاد سبد بیشتر است"
                                    } else {
                                        "مبلغ برداشت از کل دارایی بیشتر است"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "درخواست: ${Formatters.formatToman(result.inputAmount)} — " +
                                            "قابل برداشت: ${Formatters.formatToman(result.effectiveAmount)}" +
                                            if (result.isLimitedByLockedPositions) {
                                                "  (نمادهای قفل‌شده فروخته نمی‌شوند)"
                                            } else {
                                                ""
                                            },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // هشدار: ردیف‌های قفل‌شده ثابت ماندند و سهمشان روی بقیه سرشکن شد
            if (result.frozenSymbols.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${result.frozenSymbols.joinToString("، ")} قفل نگه‌داری " +
                                    "دارند؛ ارزششان دست‌نخورده ماند و سهمشان روی بقیه نمادها سرشکن شد.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 4-ب. برنامه اجراشدنی برداشت
            val plan = uiState.withdrawalPlan
            if (plan != null && plan.legs.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "برنامه پیشنهادی برداشت",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "برای اینکه تعادل سبد کمترین به‌هم‌ریختگی را داشته باشد، " +
                                        "این مبالغ را از این صندوق‌ها بفروشید. هر سفارش به کف فروش " +
                                        "بورس رسیده و قابل ثبت است.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            plan.legs.forEach { leg ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = leg.symbol,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(
                                            text = "باقیمانده: ${Formatters.formatCompactToman(leg.valueAfterRial)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (leg.closesPosition) {
                                            Text(
                                                text = "این نماد کامل بسته می‌شود",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SignalSell
                                            )
                                        } else if (leg.leavesStub) {
                                            Text(
                                                text = "باقیمانده زیر کف اقتصادی ۱۰ میلیون تومان می‌ماند",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "فروش ${Formatters.formatToman(leg.amountRial)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = SignalSell
                                        )
                                        if (leg.estimatedUnits > 0L) {
                                            Text(
                                                text = "≈ ${Formatters.formatNumber(leg.estimatedUnits)} واحد",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "انحراف سبد پس از اجرا: ${Formatters.formatPercent(plan.postTrIndex)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            plan.shortfallNotice?.let { notice ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = notice,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { planCsvLauncher.launch(planFileName()) },
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
                                    onClick = onSharePlan,
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
                }
            } else if (plan != null && plan.shortfallNotice != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = plan.shortfallNotice!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // 5. Orders List
            items(result.orders, key = { it.itemId }) { order ->
                DepositWithdrawOrderItem(order = order)
            }
        }
    }
}

@Composable
fun DepositWithdrawOrderItem(order: DepositWithdrawalOrder) {
    val isBuy = order.action == RebalanceAction.BUY
    val isSuggestion = order.isSuggestedNewPosition
    val suggestionColor = MaterialTheme.colorScheme.primary
    val actionColor = when {
        isSuggestion -> suggestionColor
        isBuy -> SignalBuy
        order.action == RebalanceAction.SELL -> SignalSell
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val actionBg = actionColor.copy(alpha = 0.12f)
    val catColor = getCategoryColor(order.category)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            if (isSuggestion) 1.dp else 0.8.dp,
            if (isSuggestion) suggestionColor.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(catColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSuggestion) "؟" else order.category.code,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = catColor
                        )
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = order.symbol,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (isSuggestion) {
                            "نماد انتخاب نشده — وزن هدف: ${Formatters.formatPercent(order.targetWeight)}"
                        } else {
                            "وزن هدف: ${Formatters.formatPercent(order.targetWeight)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSuggestion) suggestionColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(actionBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isSuggestion) {
                            "ورود ${Formatters.formatToman(order.absoluteAmount)}"
                        } else {
                            "${order.action.persianLabel} ${Formatters.formatToman(order.absoluteAmount)}"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = actionColor
                        )
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                // سفارش در کارگزاری بر حسب تعداد واحد ثبت می‌شود، نه مبلغ.
                if (order.action != RebalanceAction.HOLD && order.estimatedUnits > 0L) {
                    Text(
                        text = "≈ ${Formatters.formatNumber(order.estimatedUnits)} واحد",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = actionColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = "ارزش نهایی: ${Formatters.formatCompactToman(order.postTransactionValue)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // هشدار حداقل سفارش بورس — همان قاعده‌ای که صفحه سیگنال‌ها استفاده می‌کند
        order.minOrderNotice?.let { notice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = notice,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
