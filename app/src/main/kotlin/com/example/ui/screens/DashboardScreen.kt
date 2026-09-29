package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AssetCategoryCard
import com.example.ui.components.PcmrDonutChart
import com.example.ui.components.PercentBadge
import com.example.ui.components.PortfolioSymbolCard
import com.example.ui.components.RebalanceGauge
import com.example.ui.theme.SignalBuy
import com.example.ui.theme.SignalSell
import com.example.ui.components.PortfolioGrowthChart
import com.example.domain.portfolio.PortfolioPnl
import com.example.domain.portfolio.PortfolioWeights
import com.example.domain.sync.MarketDataFreshness
import com.example.ui.util.Formatters
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.PcmrUiState

@Composable
fun DashboardScreen(
    uiState: PcmrUiState,
    onNavigate: (AppScreen) -> Unit,
    onLoadSample: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val calc = uiState.calculationResult
    val items = uiState.portfolioItems

    // سود و زیان سبد — قاعده‌اش در PortfolioPnl است تا داشبورد، صفحه واریز و
    // برداشت و ثبت تاریخچه هر سه یک عدد بدهند.
    val pnl = PortfolioPnl.calculate(items)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. EasyTrader 3 Summary Cards at the top
        item {
            DashboardSummarySection(
                totalPv = calc.totalPv,
                pnl = pnl,
                marketDataAgeMs = uiState.marketDataAgeMs,
                riskTolerance = uiState.settings.riskTolerance,
                timeHorizonMonths = uiState.settings.timeHorizonMonths
            )
        }

        // 1-ب. روند ارزش سبد
        item {
            PortfolioGrowthChart(
                snapshots = uiState.snapshots,
                onOpenHistory = { onNavigate(AppScreen.HISTORY) }
            )
        }

        // Empty state prompt if portfolio is empty
        if (items.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "پورتفوی خالی است",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "جهت شروع محاسبات استراتژی PCMR، فایل اکسل کارگزاری یا نمادهای خود را بارگذاری نمایید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onNavigate(AppScreen.INGESTION) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("بارگذاری اکسل پورتفوی")
                        }
                    }
                }
            }
        } else {
            // 2. TR Index Rebalance Gauge
            item {
                RebalanceGauge(
                    trIndex = calc.trIndex,
                    thresholdLimit = calc.thresholdLimit,
                    isTriggered = calc.isRebalanceTriggered,
                    balancedVolumeRial = calc.balancedExecutionAmount,
                    totalBuyRial = calc.totalBuyAmount,
                    totalSellRial = calc.totalSellAmount,
                    lockBoundTrIndex = calc.lockBoundTrIndex
                )
            }

            // 3. Asset Allocation Donut Chart
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ترکیب تخصیص دارایی‌ها",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                            Text(
                                text = "ریسک سبد: ${Formatters.formatPercent(calc.calculatedRt)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (calc.calculatedRt > calc.investorRt) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        PcmrDonutChart(
                            breakdowns = calc.categoryBreakdowns,
                            totalPv = calc.totalPv
                        )
                    }
                }
            }

            // 4. Quick Action Shortcut to Signals & Calculator
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onNavigate(AppScreen.SIGNALS) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (calc.isRebalanceTriggered) SignalSell else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (calc.isRebalanceTriggered) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (calc.isRebalanceTriggered) "سیگنال‌های ریبلنس" else "بررسی سیگنال‌ها",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedButton(
                        onClick = { onNavigate(AppScreen.CALCULATOR) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "واریز و برداشت",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // 5. Category Breakdown Cards Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "کلاس‌های دارایی پورتفوی (S, G, M, F)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            items(calc.categoryBreakdowns, key = { it.category.name }) { breakdown ->
                AssetCategoryCard(breakdown = breakdown)
            }

            // 6. Individual Symbol Holdings Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "نمادها و دارایی‌های پورتفوی (${items.size} نماد)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "ترتیب بر اساس ارزش",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Render individual symbol cards with EasyTrader style
            val sortedItems = items.sortedByDescending { it.currentValue }
            val weights = PortfolioWeights.forItems(items, calc.symbolAlerts)
            items(sortedItems, key = { it.id }) { itemEntity ->
                PortfolioSymbolCard(item = itemEntity, weight = weights[itemEntity.id])
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * EasyTrader-inspired 3 Summary Cards section for Dashboard.
 * 1. دارایی کل (PV)
 * 2. سود و زیان پرتفوی (با بج درصد سبز/قرمز)
 * 3. اصل سرمایه اولیه
 */
@Composable
fun DashboardSummarySection(
    totalPv: Double,
    pnl: PortfolioPnl,
    marketDataAgeMs: Long?,
    riskTolerance: Double,
    timeHorizonMonths: Int,
    modifier: Modifier = Modifier
) {
    val totalProfit = pnl.profitRial
    val isProfitPositive = totalProfit >= 0.0
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 3 Cards in a responsive Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card 1: دارایی کل
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "دارایی کل",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Formatters.formatCompactToman(totalPv),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "تومان",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Card 2: سود و زیان پرتفوی
            val profitColor = if (isProfitPositive) SignalBuy else SignalSell
            Card(
                modifier = Modifier.weight(1.15f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "سود / زیان",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (pnl.hasBasis) {
                            PercentBadge(
                                percent = pnl.profitPercent,
                                includeSign = true,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // بدون قیمت خرید، سود «صفر» نیست بلکه نامعلوم است. عدد صفرِ
                    // سبز به کاربر می‌گفت سر به سر است، که ادعای غلطی بود.
                    Text(
                        text = if (pnl.hasBasis) {
                            "${if (isProfitPositive) "+" else ""}${Formatters.formatCompactToman(totalProfit)}"
                        } else {
                            "نامشخص"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (pnl.hasBasis) profitColor else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (pnl.hasBasis) "تومان" else "قیمت خرید وارد نشده",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (pnl.hasBasis) {
                            profitColor.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Card 3: اصل سرمایه
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "اصل سرمایه",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (pnl.hasBasis) {
                            Formatters.formatCompactToman(pnl.principalRial)
                        } else {
                            Formatters.formatCompactToman(totalPv)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "افق $timeHorizonMonths ماهه",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // بخشی از سبد که مبنای خرید ندارد باید صریح گفته شود، وگرنه کاربر عدد
        // سود را به کل سبد نسبت می‌دهد.
        if (pnl.hasBasis && pnl.isPartial) {
            Text(
                text = "سود و زیان از ${pnl.coveredCount} نماد از ${pnl.totalCount} نماد حساب شده؛ " +
                        "${Formatters.formatCompactToman(pnl.uncoveredValueRial)} بدون قیمت خرید است.",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // کاربر عدد اپ را با صفحه کارگزاری مقایسه می‌کند؛ بدون زمانِ قیمت،
        // اختلافِ طبیعیِ دو لحظه مختلف به حساب خطای محاسبه گذاشته می‌شود.
        if (marketDataAgeMs != null) {
            Text(
                text = "قیمت‌ها: ${MarketDataFreshness.describeAge(marketDataAgeMs)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        // Full Detailed Sub-Card with exact Rial and Toman amount & RT badge
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ارزش کل پورتفوی:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = Formatters.formatToman(totalPv),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "تحمل ریسک: ${Formatters.formatPercent(riskTolerance)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

