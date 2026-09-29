package com.example.ui.viewmodel

import com.example.platform.Context
import com.example.platform.Uri
import com.example.platform.AppLog as Log
import com.example.platform.ViewModel

import com.example.data.local.AppDatabase
import com.example.data.model.AssetCategory
import com.example.data.model.DepositWithdrawalResult
import com.example.data.model.FundRankingItem
import com.example.data.model.FundRiskStats
import com.example.data.model.FundTechnicalInsight
import com.example.data.model.HoldingSnapshotEntity
import com.example.data.model.PortfolioCalculationResult
import com.example.data.model.PortfolioEntity
import com.example.data.model.PortfolioSnapshotEntity
import com.example.data.model.SettingsEntity
import com.example.data.model.TimeHorizon
import com.example.data.model.TransactionType
import com.example.data.parser.DefaultEtfDatabase
import com.example.data.alert.RebalanceAlertScheduler
import com.example.data.model.AlanchandQuotes
import com.example.data.model.InflationRateEntity
import com.example.data.remote.AlanchandApiService
import com.example.data.remote.CbiInflationApiService
import com.example.data.remote.FundBaseApiService
import com.example.data.remote.TsetmcFundService
import com.example.data.repository.PcmrRepository
import com.example.domain.calculator.PcmrEngine
import com.example.domain.broker.EasyTraderLink
import com.example.domain.calculator.WithdrawalPlanner
import com.example.domain.export.OrderExport
import com.example.domain.portfolio.OrderExecution
import com.example.domain.portfolio.PortfolioEdit
import com.example.domain.sync.MarketDataFreshness
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen(val persianTitle: String) {
    DASHBOARD("داشبورد"),
    RANKING("دیده‌بان"),
    SIGNALS("سیگنال‌ها"),
    CALCULATOR("ماشین‌حساب"),
    INGESTION("بارگذاری"),
    SETTINGS("تنظیمات"),
    HISTORY("تاریخچه")
}

data class PcmrUiState(
    val currentScreen: AppScreen = AppScreen.DASHBOARD,
    val settings: SettingsEntity = SettingsEntity(),
    val portfolioItems: List<PortfolioEntity> = emptyList(),
    val calculationResult: PortfolioCalculationResult = PcmrEngine.calculatePortfolio(emptyList(), SettingsEntity()),
    // Dynamic Calculator State
    val calcTransactionType: TransactionType = TransactionType.DEPOSIT,
    val calcAmountToman: Double = 50_000_000.0, // 50 Million Toman default
    val depositWithdrawResult: DepositWithdrawalResult? = null,
    // Fund Analysis & Ranking
    val fundRankingList: List<FundRankingItem> = emptyList(),
    val isAnalyzingFunds: Boolean = false,
    // Status feedback
    val userMessage: String? = null,
    val isImporting: Boolean = false,
    /**
     * نمادهایی که دسته‌شان حدس زده شده و کاربر تأییدشان نکرده.
     *
     * دستهٔ اشتباه بی‌ضرر نیست: وزن هدف طبقات و در نتیجه سفارش‌های خرید/فروش را
     * جابه‌جا می‌کند. تا امروز این حدس‌ها بی‌صدا اعمال می‌شدند.
     */
    val guessedCategorySymbols: Set<String> = emptySet(),
    /**
     * سن داده بازار در آخرین بررسی؛ null یعنی هیچ دریافت موفقی انجام نشده.
     * UI بر اساس این تصمیم می‌گیرد که نوار «داده کهنه/آفلاین» را نشان دهد.
     */
    val marketDataAgeMs: Long? = null,
    /** تاریخ شمسی داده بازدهی، همان‌طور که API می‌دهد. */
    val returnsAsOfJalaliDate: String = "",
    /** تاریخچه روزانه سبد برای نمودار رشد؛ قدیمی‌ترین اول. */
    val snapshots: List<PortfolioSnapshotEntity> = emptyList(),
    /** ترکیب سبد در هر روز ثبت‌شده، برای صفحه تاریخچه. */
    val holdingSnapshots: List<HoldingSnapshotEntity> = emptyList(),
    /**
     * برنامه برداشت با رعایت محدودیت‌های بازار — فقط در حالت برداشت پر می‌شود.
     * برخلاف خروجی خام ماشین‌حساب، هر سفارش اینجا واقعاً قابل ثبت است.
     */
    val withdrawalPlan: WithdrawalPlanner.Plan? = null,
    /**
     * قیمت طلا، سکه و دلار آزاد از آلان‌چند، برای کارت بالای دیده‌بان.
     * منبعی جدا از FundBase است — قیمت نقدی خودِ فلز/ارز، نه NAV صندوق.
     */
    val alanchandQuotes: AlanchandQuotes? = null,
    val isLoadingAlanchandQuotes: Boolean = false,
    /**
     * نمادهایی که بیش از یک ردیف در سبد دارند.
     *
     * موتور هر ردیف را جدا حساب می‌کند و دیده‌بان ارزش‌ها را جمع می‌زند، پس
     * محاسبات درست می‌مانند — ولی معمولاً این یعنی کاربر یک نماد را دوبار وارد
     * کرده و بهتر است ادغامشان کند.
     */
    val duplicateSymbols: Set<String> = emptySet()
)

class PcmrViewModel : ViewModel() {

