package com.example.data.alert

import com.example.data.local.AppDatabase
import com.example.data.local.PortfolioScope
import com.example.data.repository.PcmrRepository
import com.example.domain.alert.RebalanceAlertPolicy
import com.example.domain.calculator.PcmrEngine
import com.example.ui.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * بررسی دوره‌ای شاخص انحراف سبد (جایگزین WorkManager اندروید).
 * تا وقتی برنامه باز است (حتی کوچک‌شده در System Tray) هر ۶ ساعت اجرا می‌شود.
 * تصمیم «اطلاع بدهیم یا نه» همان [RebalanceAlertPolicy] است.
 */
object RebalanceAlertScheduler {
    private const val INTERVAL_MS = 6L * 60 * 60 * 1000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Synchronized
    fun apply(enabled: Boolean) {
        job?.cancel()
        job = null
        if (!enabled) return
        job = scope.launch {
            delay(30_000) // اجازه بده برنامه بالا بیاید
            while (isActive) {
                runCatching { checkOnce() }
                delay(INTERVAL_MS)
            }
        }
    }

    suspend fun checkOnce() {
        val db = AppDatabase.getDatabase()
        val portfolios = PcmrRepository(db).allPortfolios()
        for (p in portfolios) checkPortfolio(db, p.id, PcmrRepository(db, PortfolioScope(p.id)), p.name, portfolios.size > 1)
    }

    private suspend fun checkPortfolio(db: AppDatabase, portfolioId: Long, repository: PcmrRepository, name: String, multi: Boolean) {
        val settings = repository.getCurrentSettings()
        if (!settings.isRebalanceAlertEnabled) return

        repository.syncLivePrices(revalueFromQuantity = settings.isLivePriceSyncEnabled)

        val directory = db.portfolioDirectoryDao()
        val (wasAbove, lastAlert) = directory.alertState(portfolioId)
        val items = repository.getCurrentPortfolioItems()
        val calculation = PcmrEngine.calculatePortfolio(items, settings)

        val decision = RebalanceAlertPolicy.decide(
            trIndex = calculation.trIndex,
            thresholdPercent = settings.rebalanceThresholdPercent,
            hasPortfolio = items.isNotEmpty() && calculation.totalPv > 0.0,
            state = RebalanceAlertPolicy.AlertState(
                wasAboveThreshold = wasAbove,
                lastAlertEpochMs = lastAlert
            ),
            nowEpochMs = System.currentTimeMillis()
        )

        if (decision.shouldNotify) {
            RebalanceNotifier.show(
                title = if (multi) "وقت بررسی ریبلنس • $name" else "وقت بررسی ریبلنس",
                body = RebalanceAlertPolicy.notificationText(
                    trIndex = calculation.trIndex,
                    thresholdPercent = settings.rebalanceThresholdPercent,
                    reason = decision.reason,
                    lockBoundTrIndex = calculation.lockBoundTrIndex,
                    lockedSymbols = calculation.lockedHold?.bindingSymbols.orEmpty(),
                    requiredCashText = calculation.lockedHold
                        ?.takeIf { it.requiredCashRial > 0.0 && !it.isUnreachableByCash }
                        ?.let { Formatters.formatCompactToman(it.requiredCashRial) }
                )
            )
        }

        val newState = decision.newState
        if (newState.wasAboveThreshold != wasAbove || newState.lastAlertEpochMs != lastAlert) {
            directory.saveAlertState(portfolioId, newState.wasAboveThreshold, newState.lastAlertEpochMs)
        }
    }
}
