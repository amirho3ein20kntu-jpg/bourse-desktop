package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.example.data.model.FundTechnicalInsight
import com.example.ui.viewmodel.PcmrViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AssetCategory
import com.example.data.model.FundRankingItem
import com.example.data.model.FundRiskStats
import com.example.domain.sync.MarketDataFreshness
import com.example.domain.analysis.Interpretation
import com.example.domain.analysis.RiskInterpreter
import com.example.domain.analysis.TechnicalInterpreter
import com.example.ui.components.ConsistencyBadge
import com.example.ui.components.FundReturnComparison
import com.example.ui.components.GoldCoinPriceCard
import com.example.ui.util.Formatters
import com.example.data.model.StatusLevel
import com.example.ui.viewmodel.PcmrUiState
import java.util.Locale

enum class FundFilterTab(val label: String) {
    ALL("همه صندوق‌ها"),
    LEVERAGED("سهامی اهرمی ⚡"),
    STOCK("سهامی کلاسیک"),
    GOLD("صندوق‌های طلا"),
    MIXED("صندوق‌های مختلط"),
    FIXED_INCOME("درآمد ثابت")
}

enum class ScreenerStrategy(val title: String, val subtitle: String) {
    BALANCED_PCMR("پیش‌فرض هلد داینامیک", "ترکیب متوازن حباب، نقدشوندگی، اندازه صندوق و روند"),
    LOW_BUBBLE("حداقل حباب قیمتی", "اولویت با خرید با تخفیف زیر NAV یا کمترین ریسک حباب"),
    STRONG_TREND("قوی‌ترین روند تکنیکال", "بر پایه روند ماهانه/هفتگی و فاصله از میانگین ۲۰۰ روزه"),
    HIGH_LIQUIDITY("نقدشوندگی و گردش بالا", "بیشترین ارزش معاملات خرد جهت تسهیل ورود و خروج"),
    LARGEST_AUM("بزرگ‌ترین دارایی تحت مدیریت", "صندوق‌هایی با بیشترین خالص ارزش دارایی")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FundRankingScreen(
    uiState: PcmrUiState,
    onRefreshAnalysis: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PcmrViewModel? = null
) {
    val technicalInsights by if (viewModel != null) {
        viewModel.technicalInsights.collectAsState()
    } else {
        remember { mutableStateOf<Map<String, FundTechnicalInsight>>(emptyMap()) }
    }

    val riskStats by if (viewModel != null) {
        viewModel.riskStats.collectAsState()
    } else {
        remember { mutableStateOf<Map<String, FundRiskStats>>(emptyMap()) }
    }

    val onRequestInsight: (FundRankingItem) -> Unit = { fund ->
        viewModel?.loadTechnicalInsight(fund)
        viewModel?.loadRiskStats(fund)
    }

    var selectedFilter by rememberSaveable { mutableStateOf(FundFilterTab.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Custom Screener State: Top 6 matched funds
    var showScreenerPanel by rememberSaveable { mutableStateOf(false) }
    var selectedStrategy by rememberSaveable { mutableStateOf(ScreenerStrategy.BALANCED_PCMR) }
    // فیلتر از ۱ میلیارد تومان شروع می‌شود: زیر آن، صندوق عملاً غیرقابل
    // ورود و خروج است و نشان دادنش فقط فهرست را شلوغ می‌کند.
    var minLiquidityBillionToman by rememberSaveable { mutableStateOf(1f) }
    var maxBubblePercent by rememberSaveable { mutableStateOf(2.5f) } // Max 2.5% bubble
    var requireStrongTrend by rememberSaveable { mutableStateOf(false) }
    var onlyUnderNav by rememberSaveable { mutableStateOf(false) }
    var selectedFundForModal by remember { mutableStateOf<FundRankingItem?>(null) }

    // AUM (دارایی تحت مدیریت) Floor and Ceiling Filter in Billion Tomans (میلیارد تومان)
    var minAumBillionInput by rememberSaveable { mutableStateOf("") }
    var maxAumBillionInput by rememberSaveable { mutableStateOf("") }

    val parsedMinAum = remember(minAumBillionInput) {
        minAumBillionInput.trim().replace("٫", ".").replace("،", "").toDoubleOrNull()
    }
    val parsedMaxAum = remember(maxAumBillionInput) {
        maxAumBillionInput.trim().replace("٫", ".").replace("،", "").toDoubleOrNull()
    }

    // Top 6 Funds dynamically computed matching user criteria
    val top6MatchedFunds = remember(
        uiState.fundRankingList,
        selectedFilter,
        selectedStrategy,
        minLiquidityBillionToman,
        maxBubblePercent,
        requireStrongTrend,
        onlyUnderNav,
        parsedMinAum,
        parsedMaxAum
    ) {
        // Step 1: Base list restricted by active tab category
        val baseList = when (selectedFilter) {
            FundFilterTab.ALL -> uiState.fundRankingList
            FundFilterTab.LEVERAGED -> uiState.fundRankingList.filter { it.category == AssetCategory.STOCK && it.isLeveraged }
            FundFilterTab.STOCK -> uiState.fundRankingList.filter { it.category == AssetCategory.STOCK && !it.isLeveraged }
            FundFilterTab.GOLD -> uiState.fundRankingList.filter { it.category == AssetCategory.GOLD }
            FundFilterTab.MIXED -> uiState.fundRankingList.filter { it.category == AssetCategory.MIXED }
            FundFilterTab.FIXED_INCOME -> uiState.fundRankingList.filter { it.category == AssetCategory.FIXED_INCOME }
        }

        // Step 2: Filter by hard constraints
        // نمادی که برای یک معیار داده ندارد، آن شرط را رد نمی‌کند (به‌جای اینکه صفر فرض شود).
        val filtered = baseList.filter { fund ->
            val liquidityTomanBillion = (fund.tradeValueToman ?: 0.0) / 1_000_000_000.0
            val meetsLiquidity = fund.tradeValueToman != null && liquidityTomanBillion >= minLiquidityBillionToman
            val bubble = fund.bubblePercent
            val meetsBubble = bubble != null &&
                    (if (onlyUnderNav) bubble <= 0.0 else bubble <= maxBubblePercent)
            val meetsTrend = !requireStrongTrend || ((fund.momentumScore ?: 0) >= 60)
            val aumBillion = (fund.netAssetToman ?: 0.0) / 1_000_000_000.0
            val meetsMinAum = parsedMinAum == null || (fund.netAssetToman != null && aumBillion >= parsedMinAum)
            val meetsMaxAum = parsedMaxAum == null || (fund.netAssetToman != null && aumBillion <= parsedMaxAum)
            meetsLiquidity && meetsBubble && meetsTrend && meetsMinAum && meetsMaxAum
        }

        // Step 3: Sort by user selected strategy
        val sorted = when (selectedStrategy) {
            ScreenerStrategy.BALANCED_PCMR -> {
                filtered.sortedByDescending { it.compositeScore }
            }
            ScreenerStrategy.LOW_BUBBLE -> {
                filtered.sortedWith(
                    compareBy<FundRankingItem> { it.bubblePercent ?: Double.MAX_VALUE }
                        .thenByDescending { it.tradeValueToman ?: 0.0 }
                )
            }
            ScreenerStrategy.STRONG_TREND -> {
                filtered.sortedWith(
                    compareByDescending<FundRankingItem> { it.momentumScore ?: -1 }
                        .thenByDescending { it.technical.priceVsSma200Pct ?: Double.NEGATIVE_INFINITY }
                )
            }
            ScreenerStrategy.HIGH_LIQUIDITY -> {
                filtered.sortedByDescending { it.tradeValueToman ?: 0.0 }
            }
            ScreenerStrategy.LARGEST_AUM -> {
                filtered.sortedByDescending { it.netAssetToman ?: 0.0 }
            }
        }

        // If hard constraints were too strict and returned fewer than 6, fallback gracefully
        if (sorted.size >= 6) {
            sorted.take(6)
        } else {
            val fallbackSet = sorted.map { it.symbol }.toSet()
            val filler = baseList
                .filter { !fallbackSet.contains(it.symbol) }
                .sortedByDescending { it.compositeScore }
                .take(6 - sorted.size)
            sorted + filler
        }
    }

    val filteredList = remember(
        uiState.fundRankingList,
        selectedFilter,
        searchQuery,
        parsedMinAum,
        parsedMaxAum
    ) {
        val list = when (selectedFilter) {
            FundFilterTab.ALL -> uiState.fundRankingList
            FundFilterTab.LEVERAGED -> uiState.fundRankingList.filter { it.category == AssetCategory.STOCK && it.isLeveraged }
            FundFilterTab.STOCK -> uiState.fundRankingList.filter { it.category == AssetCategory.STOCK && !it.isLeveraged }
            FundFilterTab.GOLD -> uiState.fundRankingList.filter { it.category == AssetCategory.GOLD }
            FundFilterTab.MIXED -> uiState.fundRankingList.filter { it.category == AssetCategory.MIXED }
            FundFilterTab.FIXED_INCOME -> uiState.fundRankingList.filter { it.category == AssetCategory.FIXED_INCOME }
        }

        val afterSearch = if (searchQuery.isBlank()) {
            list
        } else {
            val q = searchQuery.trim()
            list.filter { it.symbol.contains(q, ignoreCase = true) || it.fundName.contains(q, ignoreCase = true) }
        }

        // Filter by AUM (دارایی تحت مدیریت) floor & ceiling
        afterSearch.filter { fund ->
            val aum = fund.netAssetToman
            val aumBillion = (aum ?: 0.0) / 1_000_000_000.0
            val meetsMin = parsedMinAum == null || (aum != null && aumBillion >= parsedMinAum)
            val meetsMax = parsedMaxAum == null || (aum != null && aumBillion <= parsedMaxAum)
            meetsMin && meetsMax
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            GoldCoinPriceCard(
                quotes = uiState.alanchandQuotes,
                isLoading = uiState.isLoadingAlanchandQuotes
            )
        }

        item {
            // Header Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().testTag("ranking_header_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "دیده‌بان و رتبه‌بندی صندوق‌ها",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = onRefreshAnalysis,
                            enabled = !uiState.isAnalyzingFunds,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("refresh_analysis_button")
                        ) {
                            if (uiState.isAnalyzingFunds) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("در حال آنالیز...", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("بررسی لحظه‌ای", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    // وضعیت واقعی داده، نه یک ادعای ثابتِ «زنده». اگر شبکه در دسترس
                    // نبوده یا درخواست شکست خورده، آنچه نمایش داده می‌شود از کش است
                    // و کاربر باید قبل از تصمیم معاملاتی این را بداند.
                    val marketAgeMs = uiState.marketDataAgeMs
                    val isMarketFresh = MarketDataFreshness.isFresh(marketAgeMs)
                    Text(
                        text = when {
                            marketAgeMs == null ->
                                "داده‌ای از FundBase دریافت نشده — اتصال اینترنت را بررسی کنید"
                            isMarketFresh ->
                                "مرجع اصلی قیمت‌ها، NAV و حباب صندوق‌ها: تابلوی زنده FundBase (Supabase API)"
                            else ->
                                "داده‌ها از کش است (آخرین دریافت موفق: ${MarketDataFreshness.describeAge(marketAgeMs)}) — «بررسی لحظه‌ای» را بزنید"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isMarketFresh) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ارزیابی توصیفی و رتبه‌بندی صندوق‌ها بر اساس حباب قیمتی (P/NAV)، جریان پول و نقدشوندگی بازار از منابع زنده جهت تخصیص بهینه در استراتژی هلد داینامیک PCMR.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Category Filter Chips
        item {
            Column {
                Text(
                    text = "فیلتر بر اساس طبقه دارایی:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FundFilterTab.values().forEach { tab ->
                        FilterChip(
                            selected = selectedFilter == tab,
                            onClick = { selectedFilter = tab },
                            label = { Text(tab.label) },
                            leadingIcon = if (selectedFilter == tab) {
                                { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }
        }

        // جدول مقایسه عملکرد — همان دسته‌ای که بالا انتخاب شده
        item {
            val comparisonFunds = remember(uiState.fundRankingList, selectedFilter) {
                when (selectedFilter) {
                    FundFilterTab.ALL -> uiState.fundRankingList
                    FundFilterTab.LEVERAGED -> uiState.fundRankingList.filter {
                        it.category == AssetCategory.STOCK && it.isLeveraged
                    }
                    FundFilterTab.STOCK -> uiState.fundRankingList.filter {
                        it.category == AssetCategory.STOCK && !it.isLeveraged
                    }
                    FundFilterTab.GOLD -> uiState.fundRankingList.filter { it.category == AssetCategory.GOLD }
                    FundFilterTab.MIXED -> uiState.fundRankingList.filter { it.category == AssetCategory.MIXED }
                    FundFilterTab.FIXED_INCOME -> uiState.fundRankingList.filter {
                        it.category == AssetCategory.FIXED_INCOME
                    }
                }
            }

            FundReturnComparison(
                funds = comparisonFunds,
                asOfJalaliDate = uiState.returnsAsOfJalaliDate
            )
        }

        // Search Bar & Filter Toggle
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("جستجوی نماد یا نام صندوق (مثلاً: اهرم، عیار، افران)...", style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "پاک کردن", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val isAumFilterActive = minAumBillionInput.isNotEmpty() || maxAumBillionInput.isNotEmpty()
                        Button(
                            onClick = { showScreenerPanel = !showScreenerPanel },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAumFilterActive || showScreenerPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isAumFilterActive || showScreenerPanel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAumFilterActive) "فیلتر (فعال)" else "فیلتر اختصاصی",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // Active AUM Filter summary chip when active
                    if (minAumBillionInput.isNotEmpty() || maxAumBillionInput.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val minTxt = if (parsedMinAum != null) "کف: ${String.format(Locale.US, "%,.0f", parsedMinAum)} م.ت" else "کف: نامحدود"
                                    val maxTxt = if (parsedMaxAum != null) "سقف: ${String.format(Locale.US, "%,.0f", parsedMaxAum)} م.ت" else "سقف: نامحدود"
                                    Text(
                                        text = "فیلتر دارایی (AUM): $minTxt | $maxTxt (${filteredList.size} نماد)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        minAumBillionInput = ""
                                        maxAumBillionInput = ""
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "حذف فیلتر",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Interactive Screener Panel for 6 Matched Funds
                    AnimatedVisibility(visible = showScreenerPanel) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "🎯 استراتژی فیلتر هوشمند ۶ صندوق برتر:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ScreenerStrategy.values().forEach { strategy ->
                                    FilterChip(
                                        selected = selectedStrategy == strategy,
                                        onClick = { selectedStrategy = strategy },
                                        label = { Text(strategy.title, style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = if (selectedStrategy == strategy) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        } else null
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Sliders and criteria
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "حداقل گردش معاملات: ${String.format(Locale.US, "%.0f", minLiquidityBillionToman)} میلیارد تومان",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = when {
                                        minLiquidityBillionToman <= 1f -> "حداقل قابل معامله"
                                        minLiquidityBillionToman < 15f -> "نقدشوندگی متوسط"
                                        else -> "نقدشوندگی بالا"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = minLiquidityBillionToman,
                                onValueChange = { minLiquidityBillionToman = it },
                                // ۱ تا ۵۰ میلیارد تومان، پله‌های ۱ میلیاردی
                                valueRange = 1f..50f,
                                steps = 48
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "سقف مجاز حباب قیمتی: ${String.format(Locale.US, "%.1f", maxBubblePercent)}%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = if (maxBubblePercent <= 1.0f) "بسیار کم‌ریسک" else "متعادل",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = maxBubblePercent,
                                onValueChange = { maxBubblePercent = it },
                                valueRange = -1f..8f,
                                steps = 17
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = requireStrongTrend,
                                    onClick = { requireStrongTrend = !requireStrongTrend },
                                    label = { Text("فقط با روند تکنیکال قوی", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = onlyUnderNav,
                                    onClick = { onlyUnderNav = !onlyUnderNav },
                                    label = { Text("فقط با تخفیف زیر NAV", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(10.dp))

                            // 💰 دارایی تحت مدیریت (AUM) کف و سقف به میلیارد تومان
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "فیلتر دارایی تحت مدیریت (AUM):",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (minAumBillionInput.isNotEmpty() || maxAumBillionInput.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            minAumBillionInput = ""
                                            maxAumBillionInput = ""
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "حذف فیلتر دارایی",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "تعیین کف و سقف ارزش کل دارایی صندوق به میلیارد تومان جهت فیلتر لیست دیده‌بان:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Floor / Min AUM (کف دارایی)
                                OutlinedTextField(
                                    value = minAumBillionInput,
                                    onValueChange = { minAumBillionInput = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '٫' } },
                                    label = { Text("کف دارایی (میلیارد تومان)", style = MaterialTheme.typography.labelSmall) },
                                    placeholder = { Text("مثلاً ۱۰۰") },
                                    trailingIcon = {
                                        if (minAumBillionInput.isNotEmpty()) {
                                            IconButton(onClick = { minAumBillionInput = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )

                                // Ceiling / Max AUM (سقف دارایی)
                                OutlinedTextField(
                                    value = maxAumBillionInput,
                                    onValueChange = { maxAumBillionInput = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '٫' } },
                                    label = { Text("سقف دارایی (میلیارد تومان)", style = MaterialTheme.typography.labelSmall) },
                                    placeholder = { Text("مثلاً ۵۰۰۰") },
                                    trailingIcon = {
                                        if (maxAumBillionInput.isNotEmpty()) {
                                            IconButton(onClick = { maxAumBillionInput = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Fast preset chips for AUM
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                SuggestionChip(
                                    onClick = { minAumBillionInput = "100"; maxAumBillionInput = "" },
                                    label = { Text("بیش از ۱۰۰ م.ت", style = MaterialTheme.typography.labelSmall) }
                                )
                                SuggestionChip(
                                    onClick = { minAumBillionInput = "500"; maxAumBillionInput = "" },
                                    label = { Text("بیش از ۵۰۰ م.ت", style = MaterialTheme.typography.labelSmall) }
                                )
                                SuggestionChip(
                                    onClick = { minAumBillionInput = "1000"; maxAumBillionInput = "" },
                                    label = { Text("بیش از ۱ همت", style = MaterialTheme.typography.labelSmall) }
                                )
                                SuggestionChip(
                                    onClick = { minAumBillionInput = "5000"; maxAumBillionInput = "" },
                                    label = { Text("بیش از ۵ همت", style = MaterialTheme.typography.labelSmall) }
                                )
                                SuggestionChip(
                                    onClick = { minAumBillionInput = ""; maxAumBillionInput = "1000" },
                                    label = { Text("زیر ۱ همت", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Custom Screener: 6 Best Matched Funds Banner
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().testTag("top6_matched_screener_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "۶ صندوق با بیشترین تطابق فیلتر:",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = selectedStrategy.title,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "بر اساس مرجع وب‌سرویس زنده FundBase، این ۶ صندوق بالاترین تطابق را با معیارهای تنظیمی شما دارند:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 6 Cards Grid (2 rows of 3)
                    val chunked = top6MatchedFunds.chunked(3)
                    chunked.forEachIndexed { rowIdx, rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEachIndexed { colIdx, fund ->
                                val rankNum = (rowIdx * 3) + colIdx + 1
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 3.dp)
                                        .clickable { selectedFundForModal = fund }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (rankNum <= 3) Color(0xFFE5A93C) else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "$rankNum",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = fund.symbol,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        val categoryDisplay = when {
                                            fund.category == AssetCategory.STOCK && fund.isLeveraged -> "اهرمی ⚡"
                                            fund.category == AssetCategory.STOCK -> "سهامی"
                                            fund.category == AssetCategory.GOLD -> "طلا"
                                            fund.category == AssetCategory.MIXED -> "مختلط"
                                            else -> "درآمدثابت"
                                        }
                                        Text(
                                            text = categoryDisplay,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "امتیاز: ${fund.compositeScore}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        val fundBubble = fund.bubblePercent
                                        Text(
                                            text = if (fundBubble == null) "حباب: —"
                                            else "حباب: ${if (fundBubble > 0) "+" else ""}${String.format(Locale.US, "%.1f", fundBubble)}%",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                color = when {
                                                    fundBubble == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    fundBubble <= 0 -> Color(0xFF2E7D32)
                                                    fundBubble > 3.0 -> Color(0xFFC62828)
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        )
                                    }
                                }
                            }
                            // Fill empty spaces if last row has less than 3
                            if (rowItems.size < 3) {
                                for (i in 0 until (3 - rowItems.size)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section Title: Fund Cards
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "رتبه‌بندی کیفی و ارزندگی (${filteredList.size} صندوق):",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "به ترتیب امتیاز مولفه‌های مالی",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Fund Item Cards
        itemsIndexed(filteredList, key = { _, fund -> fund.symbol }) { index, fund ->
            FundRankingCard(
                rankIndex = index + 1,
                item = fund,
                technicalInsight = technicalInsights[fund.symbol],
                riskStats = riskStats[fund.symbol.trim()],
                onRequestInsight = { onRequestInsight(fund) },
                onClickDetail = {
                    onRequestInsight(fund)
                    selectedFundForModal = fund
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (selectedFundForModal != null) {
        val selectedFund = selectedFundForModal!!
        LaunchedEffect(selectedFund.symbol) {
            onRequestInsight(selectedFund)
        }
        FundBaseRankingDialog(
            fund = selectedFund,
            insight = technicalInsights[selectedFund.symbol],
            onDismiss = { selectedFundForModal = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FundRankingCard(
    rankIndex: Int,
    item: FundRankingItem,
    modifier: Modifier = Modifier,
    technicalInsight: FundTechnicalInsight? = null,
    riskStats: FundRiskStats? = null,
    onRequestInsight: (() -> Unit)? = null,
    onClickDetail: (() -> Unit)? = null
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            onRequestInsight?.invoke()
        }
    }

    val tierColor = when (item.gradeTier) {
        "طلایی (A+)" -> Color(0xFFE5A93C)
        "نقره‌ای (A)" -> Color(0xFF6E8CA0)
        "برنزی (B)" -> Color(0xFFB87333)
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp),
        onClick = { isExpanded = !isExpanded },
        modifier = modifier
            .fillMaxWidth()
            .testTag("fund_card_${item.symbol}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Rank, Symbol, Badges, Grade
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Rank Badge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(tierColor.copy(alpha = 0.2f))
                            .border(1.5.dp, tierColor, CircleShape)
                    ) {
                        Text(
                            text = "#$rankIndex",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = tierColor
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.symbol,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Category Badge (Distinguish Leveraged vs Classic Stock)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when {
                                    item.category == AssetCategory.STOCK && item.isLeveraged -> Color(0xFFFFCC80) // Orange/Amber for Leveraged
                                    item.category == AssetCategory.STOCK -> Color(0xFFBBDEFB) // Light Blue for Classic Stock
                                    item.category == AssetCategory.GOLD -> Color(0xFFFFE082)
                                    item.category == AssetCategory.MIXED -> Color(0xFFE1BEE7)
                                    item.category == AssetCategory.FIXED_INCOME -> Color(0xFFC8E6C9)
                                    else -> Color(0xFFE0E0E0)
                                }
                            ) {
                                val categoryDisplay = when {
                                    item.category == AssetCategory.STOCK && item.isLeveraged -> "سهامی اهرمی ⚡"
                                    item.category == AssetCategory.STOCK -> "سهامی کلاسیک"
                                    else -> item.category.persianName
                                }
                                Text(
                                    text = categoryDisplay,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (item.inUserPortfolio) {
                            Text(
                                text = "✓ موجود در سبد شما (${formatToman(item.userPortfolioValueRial / 10.0)} تومان)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32)
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        ConsistencyBadge(consistency = item.consistency)
                    }
                }

                // Grade and Score Badge + Expand Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = tierColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = item.gradeTier,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = tierColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "امتیاز: ${item.compositeScore} / ۱۰۰",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "بستن جزئیات" else "مشاهده بازدهی و رنک",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Live Price & NAV Transparency Box (For Manual Verification with TSETMC)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (item.isMarketDataFresh) Color(0xFF2E7D32) else Color(0xFFF57F17))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "منبع: ${item.dataSourceName}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isMarketDataFresh) Color(0xFF2E7D32) else Color(0xFFE65100)
                                )
                            )
                            val change = item.priceChangePercent
                            if (change != null && change != 0.0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                val isPositive = change > 0
                                Text(
                                    text = "(${if (isPositive) "+" else ""}${String.format(Locale.US, "%.1f", change)}%)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                )
                            }
                        }
                        if (item.lastUpdatedTime.isNotEmpty()) {
                            Text(
                                text = "زمان: ${item.lastUpdatedTime}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "آخرین معامله:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item.marketPriceRial?.let { "${formatPrice(it / 10.0)} تومان" } ?: "—",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Bubble status tag
                        val bubble = item.bubblePercent
                        val bubbleBg = when {
                            bubble == null -> Color(0xFFEEEEEE)
                            bubble < 0 -> Color(0xFFE8F5E9)
                            bubble <= 1.0 -> Color(0xFFE0F2F1)
                            else -> Color(0xFFFFEBEE)
                        }
                        val bubbleFg = when {
                            bubble == null -> Color(0xFF757575)
                            bubble < 0 -> Color(0xFF2E7D32)
                            bubble <= 1.0 -> Color(0xFF00796B)
                            else -> Color(0xFFC62828)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = bubbleBg
                        ) {
                            Text(
                                text = when {
                                    bubble == null -> "حباب نامشخص"
                                    bubble < 0 -> "تخفیف زیر NAV"
                                    bubble <= 1.0 -> "قیمت منصفانه"
                                    else -> "دارای حباب"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = bubbleFg
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 5 Item Cards from FundBase image (NAV، حباب، ارزش معاملات، حجم، دارایی تحت مدیریت)
                    FundBaseSymbolMetricCardsRow(
                        item = item,
                        onClick = onClickDetail
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Descriptive Tags Row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Bubble Tag
                StatusChip(
                    text = item.bubbleTierDescription,
                    level = item.bubbleStatus
                )

                // روند تکنیکال واقعی (جایگزین «پول هوشمند» که ساختگی بود)
                StatusChip(
                    text = item.trendDescription,
                    level = item.trendStatus
                )

                // Liquidity Tag
                StatusChip(
                    text = item.liquidityDescription,
                    level = if (item.liquidityToman != null || item.tradeValueToman != null) StatusLevel.GOOD else null
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Qualitative Explanations
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "• وضعیت حباب و NAV: ",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.bubbleTierDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "• روند تکنیکال: ",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.trendDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "• نقش در استراتژی هلد داینامیک: ",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.recommendationReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // FundBase 5 Ranking Cards interactive toggle bar
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isExpanded) "بستن تحلیل و رتبه‌بندی FundBase" else "مشاهده تحلیل تایم‌فریم‌ها، جمع‌بندی و رتبه‌بندی FundBase",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expandable Returns & Category Ranking Panel
            AnimatedVisibility(visible = isExpanded) {
                val insight = technicalInsight ?: FundTechnicalInsight(symbol = item.symbol)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. روند در تایم‌فریم‌ها (مطابق سایت FundBase)
                    FundBaseTimeframeTrendsSection(insight = insight)

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. جمع‌بندی کوتاه هوش مصنوعی (مطابق سایت FundBase)
                    FundBaseShortSummarySection(insight = insight, symbol = item.symbol)

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. رتبه در میان هم‌گروهی‌ها (کارت‌های مینیمال با رنگ‌بندی بدون دونات چارت حجیم)
                    FundBaseCategoryRankingSection(item = item)

                    Spacer(modifier = Modifier.height(14.dp))

                    val groupName = when {
                        item.category == AssetCategory.STOCK && item.isLeveraged -> "صندوق‌های سهامی اهرمی"
                        item.category == AssetCategory.STOCK -> "صندوق‌های سهامی کلاسیک"
                        item.category == AssetCategory.GOLD -> "صندوق‌های طلا"
                        item.category == AssetCategory.MIXED -> "صندوق‌های مختلط"
                        else -> "صندوق‌های درآمد ثابت"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "📊 وضعیت تکنیکال ($groupName):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (item.technical.lastDate.isNotBlank()) {
                                "تا ${item.technical.lastDate}"
                            } else {
                                "${item.totalInGroup} نماد در گروه"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!item.hasTechnicalData) {
                        // بدون داده واقعی، هیچ عددی نشان داده نمی‌شود
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "برای این نماد داده تکنیکالی در FundBase موجود نیست.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TechnicalMetricCell(
                                title = "از SMA۵۰",
                                value = item.technical.priceVsSma50Pct,
                                suffix = "%",
                                signed = true,
                                caption = interpretationCaption(
                                    TechnicalInterpreter.sma50(item.technical.priceVsSma50Pct)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            TechnicalMetricCell(
                                title = "از SMA۲۰۰",
                                value = item.technical.priceVsSma200Pct,
                                suffix = "%",
                                signed = true,
                                caption = interpretationCaption(
                                    TechnicalInterpreter.sma200(item.technical.priceVsSma200Pct)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            TechnicalMetricCell(
                                title = "RSI روزانه",
                                value = item.technical.rsi14,
                                suffix = "",
                                signed = false,
                                caption = interpretationCaption(
                                    TechnicalInterpreter.rsi(item.technical.rsi14)
                                ) ?: item.technical.rsiLabel,
                                modifier = Modifier.weight(1f)
                            )
                            TechnicalMetricCell(
                                title = "تا حمایت",
                                value = item.technical.nearestSupportDistPct,
                                suffix = "%",
                                signed = true,
                                caption = interpretationCaption(
                                    TechnicalInterpreter.support(item.technical.nearestSupportDistPct)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TrendCell("روزانه", item.technical.trendDaily, Modifier.weight(1f))
                            TrendCell("هفتگی", item.technical.trendWeekly, Modifier.weight(1f))
                            TrendCell("ماهانه", item.technical.trendMonthly, Modifier.weight(1f))
                            item.momentumScore?.let { m ->
                                TechnicalMetricCell(
                                    title = "امتیاز روند",
                                    value = m.toDouble(),
                                    suffix = "",
                                    signed = false,
                                    caption = if (item.rankMomentum != null) "رتبه ${item.rankMomentum} از ${item.totalInGroup}" else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        TechnicalInterpreter.summary(item.technical)?.let { summary ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🔎 ${Formatters.toPersianDigits(summary)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (item.technical.stateLabel.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "وضعیت اعلامی FundBase: ${item.technical.stateLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // آمار ریسک مستقل از داده تکنیکال است؛ صندوقی می‌تواند
                    // یکی را داشته باشد و دیگری را نه.
                    if (riskStats != null && riskStats.hasAnyStat) {
                        Spacer(modifier = Modifier.height(14.dp))
                        RiskStatsSection(riskStats)
                    }
                }
            }
        }
    }
}

/**
 * بخش «آمار ریسک» روی کارت صندوق.
 *
 * فقط وقتی نشان داده می‌شود که داده‌ای رسیده باشد — کادر خالی با سه تا «—»
 * چیزی به کاربر نمی‌گوید جز اینکه چیزی کار نکرده.
 */
@Composable
private fun RiskStatsSection(stats: FundRiskStats) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "🛡️ آمار ریسک (${Formatters.toPersianDigits(stats.windowDays.toString())} روز اخیر):",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        stats.sampleSize?.let { n ->
            Text(
                text = "${Formatters.toPersianDigits(n.toString())} روز معاملاتی",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        TechnicalMetricCell(
            title = "حداکثر افت",
            value = stats.maxDrawdownPct,
            suffix = "%",
            signed = true,
            caption = interpretationCaption(RiskInterpreter.maxDrawdown(stats.maxDrawdownPct)),
            modifier = Modifier.weight(1f)
        )
        TechnicalMetricCell(
            title = "نسبت شارپ",
            value = stats.sharpeRatio,
            suffix = "",
            signed = false,
            caption = interpretationCaption(
                RiskInterpreter.sharpe(stats.sharpeRatio, stats.windowDays)
            ),
            modifier = Modifier.weight(1f)
        )
        // نوسان علامت‌دار نشان داده نمی‌شود: «‎+۵۲٪‎» شبیه سود دیده می‌شود،
        // در حالی که نوسان نه سود است نه زیان.
        TechnicalMetricCell(
            title = "نوسان سالانه",
            value = stats.annualVolatilityPct,
            suffix = "%",
            signed = false,
            caption = interpretationCaption(RiskInterpreter.volatility(stats.annualVolatilityPct)),
            modifier = Modifier.weight(1f)
        )
    }

    if (stats.isSampleThin) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "⚠️ این آمار روی تعداد کمی روز معاملاتی حساب شده و هنوز " +
                    "قابل اتکا نیست؛ یک هفته پرنوسان می‌تواند کل عدد را تعیین کند.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    RiskInterpreter.summary(stats)?.let { summary ->
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "🔎 ${Formatters.toPersianDigits(summary)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

/**
 * سلول یک معیار تکنیکالِ واقعی. اگر مقدار null باشد «—» نشان می‌دهد
 * و هیچ عدد جایگزینی تولید نمی‌کند.
 */
@Composable
private fun TechnicalMetricCell(
    title: String,
    value: Double?,
    suffix: String,
    signed: Boolean,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    val valueColor = when {
        value == null -> MaterialTheme.colorScheme.onSurfaceVariant
        !signed -> MaterialTheme.colorScheme.onSurface
        value >= 0.0 -> Color(0xFF2E7D32)
        else -> Color(0xFFC62828)
    }

    val text = if (value == null) {
        "—"
    } else {
        val sign = if (signed && value > 0) "+" else ""
        "$sign${String.format(Locale.US, "%.1f", value)}$suffix"
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = valueColor
            )
            if (caption != null) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** روند یک بازه زمانی؛ رشته خالی یعنی داده نیست. */
@Composable
private fun TrendCell(
    title: String,
    trend: String,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (trend.lowercase()) {
        "up" -> "صعودی" to Color(0xFF2E7D32)
        "down" -> "نزولی" to Color(0xFFC62828)
        "side" -> "خنثی" to Color(0xFF757575)
        else -> "—" to Color(0xFF9E9E9E)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    level: StatusLevel?
) {
    // level = null یعنی داده‌ای برای این معیار نیست؛ خاکستری خنثی نمایش می‌دهیم
    val (bgColor, textColor) = when (level) {
        StatusLevel.EXCELLENT -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        StatusLevel.GOOD -> Color(0xFFE0F2F1) to Color(0xFF00695C)
        StatusLevel.FAIR -> Color(0xFFFFF8E1) to Color(0xFFF57F17)
        StatusLevel.RISKY -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        null -> Color(0xFFEEEEEE) to Color(0xFF757575)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun formatToman(amount: Double): String {
    val millions = amount / 1_000_000.0
    return if (millions >= 1.0) {
        String.format(Locale.US, "%,.1f میلیون", millions)
    } else {
        String.format(Locale.US, "%,.0f", amount)
    }
}

private fun formatPrice(amount: Double): String {
    return String.format(Locale.US, "%,.0f", amount)
}

fun formatTomanCompact(toman: Double): String {
    return when {
        toman >= 1_000_000_000_000.0 -> String.format(Locale.US, "%.1f همت", toman / 1_000_000_000_000.0)
        toman >= 1_000_000_000.0 -> String.format(Locale.US, "%.1f م.ت", toman / 1_000_000_000.0)
        toman >= 1_000_000.0 -> String.format(Locale.US, "%.0f میلیون", toman / 1_000_000.0)
        else -> String.format(Locale.US, "%,.0f تومان", toman)
    }
}

@Composable
fun FundBaseTimeframeTrendsSection(
    insight: FundTechnicalInsight,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (insight.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (insight.isLiveApi) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFE0F2FE),
                        border = BorderStroke(0.8.dp, Color(0xFFBAE6FD))
                    ) {
                        Text(
                            text = "FundBase دیتای زنده",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = Color(0xFF0369A1),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "روند در تایم‌فریم‌ها",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Timeframe Chips Row (روزانه، هفتگی، ماهانه)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TimeframeTrendChip(
                    timeframe = "روزانه",
                    trend = insight.trendDaily,
                    modifier = Modifier.weight(1f)
                )
                TimeframeTrendChip(
                    timeframe = "هفتگی",
                    trend = insight.trendWeekly,
                    modifier = Modifier.weight(1f)
                )
                TimeframeTrendChip(
                    timeframe = "ماهانه",
                    trend = insight.trendMonthly,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TimeframeTrendChip(
    timeframe: String,
    trend: String,
    modifier: Modifier = Modifier
) {
    val isUp = trend.equals("up", ignoreCase = true)
    val isDown = trend.equals("down", ignoreCase = true)

    val bgColor = when {
        isUp -> Color(0xFFE8F5E9)
        isDown -> Color(0xFFFFEBEE)
        else -> Color(0xFFF1F5F9)
    }
    val borderColor = when {
        isUp -> Color(0xFFA7F3D0)
        isDown -> Color(0xFFFECDD3)
        else -> Color(0xFFE2E8F0)
    }
    val textColor = when {
        isUp -> Color(0xFF15803D)
        isDown -> Color(0xFFB91C1C)
        else -> Color(0xFF475569)
    }
    val icon = when {
        isUp -> Icons.AutoMirrored.Filled.TrendingUp
        isDown -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.Default.TrendingFlat
    }
    val label = when {
        isUp -> "صعودی"
        isDown -> "نزولی"
        else -> "خنثی"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$timeframe: $label",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun FundBaseShortSummarySection(
    insight: FundTechnicalInsight,
    symbol: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.2.dp, Color(0xFF60A5FA)), // FundBase signature blue outline
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFF22C55E), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "تحلیل هوشمند",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "جمع‌بندی کوتاه",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (insight.isLoading && insight.summary.isBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "در حال بارگذاری تحلیل FundBase...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = insight.summary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "این تحلیل صرفاً جنبه دیده‌بانی داشته و توصیه مستقیم سرمایه‌گذاری نیست.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    ),
                    textAlign = TextAlign.Right
                )
            }
        }
    }
}

@Composable
fun FundBaseCategoryRankingSection(
    item: FundRankingItem,
    modifier: Modifier = Modifier
) {
    val totalCount = if (item.categoryPeerCount > 0) item.categoryPeerCount else item.totalInGroup
    val categoryName = item.categoryPersianName.ifBlank {
        when {
            item.category == AssetCategory.STOCK && item.isLeveraged -> "صندوق‌های سهامی اهرمی"
            item.category == AssetCategory.STOCK -> "صندوق‌های سهامی کلاسیک"
            item.category == AssetCategory.GOLD -> "صندوق‌های طلا"
            item.category == AssetCategory.MIXED -> "صندوق‌های مختلط"
            else -> "صندوق‌های درآمد ثابت"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.padding(start = 2.dp)
            ) {
                Text(
                    text = "$totalCount صندوق هم‌گروه",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "رتبه در میان $categoryName",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFE5A93C),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5 Minimal Cards with color-coding and progress indicators (Zero scroll clutter!)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Row 1
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Card 1: قدرت روند تکنیکال (جایگزین «بازدهی برتر» که داده‌اش ساختگی بود)
                MinimalFundBaseRankCard(
                    criteriaTitle = "قدرت روند",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    rank = item.rankMomentum,
                    total = totalCount,
                    subtitle = item.momentumScore?.let { "امتیاز $it از ۱۰۰" }
                        ?: "داده تکنیکال موجود نیست",
                    modifier = Modifier.weight(1f)
                )

                // Card 2: نقدشوندگی
                val liqValue = item.liquidityToman ?: item.tradeValueToman
                val liqSubtitle = liqValue?.let {
                    "${formatTomanCompact(it)} گردش"
                } ?: "داده موجود نیست"
                MinimalFundBaseRankCard(
                    criteriaTitle = "نقدشوندگی",
                    icon = Icons.Default.WaterDrop,
                    rank = item.rankLiquidity,
                    total = totalCount,
                    subtitle = liqSubtitle,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Card 3: کم‌حباب بودن
                val cardBubble = item.bubblePercent
                val bubbleSubtitle = if (cardBubble == null) {
                    "داده حباب موجود نیست"
                } else {
                    "${if (cardBubble > 0) "+" else ""}${String.format(Locale.US, "%.1f", cardBubble)}% نسبت به NAV"
                }
                MinimalFundBaseRankCard(
                    criteriaTitle = "کم‌حباب بودن",
                    icon = Icons.Default.Savings,
                    rank = item.rankLowBubble,
                    total = totalCount,
                    subtitle = bubbleSubtitle,
                    modifier = Modifier.weight(1f)
                )

                // Card 4: ارزش دارایی (AUM) — بدون تخمین از روی ارزش معاملات
                val aumSubtitle = item.netAssetToman?.let {
                    "${formatTomanCompact(it)} دارایی"
                } ?: "داده دارایی موجود نیست"
                MinimalFundBaseRankCard(
                    criteriaTitle = "ارزش کل دارایی",
                    icon = Icons.Default.AccountBalance,
                    rank = item.rankNetAsset,
                    total = totalCount,
                    subtitle = aumSubtitle,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 5: قدمت و سابقه فعالیت (Full width)
            val ageSubtitle = if (item.establishedDate.isNotBlank()) {
                "تأسیس: ${item.establishedDate}"
            } else {
                "سابقه فعالیت معاملاتی در بورس"
            }
            MinimalFundBaseRankCard(
                criteriaTitle = "قدمت و سابقه فعالیت",
                icon = Icons.Default.History,
                rank = item.rankAge,
                total = totalCount,
                subtitle = ageSubtitle,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class RankCardColors(
    val badgeBg: Color,
    val badgeText: Color,
    val badgeBorder: Color,
    val tierColor: Color
)

@Composable
fun MinimalFundBaseRankCard(
    criteriaTitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    /** null یعنی برای این معیار داده‌ای نبود و رتبه‌ای محاسبه نشده */
    rank: Int?,
    total: Int,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val totalCount = if (total > 0) total else 1
    val hasRank = rank != null
    val safeRank = rank ?: totalCount
    val ratio = safeRank.toFloat() / totalCount.toFloat()

    val cardColors = when {
        !hasRank -> RankCardColors(
            badgeBg = Color(0xFFEEEEEE),
            badgeText = Color(0xFF757575),
            badgeBorder = Color(0xFFE0E0E0),
            tierColor = Color(0xFF9E9E9E)
        )
        safeRank <= 3 || ratio <= 0.15f -> RankCardColors(
            badgeBg = Color(0xFFE8F5E9),
            badgeText = Color(0xFF15803D),
            badgeBorder = Color(0xFFA7F3D0),
            tierColor = Color(0xFF16A34A)
        )
        ratio <= 0.40f -> RankCardColors(
            badgeBg = Color(0xFFE0F2FE),
            badgeText = Color(0xFF0369A1),
            badgeBorder = Color(0xFFBAE6FD),
            tierColor = Color(0xFF0284C7)
        )
        ratio <= 0.70f -> RankCardColors(
            badgeBg = Color(0xFFFEF3C7),
            badgeText = Color(0xFF92400E),
            badgeBorder = Color(0xFFFDE68A),
            tierColor = Color(0xFFD97706)
        )
        else -> RankCardColors(
            badgeBg = Color(0xFFFFE4E6),
            badgeText = Color(0xFFBE123C),
            badgeBorder = Color(0xFFFECDD3),
            tierColor = Color(0xFFE11D48)
        )
    }
    val badgeBg = cardColors.badgeBg
    val badgeText = cardColors.badgeText
    val badgeBorder = cardColors.badgeBorder
    val tierColor = cardColors.tierColor

    val sweepFraction = if (!hasRank) {
        0.08f
    } else {
        ((totalCount - safeRank + 1).toFloat() / totalCount.toFloat()).coerceIn(0.08f, 1f)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = sweepFraction,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "rank_progress"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Top Row: Title + Icon (right) and Rank Badge (left)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left: Rank Badge
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeBg,
                    border = BorderStroke(0.8.dp, badgeBorder)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        if (hasRank && safeRank <= 3) {
                            Text(
                                text = "★",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = badgeText
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = if (hasRank) "رتبه $safeRank از $totalCount" else "بدون رتبه",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            ),
                            color = badgeText
                        )
                    }
                }

                // Right: Icon + Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = criteriaTitle,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tierColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Sleek Minimal Percentile Bar (height 3.5.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(2.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(3.5.dp)
                        .background(
                            color = tierColor,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom Row: Subtitle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun FundBaseRankingDialog(
    fund: FundRankingItem,
    insight: FundTechnicalInsight? = null,
    onDismiss: () -> Unit
) {
    val activeInsight = insight ?: FundTechnicalInsight(symbol = fund.symbol)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("بستن")
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "تحلیل و رتبه‌بندی FundBase: ${fund.symbol}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFE5A93C)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                // 1. Display 5 key metrics from image in Toman
                Text(
                    text = "شاخص‌های بنیادین (واحد: تومان):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                FundBaseSymbolMetricCardsRow(item = fund)

                Spacer(modifier = Modifier.height(14.dp))

                // 2. روند در تایم‌فریم‌ها
                FundBaseTimeframeTrendsSection(insight = activeInsight)

                Spacer(modifier = Modifier.height(10.dp))

                // 3. جمع‌بندی کوتاه هوش مصنوعی
                FundBaseShortSummarySection(insight = activeInsight, symbol = fund.symbol)

                Spacer(modifier = Modifier.height(14.dp))

                // 4. رتبه در میان هم‌گروهی‌ها (کارتهای مینیمال جدید)
                FundBaseCategoryRankingSection(item = fund)
            }
        }
    )
}

/**
 * 5 Attractive Cards from FundBase image for each symbol (All in Toman):
 * 1. NAV (ارزش ذاتی ابطال)
 * 2. حباب قیمتی
 * 3. ارزش معاملات
 * 4. حجم معاملات
 * 5. دارایی تحت مدیریت (AUM)
 */
@Composable
fun FundBaseSymbolMetricCardsRow(
    item: FundRankingItem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    // هر معیاری که داده نداشته باشد «—» نشان داده می‌شود، نه صفر.
    val navText = item.navPriceRial?.let { String.format(Locale.US, "%,.0f", it / 10.0) } ?: "—"

    val bubble = item.bubblePercent
    val bubbleText = if (bubble == null) {
        "—"
    } else {
        "${if (bubble > 0) "+" else ""}${String.format(Locale.US, "%.2f", bubble)}%"
    }
    val bubbleColor = when {
        bubble == null -> Color(0xFF9E9E9E)
        bubble < 0 -> Color(0xFF2E7D32) // تخفیف
        bubble <= 1.0 -> Color(0xFF00796B) // منصفانه
        else -> Color(0xFFC62828) // حباب
    }

    val tradeValueText = item.tradeValueToman?.let { formatTradeValueTomanLabel(it) } ?: "—"
    val tradeVolumeText = item.tradeVolume?.let { String.format(Locale.US, "%,d", it) } ?: "—"
    val aumText = item.netAssetToman?.let { formatAumTomanLabel(it) } ?: "—"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Card 1: NAV
        FundBaseImageMetricCard(
            title = "NAV",
            unit = "تومان",
            value = navText,
            icon = Icons.Default.AutoAwesome,
            valueColor = MaterialTheme.colorScheme.onSurface,
            onClick = onClick
        )

        // Card 2: حباب قیمتی
        FundBaseImageMetricCard(
            title = "حباب قیمتی",
            unit = "",
            value = bubbleText,
            icon = Icons.Default.BarChart,
            valueColor = bubbleColor,
            onClick = onClick
        )

        // Card 3: ارزش معاملات
        FundBaseImageMetricCard(
            title = "ارزش معاملات",
            unit = "",
            value = tradeValueText,
            icon = Icons.Default.AccountBalanceWallet,
            valueColor = MaterialTheme.colorScheme.onSurface,
            onClick = onClick
        )

        // Card 4: حجم معاملات
        FundBaseImageMetricCard(
            title = "حجم معاملات",
            unit = "برگه",
            value = tradeVolumeText,
            icon = Icons.Default.ShowChart,
            valueColor = MaterialTheme.colorScheme.onSurface,
            onClick = onClick
        )

        // Card 5: دارایی تحت مدیریت (AUM)
        FundBaseImageMetricCard(
            title = "دارایی تحت مدیریت",
            unit = "",
            value = aumText,
            icon = Icons.Default.AccountBalanceWallet,
            valueColor = MaterialTheme.colorScheme.primary,
            onClick = onClick
        )
    }
}

@Composable
private fun FundBaseImageMetricCard(
    title: String,
    unit: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valueColor: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = Modifier
            .widthIn(min = 126.dp)
            .let { if (onClick != null) it.clickable { onClick() } else it }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Header: Title and Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Value and Unit
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = valueColor
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

fun formatTradeValueTomanLabel(toman: Double): String {
    return when {
        toman >= 1_000_000_000_000.0 -> String.format(Locale.US, "%.1f همت", toman / 1_000_000_000_000.0)
        toman >= 1_000_000_000.0 -> {
            val billions = toman / 1_000_000_000.0
            if (billions >= 10.0) {
                String.format(Locale.US, "%.0f میلیارد ت", billions)
            } else {
                String.format(Locale.US, "%.1f میلیارد ت", billions)
            }
        }
        toman >= 1_000_000.0 -> String.format(Locale.US, "%.0f میلیون ت", toman / 1_000_000.0)
        else -> String.format(Locale.US, "%,.0f ت", toman)
    }
}

fun formatAumTomanLabel(toman: Double): String {
    return when {
        toman >= 1_000_000_000_000.0 -> String.format(Locale.US, "%.1f همت", toman / 1_000_000_000_000.0)
        toman >= 100_000_000_000.0 -> String.format(Locale.US, "%.1f همت", toman / 1_000_000_000_000.0) // e.g. 0.2 همت
        toman >= 1_000_000_000.0 -> String.format(Locale.US, "%.0f م.ت", toman / 1_000_000_000.0)
        else -> String.format(Locale.US, "%,.0f ت", toman)
    }
}

/**
 * متن یک تفسیر را برای نمایش در سلول آماده می‌کند: ارقام لاتینِ داخل متن به
 * فارسی تبدیل می‌شوند تا کنار بقیه اعداد برنامه ناجور نباشد.
 *
 * منطق تفسیر در [TechnicalInterpreter] است و ارقام را لاتین برمی‌گرداند —
 * چون آنجا منطق خالص است و تستش نباید به شکل نمایش گره بخورد.
 */
private fun interpretationCaption(interpretation: Interpretation?): String? =
    interpretation?.let { Formatters.toPersianDigits(it.text) }