    private val repository: PcmrRepository

    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    private val _calcTransactionType = MutableStateFlow(TransactionType.DEPOSIT)
    private val _calcAmountToman = MutableStateFlow(50_000_000.0) // Toman
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _isImporting = MutableStateFlow(false)
    private val _fundRankingList = MutableStateFlow<List<FundRankingItem>>(emptyList())
    private val _isAnalyzingFunds = MutableStateFlow(false)
    private val _marketDataAgeMs = MutableStateFlow<Long?>(null)
    private val _returnsAsOfJalaliDate = MutableStateFlow("")
    private val _alanchandQuotes = MutableStateFlow<AlanchandQuotes?>(null)
    private val _isLoadingAlanchandQuotes = MutableStateFlow(false)
    private val _technicalInsights = MutableStateFlow<Map<String, FundTechnicalInsight>>(emptyMap())
    val technicalInsights: StateFlow<Map<String, FundTechnicalInsight>> = _technicalInsights.asStateFlow()

    fun loadTechnicalInsight(fund: FundRankingItem) {
        val sym = fund.symbol.trim()
        if (_technicalInsights.value.containsKey(sym)) return

        viewModelScope.launch {
            val insight = FundBaseApiService.fetchFundTechnicalInsight(
                symbol = sym,
                fallbackCategory = fund.category,
                fallbackBubblePercent = fund.bubblePercent ?: 0.0,
                fallbackPriceChangePercent = fund.priceChangePercent ?: 0.0
            )
            _technicalInsights.update { it + (sym to insight) }
        }
    }

    /** فهرست پورتفوها و پورتفوی فعال؛ تنظیمات و دسته‌بندی صندوق‌ها بین همه مشترک است. */
    val portfolios: StateFlow<List<com.example.data.local.PortfolioInfo>> by lazy {
        repository.portfoliosFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }
    val selectedPortfolioId: StateFlow<Long> get() = repository.selectedPortfolioId

    fun selectPortfolio(id: Long) = repository.selectPortfolio(id)

