package com.example

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.data.alert.RebalanceAlertScheduler
import com.example.platform.Context
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DepositWithdrawScreen
import com.example.ui.screens.FundRankingScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.IngestionScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignalsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.PcmrViewModel

fun main() = application {
    val viewModel = remember { PcmrViewModel() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "PCMR • بازتعادل پویای پورتفوی",
        state = rememberWindowState(size = DpSize(1180.dp, 800.dp))
    ) {
        MaterialThemeHost(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialThemeHost(viewModel: PcmrViewModel) {

            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val uiState by viewModel.uiState.collectAsState()
                    val inflationRates by viewModel.inflationRates.collectAsState()
                    val isLoadingInflation by viewModel.isLoadingInflation.collectAsState()
                    val snackbarHostState = remember { SnackbarHostState() }

                    val hasNotificationPermission = true
                    val ctx = remember { Context() }
                    val clipboard = LocalClipboardManager.current
                    fun shareText(text: String, @Suppress("UNUSED_PARAMETER") subject: String) {
                        if (text.isNotBlank()) clipboard.setText(AnnotatedString(text))
                        viewModel.showMessage("متن در کلیپ‌بورد کپی شد")
                    }

                    // بدون این، دکمه بازگشت از هر تبی مستقیم اپ را می‌بست.
                    // حالا اول به داشبورد برمی‌گردد و فقط از آنجا خارج می‌شود.
                    val symbolIsinMap by viewModel.symbolIsinMap.collectAsState()

                    
                    LaunchedEffect(uiState.userMessage) {
                        uiState.userMessage?.let { msg ->
                            snackbarHostState.showSnackbar(msg)
                            viewModel.clearUserMessage()
                        }
                    }

                    Scaffold(
                        topBar = {
                            Column {
                                TopAppBar(
                                    title = {
                                        Text(
                                            text = when (uiState.currentScreen) {
                                                AppScreen.DASHBOARD -> "پورتفوی من • PCMR"
                                                AppScreen.RANKING -> "دیده‌بان و فیلتر صندوق‌ها"
                                                AppScreen.SIGNALS -> "سیگنال‌های ریبلنس و معاملات"
                                                AppScreen.CALCULATOR -> "ماشین‌حساب واریز و برداشت"
                                                AppScreen.INGESTION -> "بارگذاری دارایی‌ها و اکسل"
                                                AppScreen.SETTINGS -> "تنظیمات استراتژی و EML"
                                                AppScreen.HISTORY -> "تاریخچه سود و زیان و ترکیب سبد"
                                            },
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.background
                                    )
                                )
                                HorizontalDivider(
                                    thickness = 0.6.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            }
                        },
                        bottomBar = {
                            Column {
                                HorizontalDivider(
                                    thickness = 0.8.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                )
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 2.dp
                                ) {
                                    val itemColors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // 1. Dashboard
                                    val isDashboard = uiState.currentScreen == AppScreen.DASHBOARD
                                    NavigationBarItem(
                                        selected = isDashboard,
                                        onClick = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                                        icon = {
                                            Icon(
                                                imageVector = if (isDashboard) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                                                contentDescription = "داشبورد"
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "پورتفوی",
                                                fontWeight = if (isDashboard) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                        },
                                        colors = itemColors
                                    )

                                    // 2. Ranking / Screener
                                    val isRanking = uiState.currentScreen == AppScreen.RANKING
                                    NavigationBarItem(
                                        selected = isRanking,
                                        onClick = { viewModel.navigateTo(AppScreen.RANKING) },
                                        icon = {
                                            Icon(
                                                imageVector = if (isRanking) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                                contentDescription = "دیده‌بان"
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "دیده‌بان",
                                                fontWeight = if (isRanking) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                        },
                                        colors = itemColors
                                    )

                                    // 3. Signals
                                    val isSignals = uiState.currentScreen == AppScreen.SIGNALS
                                    NavigationBarItem(
                                        selected = isSignals,
                                        onClick = { viewModel.navigateTo(AppScreen.SIGNALS) },
                                        icon = {
                                            val iconVector = if (isSignals) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Outlined.TrendingUp
                                            if (uiState.calculationResult.isRebalanceTriggered) {
                                                BadgedBox(badge = { Badge { Text("!") } }) {
                                                    Icon(iconVector, contentDescription = "سیگنال‌ها")
                                                }
                                            } else {
                                                Icon(iconVector, contentDescription = "سیگنال‌ها")
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = "سیگنال‌ها",
                                                fontWeight = if (isSignals) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                        },
                                        colors = itemColors
                                    )

                                    // 4. Calculator
                                    val isCalc = uiState.currentScreen == AppScreen.CALCULATOR
                                    NavigationBarItem(
                                        selected = isCalc,
                                        onClick = { viewModel.navigateTo(AppScreen.CALCULATOR) },
                                        icon = {
                                            Icon(
                                                imageVector = if (isCalc) Icons.Filled.Calculate else Icons.Outlined.Calculate,
                                                contentDescription = "واریز/برداشت"
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "تراکنش",
                                                fontWeight = if (isCalc) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                        },
                                        colors = itemColors
                                    )

                                    // 5. Ingestion
                                    val isIngestion = uiState.currentScreen == AppScreen.INGESTION
                                    NavigationBarItem(
                                        selected = isIngestion,
                                        onClick = { viewModel.navigateTo(AppScreen.INGESTION) },
                                        icon = {
                                            Icon(
                                                imageVector = if (isIngestion) Icons.Filled.FolderOpen else Icons.Outlined.FolderOpen,
                                                contentDescription = "بارگذاری"
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "بارگذاری",
                                                fontWeight = if (isIngestion) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                        },
                                        colors = itemColors
                                    )

                                    // 6. Settings
                                    val isSettings = uiState.currentScreen == AppScreen.SETTINGS
                                    NavigationBarItem(
                                        selected = isSettings,
                                        onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSettings) Icons.Filled.Settings else Icons.Outlined.Settings,
                                                contentDescription = "تنظیمات"
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "تنظیمات",
                                                fontWeight = if (isSettings) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                        },
                                        colors = itemColors
                                    )
                                }
                            }
                        },
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            AnimatedContent(
                                targetState = uiState.currentScreen,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "ScreenTransition"
                            ) { screen ->
                                when (screen) {
                                    AppScreen.DASHBOARD -> DashboardScreen(
                                        uiState = uiState,
                                        onNavigate = { viewModel.navigateTo(it) },
                                        onLoadSample = { viewModel.loadSampleData() }
                                    )
                                    AppScreen.RANKING -> FundRankingScreen(
                                        uiState = uiState,
                                        // رفرشِ دستی: هم کش را دور می‌زند و هم نتیجه را
                                        // به کاربر گزارش می‌دهد. قبلاً هیچ‌کدام را نمی‌کرد و
                                        // دکمه بی‌اثر به نظر می‌رسید.
                                        onRefreshAnalysis = {
                                            viewModel.refreshFundAnalysis(
                                                announce = true,
                                                forceRefresh = true
                                            )
                                            viewModel.refreshAlanchandQuotes(forceRefresh = true)
                                        },
                                        viewModel = viewModel
                                    )
                                    AppScreen.SIGNALS -> SignalsScreen(
                                        uiState = uiState,
                                        executableOrders = { viewModel.executableOrders() },
                                        onApplyExecutedOrders = { ids ->
                                            viewModel.applyExecutedOrders(ids)
                                        },
                                        onExportCsv = { uri ->
                                            viewModel.exportSignalsCsv(ctx, uri)
                                        },
                                        onShareText = {
                                            shareText(
                                                viewModel.signalsShareText(),
                                                "سفارش‌های ریبلنس"
                                            )
                                        },
                                        exportFileName = { viewModel.signalsFileName() },
                                        symbolIsinMap = symbolIsinMap,
                                        onSaveIsin = { symbol, pasted ->
                                            viewModel.saveSymbolIsin(symbol, pasted)
                                        },
                                        onToggleHoldLock = { itemId, locked ->
                                            viewModel.setHoldLock(itemId, locked)
                                        }
                                    )
                                    AppScreen.CALCULATOR -> DepositWithdrawScreen(
                                        uiState = uiState,
                                        onTypeChange = { viewModel.setCalculatorTransactionType(it) },
                                        onAmountChange = { viewModel.setCalculatorAmountToman(it) },
                                        onExportPlanCsv = { uri ->
                                            viewModel.exportWithdrawalPlanCsv(ctx, uri)
                                        },
                                        onSharePlan = {
                                            shareText(
                                                viewModel.withdrawalShareText(),
                                                "برنامه برداشت"
                                            )
                                        },
                                        planFileName = { viewModel.withdrawalPlanFileName() }
                                    )
                                    AppScreen.INGESTION -> IngestionScreen(
                                        uiState = uiState,
                                        onImportPortfolio = { uri -> viewModel.importPortfolioFile(ctx, uri) },
                                        onAddOrUpdateItem = { id, sym, cat, valRial, qty, price ->
                                            viewModel.addOrUpdateSymbol(id, sym, cat, valRial, qty, price)
                                        },
                                        onDeleteItem = { id -> viewModel.deleteSymbol(id) },
                                        onClearAll = { viewModel.clearAllPortfolio() },
                                        onExportBackup = { uri ->
                                            viewModel.exportBackup(ctx, uri)
                                        },
                                        onRestoreBackup = { uri ->
                                            viewModel.restoreBackup(ctx, uri)
                                        },
                                        backupFileName = { viewModel.suggestedBackupFileName() }
                                    )
                                    AppScreen.HISTORY -> HistoryScreen(uiState = uiState)
                                    AppScreen.SETTINGS -> SettingsScreen(
                                        settings = uiState.settings,
                                        onUpdateRt = { viewModel.updateRiskTolerance(it) },
                                        onUpdateTimeHorizon = { viewModel.updateTimeHorizon(it) },
                                        onUpdateEml = { horizon, s, g -> viewModel.updateEml(horizon, s, g) },
                                        onUpdateThreshold = { viewModel.updateRebalanceThreshold(it) },
                                        onSetManualWeights = { enabled, g, s, m, f ->
                                            viewModel.setManualWeights(enabled, g, s, m, f)
                                        },
                                        onSetLivePriceSync = { viewModel.setLivePriceSync(it) },
                                        onRoundWeights = { viewModel.roundTargetWeightsToMultiples(it) },
                                        hasNotificationPermission = hasNotificationPermission,
                                        onOpenNotificationSettings = {},
                                        onSetRebalanceAlert = { enabled -> viewModel.setRebalanceAlert(enabled) },
                                        inflationRates = inflationRates,
                                        isLoadingInflation = isLoadingInflation,
                                        onRefreshInflation = { viewModel.refreshInflationRates(announce = true) },
                                        onSaveManualInflation = { year, month, rate ->
                                            viewModel.saveManualInflationRate(year, month, rate)
                                        },
                                        onDeleteInflation = { year, month ->
                                            viewModel.deleteInflationRate(year, month)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
}