    fun createPortfolio(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createPortfolio(name)
            repository.selectPortfolio(id)
            showMessage("پورتفوی «${name.trim()}» ساخته شد")
        }
    }

    fun renamePortfolio(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renamePortfolio(id, name) }
    }

    fun deletePortfolio(id: Long) {
        viewModelScope.launch {
            if (repository.deletePortfolio(id)) showMessage("پورتفو حذف شد")
            else showMessage("آخرین پورتفو قابل حذف نیست")
        }
    }

    private val _riskStats = MutableStateFlow<Map<String, FundRiskStats>>(emptyMap())
    val riskStats: StateFlow<Map<String, FundRiskStats>> = _riskStats.asStateFlow()

    /** نمادهایی که درخواست آمار ریسکشان رفته — چه جواب داده باشد چه نه. */
    private val requestedRiskSymbols = mutableSetOf<String>()

    /**
     * آمار ریسک را **فقط وقتی کاربر کارت را باز می‌کند** می‌گیرد.
     *
     * RPC مربوطه یک صندوق در هر فراخوانی می‌گیرد، پس گرفتنش برای کل فهرست
     * یعنی صدها درخواست. صندوقی که کاربر بازش نکرده، آماری هم لازم ندارد.
     *
     * نمادی که یک‌بار درخواست شده دوباره درخواست نمی‌شود، حتی اگر جواب
     * `null` بوده باشد — وگرنه هر بار باز و بسته کردن کارت، یک درخواست
     * ناموفق دیگر می‌زد.
     */
    fun loadRiskStats(fund: FundRankingItem) {
        val sym = fund.symbol.trim()
        val fundId = fund.fundId.trim()
        if (fundId.isEmpty() || sym in requestedRiskSymbols) return
        requestedRiskSymbols += sym

        viewModelScope.launch {
            val stats = FundBaseApiService.fetchRiskStats(fundId)
            if (stats != null) _riskStats.update { it + (sym to stats) }
        }
    }

    init {
        val db = AppDatabase.getDatabase()
        repository = PcmrRepository(db)
        viewModelScope.launch {
            repository.initializeDefaultsIfEmpty()
            // بررسی روزانه بر اساس تنظیم ذخیره‌شده — نه مقدار پیش‌فرض
            RebalanceAlertScheduler.apply(repository.getCurrentSettings().isRebalanceAlertEnabled)
            refreshFundAnalysis()
            refreshAlanchandQuotes()
            refreshInflationRates()
        }
    }

    /**
     * قیمت طلا، سکه و دلار آزاد از آلان‌چند — برای کارت بالای دیده‌بان.
     *
     * برخلاف [refreshFundAnalysis]، شکست این درخواست را با `showMessage` اعلام
     * نمی‌کند: این کارت یک افزونه اختیاری روی دیده‌بان است، نه بخشی از محاسبات
     * PCMR، و کارت خودش حالت «دریافت نشد» را نشان می‌دهد.
     */
    fun refreshAlanchandQuotes(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoadingAlanchandQuotes.value = true
            try {
                val quotes = AlanchandApiService.fetchQuotes(forceRefresh)
                if (quotes != null) {
                    _alanchandQuotes.value = quotes
                }
            } catch (e: Exception) {
                Log.e("PcmrViewModel", "خطا در دریافت قیمت آلان‌چند: ${e.localizedMessage}")
            } finally {
                _isLoadingAlanchandQuotes.value = false
            }
        }
    }

    private val _calcParams = combine(_calcTransactionType, _calcAmountToman) { type, amount ->
        type to amount
    }

    private val _uiFlags = combine(_userMessage, _isImporting) { msg, importing ->
        msg to importing
    }

    private data class RankingState(
        val list: List<FundRankingItem>,
        val isAnalyzing: Boolean,
        val marketDataAgeMs: Long?,
        val returnsAsOfJalaliDate: String
    )

    private val _rankingState =
        combine(
            _fundRankingList,
            _isAnalyzingFunds,
            _marketDataAgeMs,
            _returnsAsOfJalaliDate
        ) { list, analyzing, ageMs, asOf ->
            RankingState(list, analyzing, ageMs, asOf)
        }

    private val _alanchandState = combine(
        _alanchandQuotes,
        _isLoadingAlanchandQuotes
    ) { quotes, loading -> quotes to loading }

    /**
     * وضعیتی که **ارزان** است و با هر تعامل کوچک UI عوض می‌شود.
     *
     * عمداً از محاسبات سبد جدا نگه داشته شده. قبلاً `currentScreen` داخل همان
     * combineای بود که `calculatePortfolio`، اسکن نمادهای حدسی، تشخیص
     * تکراری‌ها و برنامه برداشت را حساب می‌کرد — یعنی **فقط عوض کردن تب**،
     * همه آن‌ها را دوباره راه می‌انداخت، آن هم روی ترد اصلی. نتیجه‌اش لگِ
     * محسوس در جابه‌جایی بین تب‌ها بود.
     */
    private data class LightUiState(
        val screen: AppScreen,
        val userMessage: String?,
        val isImporting: Boolean,
        val rankingList: List<FundRankingItem>,
        val isAnalyzing: Boolean,
        val marketDataAgeMs: Long?,
        val returnsAsOfJalaliDate: String,
        val alanchandQuotes: AlanchandQuotes?,
        val isLoadingAlanchandQuotes: Boolean
    )

    private val _lightUiState = combine(
        _currentScreen,
        _uiFlags,
        _rankingState,
        _alanchandState
    ) { screen, uiFlags, rankingState, alanchandState ->
        val (msg, importing) = uiFlags
        val (alanchandQuotes, loadingAlanchand) = alanchandState
        LightUiState(
            screen = screen,
            userMessage = msg,
            isImporting = importing,
            rankingList = rankingState.list,
            isAnalyzing = rankingState.isAnalyzing,
            marketDataAgeMs = rankingState.marketDataAgeMs,
            returnsAsOfJalaliDate = rankingState.returnsAsOfJalaliDate,
            alanchandQuotes = alanchandQuotes,
            isLoadingAlanchandQuotes = loadingAlanchand
        )
    }

    /** نتیجه محاسبات سنگین سبد. فقط وقتی عوض می‌شود که ورودی واقعی‌اش عوض شود. */
    private data class PortfolioDerived(
        val settings: SettingsEntity,
        val items: List<PortfolioEntity>,
        val calcResult: PortfolioCalculationResult,
        val guessedSymbols: Set<String>,
        val duplicateSymbols: Set<String>,
        val calcType: TransactionType,
        val calcAmountToman: Double,
        val depositWithdrawResult: DepositWithdrawalResult?,
        val withdrawalPlan: WithdrawalPlanner.Plan?
    )

    /**
     * محاسبات سنگین سبد.
     *
     * ورودی‌هایش فقط چیزهایی‌اند که نتیجه واقعاً به آن‌ها وابسته است: تنظیمات،
     * دارایی‌ها، جدول دسته‌ها، و پارامترهای ماشین‌حساب. تب فعلی، پیام‌ها و
     * فهرست دیده‌بان اینجا نیستند، پس عوض شدنشان این را دوباره حساب نمی‌کند.
     *
     * `flowOn(Default)` محاسبه را از ترد اصلی برمی‌دارد؛ حتی وقتی واقعاً لازم
     * است دوباره حساب شود، UI را نمی‌خواباند.
     */
    private val _portfolioDerived = combine(
        repository.settingsFlow,
        repository.portfolioItemsFlow,
        repository.fundCategoriesFlow,
        _calcParams
    ) { settings, items, fundCategories, calcParams ->
        val (calcType, calcAmountToman) = calcParams
        val safeSettings = settings ?: SettingsEntity()
        val calcResult = PcmrEngine.calculatePortfolio(items, safeSettings)

        // نمادی «حدسی» است اگر کاربر دسته‌اش را تأیید نکرده باشد، در جدول
        // صندوق‌های واردشده هم نباشد، و تطبیق جدول پیش‌فرض هم فقط حدس بوده باشد.
        val knownSymbols = fundCategories.mapTo(HashSet()) { it.symbol.trim() }
        val guessed = items.asSequence()
            .filter { !it.isCategoryManuallySet }
            .map { it.symbol.trim() }
            .filter { it !in knownSymbols }
            .filter { DefaultEtfDatabase.matchCategoryForSymbol(it).isGuess }
            .toSet()

        val duplicates = items
            .groupingBy { it.symbol.trim() }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        // Convert Toman to Rial for calculation (x10)
        val transactionAmountRial = calcAmountToman * 10.0
        val dwResult = if (calcResult.totalPv > 0) {
            PcmrEngine.calculateDepositWithdrawal(calcResult, calcType, transactionAmountRial)
        } else {
            null
        }

        // برداشت، برخلاف واریز، به سفارش فروش نیاز دارد و فروش کف بازار دارد.
        // پس علاوه بر خروجی نظری، یک برنامه واقعاً اجراشدنی هم ساخته می‌شود.
        val plan = if (
            calcType == TransactionType.WITHDRAWAL &&
            calcResult.totalPv > 0.0 &&
            transactionAmountRial > 0.0
        ) {
            WithdrawalPlanner.plan(
                holdings = calcResult.symbolAlerts
                    .filter { !it.isSuggestedNewPosition && it.currentValue > 0.0 }
                    .map { alert ->
                        WithdrawalPlanner.Holding(
                            itemId = alert.itemId,
                            symbol = alert.symbol,
                            category = alert.category,
                            currentValueRial = alert.currentValue,
                            targetWeightPercent = alert.targetWeightPercent,
                            unitPriceRial = alert.lastPrice,
                            isHoldLocked = alert.isHoldLocked
                        )
                    },
                withdrawalRial = transactionAmountRial
            )
        } else {
            null
        }

        PortfolioDerived(
            settings = safeSettings,
            items = items,
            calcResult = calcResult,
            guessedSymbols = guessed,
            duplicateSymbols = duplicates,
            calcType = calcType,
            calcAmountToman = calcAmountToman,
            depositWithdrawResult = dwResult,
            withdrawalPlan = plan
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<PcmrUiState> = combine(
        _portfolioDerived,
        repository.snapshotsFlow,
        repository.holdingSnapshotsFlow,
        _lightUiState
    ) { derived, history, holdings, light ->
        PcmrUiState(
            currentScreen = light.screen,
            settings = derived.settings,
            portfolioItems = derived.items,
            calculationResult = derived.calcResult,
            calcTransactionType = derived.calcType,
            calcAmountToman = derived.calcAmountToman,
            depositWithdrawResult = derived.depositWithdrawResult,
            fundRankingList = light.rankingList,
            isAnalyzingFunds = light.isAnalyzing,
            userMessage = light.userMessage,
            isImporting = light.isImporting,
            guessedCategorySymbols = derived.guessedSymbols,
            marketDataAgeMs = light.marketDataAgeMs,
            returnsAsOfJalaliDate = light.returnsAsOfJalaliDate,
            duplicateSymbols = derived.duplicateSymbols,
            snapshots = history,
            holdingSnapshots = holdings,
            withdrawalPlan = derived.withdrawalPlan,
            alanchandQuotes = light.alanchandQuotes,
            isLoadingAlanchandQuotes = light.isLoadingAlanchandQuotes
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PcmrUiState()
    )

    // ---------------------------------------------------------------------
    // نرخ تورم (بانک مرکزی)
    // ---------------------------------------------------------------------

    /** نرخ تورم نقطه‌به‌نقطه ماهانه، جدیدترین اول (از cbi.ir یا ورود دستی). */
    val inflationRates: StateFlow<List<InflationRateEntity>> = repository.inflationRatesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isLoadingInflation = MutableStateFlow(false)
    val isLoadingInflation: StateFlow<Boolean> = _isLoadingInflation.asStateFlow()

    /**
     * تلاش برای دریافت خودکار از cbi.ir.
     *
     * **این سایت فقط از IP ایران باز می‌شود** — پس این فقط روی گوشی خودِ
     * کاربر جواب می‌دهد، نه لزوماً هر جا. شکستش بی‌صدا نیست ولی هم پیام
     * فاجعه‌بار نشان نمی‌دهد: کاربر همیشه می‌تواند دستی وارد کند.
     */
    fun refreshInflationRates(announce: Boolean = false) {
        viewModelScope.launch {
            _isLoadingInflation.value = true
            try {
                val page = CbiInflationApiService.fetchCurrentYearTable()
                if (page != null) {
                    repository.saveAutoFetchedInflationRates(
                        page.rows.map {
                            InflationRateEntity(
                                jalaliYear = it.jalaliYear,
                                jalaliMonth = it.jalaliMonth,
                                ratePercent = it.ratePercent
                            )
                        }
                    )
                    if (announce) showMessage("نرخ تورم سال ${page.year} از بانک مرکزی به‌روزرسانی شد")
                } else if (announce) {
                    showMessage("دریافت خودکار نرخ تورم از cbi.ir ممکن نشد (این سایت فقط با IP ایران باز می‌شود)؛ می‌توانید دستی وارد کنید.")
                }
            } catch (e: Exception) {
                Log.e("PcmrViewModel", "خطا در دریافت نرخ تورم: ${e.localizedMessage}")
            } finally {
                _isLoadingInflation.value = false
            }
        }
    }

    fun saveManualInflationRate(year: Int, month: Int, ratePercent: Double) {
        viewModelScope.launch {
            repository.saveManualInflationRate(year, month, ratePercent)
        }
    }

    fun deleteInflationRate(year: Int, month: Int) {
        viewModelScope.launch {
            repository.deleteInflationRate(year, month)
        }
    }

    // ---------------------------------------------------------------------
    // لینک سفارش کارگزاری (ایزی‌تریدر)
    // ---------------------------------------------------------------------

    /**
     * نگاشت نماد به ISIN، همان‌طور که کاربر ثبتش کرده.
     *
     * برنامه این کدها را نمی‌سازد و حدس نمی‌زند: هیچ منبع داده‌ای که استفاده
     * می‌کند ISIN نمی‌دهد، و یک حرف اشتباه یعنی فرم سفارشِ اوراق دیگری باز
     * می‌شود.
     */
    val symbolIsinMap: StateFlow<Map<String, String>> = repository.symbolIsinFlow
        .map { rows -> rows.associate { it.symbol to it.isin } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    /**
     * ISIN را از چیزی که کاربر چسبانده بیرون می‌کشد و ذخیره می‌کند.
     *
     * `false` یعنی متن چسبانده‌شده ISIN معتبری نداشت — و آن‌وقت **هیچ چیز
     * ذخیره نمی‌شود**؛ ذخیره‌کردن یک کد ناقص یعنی دفعه بعد بی‌صدا فرم اشتباه
     * باز شود.
     */
    fun saveSymbolIsin(symbol: String, pastedText: String): Boolean {
        val isin = EasyTraderLink.extractIsin(pastedText)
        if (isin == null) {
            showMessage("در متنی که چسباندید کد ISIN معتبری پیدا نشد.")
            return false
        }
        viewModelScope.launch {
            repository.saveSymbolIsin(symbol, isin)
            showMessage("کد «$symbol» ذخیره شد؛ دفعه بعد مستقیم باز می‌شود.")
        }
        return true
    }

    fun forgetSymbolIsin(symbol: String) {
        viewModelScope.launch { repository.deleteSymbolIsin(symbol) }
    }

    /**
     * آدرس فرم سفارش یک نماد، یا `null` اگر ISINش هنوز ثبت نشده باشد.
     */
    fun orderFormUrl(symbol: String, side: EasyTraderLink.Side): String? =
        EasyTraderLink.orderFormUrl(symbolIsinMap.value[symbol.trim()], side)

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        // ورود به دیده‌بان: قیمت آلان‌چند را تازه کن (کش ۵ دقیقه‌ای رعایت می‌شود،
        // پس رفت‌وآمد بین تب‌ها درخواست اضافه نمی‌زند).
        if (screen == AppScreen.RANKING) {
            refreshAlanchandQuotes()
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(text: String) {
        _userMessage.value = text
    }

    /**
     * تنظیمات ذخیره‌شده را می‌خواند، تغییر می‌دهد و برمی‌گرداند به دیتابیس.
     *
     * خواندن عمداً از `repository` است نه `uiState.value`: تا وقتی صفحه‌ای مشترک
     * `uiState` نشده باشد، مقدارش `SettingsEntity()` پیش‌فرض است. با خواندن از
     * `uiState`، یک `copy` روی همان پیش‌فرض می‌نشست و بقیه تنظیمات کاربر
     * (EML، آستانه، وزن‌های دستی) بی‌صدا به مقدار اولیه برمی‌گشت.
     */
    private fun editSettings(
        successMessage: ((SettingsEntity) -> String)? = null,
        transform: (SettingsEntity) -> SettingsEntity
    ) {
        viewModelScope.launch {
            val updated = transform(repository.getCurrentSettings())
            repository.updateSettings(updated)
            successMessage?.let { showMessage(it(updated)) }
        }
    }

    fun updateRiskTolerance(rt: Double) = editSettings { it.copy(riskTolerance = rt) }

    fun updateTimeHorizon(horizon: TimeHorizon) =
        editSettings { it.copy(timeHorizonMonths = horizon.months) }

    fun updateEml(
        horizon: TimeHorizon,
        emlStock: Double,
        emlGold: Double
    ) = editSettings({ "پارامترهای EML ذخیره شدند." }) { current ->
        when (horizon) {
            TimeHorizon.THREE_MONTHS -> current.copy(emlStock3M = emlStock, emlGold3M = emlGold)
            TimeHorizon.SIX_MONTHS -> current.copy(emlStock6M = emlStock, emlGold6M = emlGold)
            TimeHorizon.TWELVE_MONTHS -> current.copy(emlStock12M = emlStock, emlGold12M = emlGold)
        }
    }

    fun updateRebalanceThreshold(threshold: Double) =
        editSettings({ "آستانه ریبلنس روی $threshold% تنظیم شد." }) {
            it.copy(rebalanceThresholdPercent = threshold)
        }

    fun setManualWeights(
        enabled: Boolean,
        gold: Double,
        stock: Double,
        mixed: Double,
        fixed: Double
    ) = editSettings({ "چینش دستی وزن‌ها ذخیره شد." }) {
        it.copy(
            isManualWeightsEnabled = enabled,
            manualGoldWeight = gold,
            manualStockWeight = stock,
            manualMixedWeight = mixed,
            manualFixedWeight = fixed
        )
    }

    /**
     * وزن‌های هدف فعلی را به مضارب [step] گرد می‌کند و به‌عنوان چینش دستی ذخیره
     * می‌کند. موتور تضمین می‌کند RT حاصل از گرد کردن، از ریسک‌پذیری کاربر بالاتر
     * نرود — یعنی گرد کردن همیشه به سمت محافظه‌کارانه‌تر است.
     *
     * این قابلیت در موتور بود و تست هم داشت، ولی به هیچ دکمه‌ای وصل نبود.
     */
    fun roundTargetWeightsToMultiples(step: Double = 5.0) {
        viewModelScope.launch {
            val settings = repository.getCurrentSettings()
            val horizon = TimeHorizon.fromMonths(settings.timeHorizonMonths)
            val rounded = PcmrEngine.roundWeightsToMultiples(
                raw = PcmrEngine.calculateTargetWeights(settings),
                step = step,
                investorRt = settings.riskTolerance,
                emlG = settings.currentEmlGold(horizon),
                emlS = settings.currentEmlStock(horizon),
                emlM = settings.currentEmlMixed(horizon)
            )

            repository.updateSettings(
                settings.copy(
                    isManualWeightsEnabled = true,
                    manualGoldWeight = rounded.goldWeight,
                    manualStockWeight = rounded.stockWeight,
                    manualMixedWeight = rounded.mixedWeight,
                    manualFixedWeight = rounded.fixedIncomeWeight
                )
            )
            showMessage(
                "وزن‌ها به مضارب ${step.toInt()}٪ گرد شدند — " +
                        "ریسک محاسبه‌شده: ${rounded.calculatedRt}٪"
            )
        }
    }

    fun setCalculatorTransactionType(type: TransactionType) {
        _calcTransactionType.value = type
    }

    fun setCalculatorAmountToman(toman: Double) {
        _calcAmountToman.value = toman
    }

    fun importPortfolioFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = repository.importPortfolioFromUri(context, uri)
            _isImporting.value = false
            result.onSuccess { count ->
                showMessage("تعداد $count نماد با موفقیت بارگذاری و استخراج شد.")
                refreshFundAnalysis()
                _currentScreen.value = AppScreen.DASHBOARD
            }.onFailure { err ->
                showMessage("خطا در خواندن فایل: ${err.localizedMessage}")
            }
        }
    }

    fun importCategoriesFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = repository.importCategoriesFromUri(context, uri)
            _isImporting.value = false
            result.onSuccess { summary ->
                // ردیف‌های ناخوانا صریحاً گزارش می‌شوند، نه اینکه بی‌صدا «سهامی» شوند
                val skipped = summary.skippedSymbols
                showMessage(
                    buildString {
                        append("تعداد ${summary.appliedCount} دسته‌بندی صندوق اعمال شد")
                        if (skipped.isNotEmpty()) {
                            append(" — ${skipped.size} ردیف به‌خاطر نوع نامشخص رد شد")
                            append(" (${skipped.take(3).joinToString("، ")}")
                            if (skipped.size > 3) append(" و ...")
                            append(")")
                        }
                        append(".")
                    }
                )
                refreshFundAnalysis()
            }.onFailure { err ->
                showMessage("خطا در خواندن فایل دسته‌بندی: ${err.localizedMessage}")
            }
        }
    }

    // ------------------------------------------------------------------
    // خروجی گرفتن از سفارش‌ها
    // ------------------------------------------------------------------

    private fun todayStamp(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun signalsFileName(): String = OrderExport.fileName("pcmr-signals", todayStamp())

    fun withdrawalPlanFileName(): String = OrderExport.fileName("pcmr-withdrawal", todayStamp())

    /** متن سیگنال‌ها برای اشتراک‌گذاری در پیام‌رسان. */
    fun signalsShareText(): String = OrderExport.signalsToText(
        alerts = uiState.value.calculationResult.symbolAlerts,
        title = "سفارش‌های ریبلنس — ${todayStamp()}",
        lockedHold = uiState.value.calculationResult.lockedHold
    )

    fun withdrawalShareText(): String {
        val plan = uiState.value.withdrawalPlan ?: return ""
        return OrderExport.withdrawalPlanToText(plan, "برنامه برداشت — ${todayStamp()}")
    }

    fun exportSignalsCsv(context: Context, uri: Uri) {
        val csv = OrderExport.signalsToCsv(uiState.value.calculationResult.symbolAlerts)
        writeText(context, uri, csv, "سفارش‌های ریبلنس")
    }

    fun exportWithdrawalPlanCsv(context: Context, uri: Uri) {
        val plan = uiState.value.withdrawalPlan
        if (plan == null) {
            showMessage("برنامه برداشتی برای خروجی گرفتن وجود ندارد.")
            return
        }
        writeText(context, uri, OrderExport.withdrawalPlanToCsv(plan), "برنامه برداشت")
    }

    private fun writeText(context: Context, uri: Uri, text: String, label: String) {
        viewModelScope.launch {
            val result = repository.writeTextToUri(context, uri, text)
            result.onSuccess {
                showMessage("$label در فایل ذخیره شد.")
            }.onFailure { err ->
                showMessage("خطا در ذخیره فایل: ${err.localizedMessage}")
            }
        }
    }

    // ------------------------------------------------------------------
    // پشتیبان‌گیری و بازیابی
    // ------------------------------------------------------------------

    /** نام پیشنهادی فایل پشتیبان — تاریخ در نامش تا نسخه‌ها قاطی نشوند. */
    fun suggestedBackupFileName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
        return "pcmr-backup-$stamp.json"
    }

    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val result = repository.writeBackupToUri(context, uri, stamp)
            _isImporting.value = false
            result.onSuccess {
                showMessage("پشتیبان با موفقیت ذخیره شد. این فایل را جای امنی نگه دارید.")
            }.onFailure { err ->
                showMessage("خطا در ذخیره پشتیبان: ${err.localizedMessage}")
            }
        }
    }

    fun restoreBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = repository.restoreBackupFromUri(context, uri)
            _isImporting.value = false
            result.onSuccess { summary ->
                showMessage(
                    buildString {
                        append("بازیابی انجام شد: ${summary.itemCount} نماد")
                        if (summary.categoryCount > 0) {
                            append("، ${summary.categoryCount} دسته‌بندی")
                        }
                        if (summary.settingsRestored) append("، به‌همراه تنظیمات")
                        append(".")
                    }
                )
                refreshFundAnalysis()
                _currentScreen.value = AppScreen.DASHBOARD
            }.onFailure { err ->
                showMessage("خطا در بازیابی: ${err.localizedMessage}")
            }
        }
    }

    fun loadSampleData() {
        viewModelScope.launch {
            repository.loadSamplePortfolio()
            showMessage("پورتفوی نمونه استاندارد PCMR با موفقیت لود شد.")
            refreshFundAnalysis()
            _currentScreen.value = AppScreen.DASHBOARD
        }
    }

    fun addOrUpdateSymbol(
        id: Long,
        symbol: String,
        category: AssetCategory,
        currentValueRial: Double,
        quantity: Long,
        lastPriceRial: Double
    ) {
        viewModelScope.launch {
            // فیلدهایی که دیالوگ ویرایش نمی‌پرسد — قیمت سر به سر و درصد هدف اختصاصی —
            // باید از رکورد قبلی حفظ شوند. قاعده‌اش در [PortfolioEdit] است تا تست شود.
            val existing = if (id != 0L) repository.getPortfolioItem(id) else null

            val entity = PortfolioEdit.applyManualEdit(
                existing = existing,
                id = id,
                symbol = symbol,
                category = category,
                currentValueRial = currentValueRial,
                quantity = quantity,
                lastPriceRial = lastPriceRial
            )
            if (id == 0L) {
                repository.insertPortfolioItem(entity)
                showMessage("نماد $symbol به پورتفو اضافه شد.")
            } else {
                repository.updatePortfolioItem(entity)
                showMessage("نماد $symbol ویرایش شد.")
            }
            refreshFundAnalysis()
        }
    }

    // ------------------------------------------------------------------
    // اعمال سفارش‌های اجراشده
    // ------------------------------------------------------------------

    /**
     * سفارش‌های قابل اعمال بر اساس سیگنال‌های فعلی.
     * محاسبه‌اش خالص است؛ قاعده در [OrderExecution].
     */
    fun executableOrders(): List<OrderExecution.ExecutableOrder> {
        val state = uiState.value
        return OrderExecution.planFrom(
            alerts = state.calculationResult.symbolAlerts,
            portfolio = state.portfolioItems
        )
    }

    /**
     * سفارش‌هایی که کاربر تأیید کرده را روی سبد اعمال می‌کند.
     *
     * فهرست دوباره از داده فعلی دیتابیس ساخته می‌شود، نه از چیزی که هنگام باز
     * شدن دیالوگ در حافظه بود — بین باز شدن دیالوگ و تأیید ممکن است قیمت زنده
     * عوض شده یا ردیفی حذف شده باشد.
     */
    fun applyExecutedOrders(selectedItemIds: Set<Long>) {
        if (selectedItemIds.isEmpty()) {
            showMessage("هیچ سفارشی انتخاب نشده بود.")
            return
        }

        viewModelScope.launch {
            val portfolio = repository.getCurrentPortfolioItems()
            val settings = repository.getCurrentSettings()
            val fresh = PcmrEngine.calculatePortfolio(portfolio, settings)

            val orders = OrderExecution
                .planFrom(fresh.symbolAlerts, portfolio)
                .filter { it.itemId in selectedItemIds }

            if (orders.isEmpty()) {
                showMessage("سفارش انتخاب‌شده دیگر معتبر نیست؛ سیگنال‌ها را دوباره بررسی کنید.")
                return@launch
            }

            val changed = OrderExecution.apply(portfolio, orders)
            val written = repository.applyPortfolioChanges(changed)

            val capped = orders.count { it.isCappedBySholding }
            showMessage(
                buildString {
                    append("$written نماد به‌روزرسانی شد")
                    if (capped > 0) {
                        append(" ($capped فروش به اندازه موجودی محدود شد)")
                    }
                    append(". اگر اجرای واقعی فرق داشت، از صفحه بارگذاری اصلاحش کنید.")
                }
            )
            refreshFundAnalysis()
        }
    }

    /**
     * «قفل نگه‌داری» یک نماد را روشن یا خاموش می‌کند.
     *
     * تا وقتی قفل روشن است موتور برای این نماد پیشنهاد فروش نمی‌دهد و به‌جایش
     * مبلغ نقدی را حساب می‌کند که با آن، وزن نماد بدون فروش به هدف می‌رسد.
     */
    fun setHoldLock(id: Long, locked: Boolean) {
        viewModelScope.launch {
            val item = repository.getPortfolioItem(id)
            if (item == null) {
                showMessage("این نماد دیگر در سبد نیست.")
                return@launch
            }
            if (item.isHoldLocked == locked) return@launch

            repository.updatePortfolioItem(item.copy(isHoldLocked = locked))
            showMessage(
                if (locked) {
                    "${item.symbol} قفل شد؛ دیگر پیشنهاد فروشش داده نمی‌شود."
                } else {
                    "قفل ${item.symbol} برداشته شد."
                }
            )
        }
    }

    fun deleteSymbol(id: Long) {
        viewModelScope.launch {
            repository.deletePortfolioItem(id)
            showMessage("نماد حذف شد.")
            refreshFundAnalysis()
        }
    }

    fun clearAllPortfolio() {
        viewModelScope.launch {
            repository.clearPortfolio()
            showMessage("پورتفوی پاک شد.")
            refreshFundAnalysis()
        }
    }

    /**
     * @param announce وقتی true باشد، نتیجه همگام‌سازی به کاربر گزارش می‌شود.
     *        رفرش‌های خودکار پس‌زمینه بی‌صدا می‌مانند تا اسنک‌بار شلوغ نشود،
     *        ولی هر تغییری که روی داده کاربر اثر بگذارد گزارش می‌شود.
     * @param forceRefresh کش ۱۵ ثانیه‌ای بازار را دور می‌زند. رفرشِ دستیِ کاربر
     *        باید واقعاً درخواست بزند؛ وگرنه دکمه بی‌اثر به نظر می‌رسد.
     *        محافظ backoff پس از HTTP 429 همچنان محترم شمرده می‌شود.
     */
    fun refreshFundAnalysis(announce: Boolean = false, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isAnalyzingFunds.value = true
            try {
                repository.autoCorrectMisclassifiedSymbols()

                val currentItems = repository.getCurrentPortfolioItems()
                _fundRankingList.value =
                    TsetmcFundService.analyzeAndRankFunds(currentItems, forceRefresh)

                // تنظیمات از دیتابیس خوانده می‌شود نه از uiState: این متد از داخل
                // `init` هم صدا زده می‌شود، یعنی قبل از اینکه UI مشترک uiState شود
                // و مقدارش هنوز پیش‌فرض است. نتیجه‌اش این بود که همگام‌سازی زنده
                // در استارت اپ اجرا می‌شد حتی وقتی کاربر خاموشش کرده بود.
                _marketDataAgeMs.value = FundBaseApiService.marketDataAgeMs
                _returnsAsOfJalaliDate.value = FundBaseApiService.returnsAsOfJalaliDate

                val syncEnabled = repository.getCurrentSettings().isLivePriceSyncEnabled
                val result = repository.syncLivePrices(revalueFromQuantity = syncEnabled)

                // یک نقطه در نمودار رشد برای امروز
                val settingsNow = repository.getCurrentSettings()
                val freshItems = repository.getCurrentPortfolioItems()
                repository.recordSnapshot(
                    PcmrEngine.calculatePortfolio(freshItems, settingsNow).trIndex
                )

                if (result.revaluedCount > 0) {
                    // بازمحاسبه ارزش، داده سبد کاربر را تغییر می‌دهد — همیشه اعلام شود
                    showMessage(
                        "ارزش ${result.revaluedCount} نماد با قیمت زنده بازمحاسبه شد" +
                                if (result.keptManual > 0) " (${result.keptManual} نماد با ارزش دستی دست‌نخورده ماند)" else ""
                    )
                } else if (announce) {
                    // گزارش باید واقعیت را بگوید: اگر دریافت شکست خورده و آنچه
                    // نمایش داده می‌شود از کش است، کاربر باید بداند — نه اینکه
                    // «داده جدیدی نبود» بشنود و فکر کند بازار آرام بوده.
                    val ageMs = FundBaseApiService.marketDataAgeMs
                    showMessage(
                        when {
                            result.pricedCount > 0 -> "قیمت ${result.pricedCount} نماد به‌روزرسانی شد"
                            !syncEnabled -> "همگام‌سازی قیمت زنده خاموش است"
                            ageMs == null -> "داده بازار در دسترس نیست؛ اتصال اینترنت را بررسی کنید"
                            !MarketDataFreshness.isFresh(ageMs) ->
                                "دریافت داده تازه ممکن نشد؛ آخرین داده مربوط به " +
                                        MarketDataFreshness.describeAge(ageMs) + " است"
                            else -> "قیمت‌ها بدون تغییر بودند"
                        }
                    )
                }
            } catch (e: Exception) {
                showMessage("خطا در بررسی داده‌های بازار: ${e.localizedMessage}")
            } finally {
                _isAnalyzingFunds.value = false
            }
        }
    }

    /**
     * هشدار ریبلنس در پس‌زمینه را روشن یا خاموش می‌کند و زمان‌بندی را هم‌راستا
     * می‌کند. وقتی خاموش می‌شود، وضعیت «عبور» هم صفر می‌شود تا روشن کردن دوباره
     * یک شروع تازه باشد، نه ادامه‌ی یک هشدار قدیمی.
     */
    fun setRebalanceAlert(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getCurrentSettings()
            repository.updateSettings(
                current.copy(
                    isRebalanceAlertEnabled = enabled,
                    wasAboveThreshold = if (enabled) current.wasAboveThreshold else false,
                    lastAlertEpochMs = if (enabled) current.lastAlertEpochMs else 0L
                )
            )
            RebalanceAlertScheduler.apply(enabled)
            showMessage(
                if (enabled) {
                    "هشدار ریبلنس روشن شد؛ روزانه در پس‌زمینه بررسی می‌شود."
                } else {
                    "هشدار ریبلنس خاموش شد."
                }
            )
        }
    }

    fun setLivePriceSync(enabled: Boolean) = editSettings({
        if (enabled) {
            "همگام‌سازی قیمت زنده روشن شد؛ ارزش نمادهای دارای تعداد واحد بازمحاسبه می‌شود."
        } else {
            "همگام‌سازی خاموش شد؛ فقط قیمت روز به‌روز می‌شود و ارزش سبد دست‌نخورده می‌ماند."
        }
    }) {
        it.copy(isLivePriceSyncEnabled = enabled)
    }
}
