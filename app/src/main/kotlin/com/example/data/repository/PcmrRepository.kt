package com.example.data.repository

import com.example.platform.Context
import com.example.platform.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.PortfolioInfo
import com.example.data.local.PortfolioScope
import com.example.data.model.AssetCategory
import com.example.data.model.FundCategoryEntity
import com.example.data.model.HoldingSnapshotEntity
import com.example.data.model.InflationRateEntity
import com.example.data.model.PortfolioEntity
import com.example.data.model.PortfolioSnapshotEntity
import com.example.data.model.SymbolIsinEntity
import com.example.data.model.SettingsEntity
import com.example.data.parser.DefaultEtfDatabase
import com.example.data.parser.ExcelAndCsvParser
import com.example.data.remote.FundBaseApiService
import com.example.domain.backup.PortfolioBackup
import com.example.domain.portfolio.PortfolioHistory
import com.example.domain.sync.PriceSyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PcmrRepository(
    private val database: AppDatabase,
    private val scope: PortfolioScope = database.scope
) {

    private val portfolioDao = database.portfolioDao(scope)
    private val settingsDao = database.settingsDao()
    private val fundCategoryDao = database.fundCategoryDao()
    private val snapshotDao = database.portfolioSnapshotDao(scope)
    private val symbolIsinDao = database.symbolIsinDao()
    private val inflationRateDao = database.inflationRateDao()

    private val directoryDao = database.portfolioDirectoryDao()

    /** فهرست پورتفوها و شناسه‌ی پورتفوی فعال. */
    val portfoliosFlow: Flow<List<PortfolioInfo>> = directoryDao.observeAll()
    val selectedPortfolioId: kotlinx.coroutines.flow.StateFlow<Long> = scope.id

    fun selectPortfolio(id: Long) {
        scope.id.value = id
        runCatching { directoryDao.saveSelection(id) }
    }

    suspend fun createPortfolio(name: String): Long = withContext(Dispatchers.IO) { directoryDao.create(name) }
    suspend fun renamePortfolio(id: Long, name: String) = withContext(Dispatchers.IO) { directoryDao.rename(id, name) }

    /** حذف یک پورتفو. آخرین پورتفو حذف نمی‌شود؛ اگر فعال بود، به اولین باقی‌مانده می‌رویم. */
    suspend fun deletePortfolio(id: Long): Boolean = withContext(Dispatchers.IO) {
        val all = directoryDao.getAll()
        if (all.size <= 1) return@withContext false
        directoryDao.delete(id)
        if (scope.current == id) selectPortfolio(all.first { it.id != id }.id)
        true
    }

    suspend fun allPortfolios(): List<PortfolioInfo> = withContext(Dispatchers.IO) { directoryDao.getAll() }

    val portfolioItemsFlow: Flow<List<PortfolioEntity>> = portfolioDao.getAllPortfolioItems()
    val settingsFlow: Flow<SettingsEntity?> = settingsDao.getSettingsFlow()
    val fundCategoriesFlow: Flow<List<FundCategoryEntity>> = fundCategoryDao.getAllFundCategoriesFlow()
    val snapshotsFlow: Flow<List<PortfolioSnapshotEntity>> = snapshotDao.getAllSnapshots()
    val holdingSnapshotsFlow: Flow<List<HoldingSnapshotEntity>> = snapshotDao.getAllHoldings()

    /** نگاشت نماد به ISIN، برای ساختن لینک فرم سفارش کارگزاری. */
    val symbolIsinFlow: Flow<List<SymbolIsinEntity>> = symbolIsinDao.observeAll()

    suspend fun saveSymbolIsin(symbol: String, isin: String) {
        symbolIsinDao.upsert(
            SymbolIsinEntity(
                symbol = symbol.trim(),
                isin = isin.trim().uppercase(),
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteSymbolIsin(symbol: String) = symbolIsinDao.delete(symbol.trim())

    /** نرخ تورم نقطه‌به‌نقطه ماهانه، جدیدترین اول. */
    val inflationRatesFlow: Flow<List<InflationRateEntity>> = inflationRateDao.observeAll()

    /**
     * نتیجه دریافت خودکار از cbi.ir را ذخیره می‌کند — ولی هیچ ردیفی را که
     * کاربر خودش دستی اصلاح کرده رونویسی نمی‌کند (`isManuallyEdited`).
     */
    suspend fun saveAutoFetchedInflationRates(rows: List<InflationRateEntity>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        rows.forEach { row ->
            val existing = inflationRateDao.get(row.jalaliYear, row.jalaliMonth)
            if (existing == null || !existing.isManuallyEdited) {
                inflationRateDao.upsert(row.copy(isManuallyEdited = false, updatedAtEpochMs = now))
            }
        }
    }

    suspend fun saveManualInflationRate(year: Int, month: Int, ratePercent: Double) {
        inflationRateDao.upsert(
            InflationRateEntity(
                jalaliYear = year,
                jalaliMonth = month,
                ratePercent = ratePercent,
                isManuallyEdited = true,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteInflationRate(year: Int, month: Int) = inflationRateDao.delete(year, month)

    suspend fun initializeDefaultsIfEmpty() = withContext(Dispatchers.IO) {
        val existingSettings = settingsDao.getSettingsDirect()
        if (existingSettings == null) {
            settingsDao.insertOrUpdate(SettingsEntity())
        }

        val existingCategories = fundCategoryDao.getAllCategoriesDirect()
        if (existingCategories.isEmpty()) {
            fundCategoryDao.insertAll(DefaultEtfDatabase.toEntities())
        }

        // سبد نمونه دیگر خودکار لود نمی‌شود. کاربر تازه‌وارد به‌جای سبد خالی،
        // هفت نماد ساختگی می‌دید و نمی‌دانست کدام عدد واقعی است — در یک اپ مالی
        // این خطرناک است. داشبورد حالت خالی را با دکمه «لود نمونه» نشان می‌دهد.
        val existingItems = portfolioDao.getAllPortfolioItemsDirect()
        if (existingItems.isNotEmpty()) {
            autoCorrectMisclassifiedSymbols()
        }
    }

    /**
     * دسته‌بندی نمادهایی که هنوز روی مقدار پیش‌فرض (سهامی) مانده‌اند را با
     * دیتابیس صندوق‌ها هماهنگ می‌کند.
     *
     * ردیفی که کاربر دسته‌اش را دستی انتخاب کرده دست نمی‌خورد — نسخه قبلی
     * انتخاب کاربر را در هر رفرش با حدس fuzzy بازنویسی می‌کرد.
     */
    suspend fun autoCorrectMisclassifiedSymbols(): Int = withContext(Dispatchers.IO) {
        val items = portfolioDao.getAllPortfolioItemsDirect()
        val corrections = items.mapNotNull { item ->
            if (item.isCategoryManuallySet) return@mapNotNull null
            if (item.assetCategory != AssetCategory.STOCK) return@mapNotNull null

            val correctCategory = DefaultEtfDatabase.getCategoryForSymbol(item.symbol)
            if (correctCategory != item.assetCategory) {
                item.copy(assetCategory = correctCategory)
            } else {
                null
            }
        }

        if (corrections.isNotEmpty()) {
            portfolioDao.updateAll(corrections)
        }
        corrections.size
    }

    /**
     * قیمت‌های زنده را روی سبد اعمال می‌کند و خلاصه‌ی تغییرات را برمی‌گرداند.
     * همه تغییرات در یک تراکنش نوشته می‌شوند (قبلاً به‌ازای هر نماد یک نوشتن جدا بود).
     */
    suspend fun syncLivePrices(revalueFromQuantity: Boolean): PriceSyncResult =
        withContext(Dispatchers.IO) {
            val current = portfolioDao.getAllPortfolioItemsDirect()
            val result = FundBaseApiService.syncPortfolioPrices(current, revalueFromQuantity)
            if (result.items.isNotEmpty()) {
                portfolioDao.updateAll(result.items)
            }
            result
        }

    /**
     * تنظیمات ذخیره‌شده را مستقیم از دیتابیس می‌خواند.
     *
     * `uiState` با `WhileSubscribed` ساخته شده، پس تا وقتی UI مشترک نشده مقدارش
     * `SettingsEntity()` پیش‌فرض است نه تنظیمات کاربر. هر جا قرار است روی تنظیمات
     * موجود `copy` بزنیم یا تصمیمی بگیریم، باید از همین‌جا بخوانیم — وگرنه
     * انتخاب‌های کاربر با مقدار پیش‌فرض بازنویسی می‌شوند.
     */
    suspend fun getCurrentSettings(): SettingsEntity = withContext(Dispatchers.IO) {
        settingsDao.getSettingsDirect() ?: SettingsEntity()
    }

    suspend fun getPortfolioItem(id: Long): PortfolioEntity? = withContext(Dispatchers.IO) {
        portfolioDao.getItemById(id)
    }

    suspend fun updateSettings(settings: SettingsEntity) = withContext(Dispatchers.IO) {
        settingsDao.insertOrUpdate(settings)
    }

    suspend fun insertPortfolioItem(item: PortfolioEntity) = withContext(Dispatchers.IO) {
        portfolioDao.insertItem(item)
    }

    suspend fun updatePortfolioItem(item: PortfolioEntity) = withContext(Dispatchers.IO) {
        portfolioDao.updateItem(item)
    }

    suspend fun deletePortfolioItem(id: Long) = withContext(Dispatchers.IO) {
        portfolioDao.deleteItemById(id)
    }

    suspend fun clearPortfolio() = withContext(Dispatchers.IO) {
        portfolioDao.clearAll()
    }

    suspend fun getCurrentPortfolioItems(): List<PortfolioEntity> = withContext(Dispatchers.IO) {
        portfolioDao.getAllPortfolioItemsDirect()
    }

    suspend fun loadSamplePortfolio() = withContext(Dispatchers.IO) {
        val sampleItems = listOf(
            PortfolioEntity(
                symbol = "اهرم",
                assetCategory = AssetCategory.STOCK,
                currentValue = 350_000_000.0, // 35M Toman
                quantity = 140_000L,
                lastPrice = 2500.0
            ),
            PortfolioEntity(
                symbol = "آگاس",
                assetCategory = AssetCategory.STOCK,
                currentValue = 180_000_000.0, // 18M Toman
                quantity = 30_000L,
                lastPrice = 6000.0
            ),
            PortfolioEntity(
                symbol = "طلا",
                assetCategory = AssetCategory.GOLD,
                currentValue = 280_000_000.0, // 28M Toman
                quantity = 14_000L,
                lastPrice = 20_000.0
            ),
            PortfolioEntity(
                symbol = "عیار",
                assetCategory = AssetCategory.GOLD,
                currentValue = 120_000_000.0, // 12M Toman
                quantity = 8_000L,
                lastPrice = 15_000.0
            ),
            PortfolioEntity(
                symbol = "زیتون",
                assetCategory = AssetCategory.MIXED,
                currentValue = 150_000_000.0, // 15M Toman
                quantity = 50_000L,
                lastPrice = 3000.0
            ),
            PortfolioEntity(
                symbol = "کمند",
                assetCategory = AssetCategory.FIXED_INCOME,
                currentValue = 220_000_000.0, // 22M Toman
                quantity = 22_000L,
                lastPrice = 10_000.0
            ),
            PortfolioEntity(
                symbol = "افران",
                assetCategory = AssetCategory.FIXED_INCOME,
                currentValue = 100_000_000.0, // 10M Toman
                quantity = 100_000L,
                lastPrice = 1000.0
            )
        )
        portfolioDao.clearAll()
        portfolioDao.insertAll(sampleItems)
    }

    suspend fun importPortfolioFromUri(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val rows = ExcelAndCsvParser.parseRowsFromUri(context, uri)
            if (rows.isEmpty()) {
                return@withContext Result.failure(Exception("فایل خالی است یا فرمت آن پشتیبانی نمی‌شود"))
            }

            val currentCategories = fundCategoryDao.getAllCategoriesDirect().associate { it.symbol to it.category }
            val items = ExcelAndCsvParser.extractPortfolioItems(rows, currentCategories)

            if (items.isEmpty()) {
                return@withContext Result.failure(Exception("هیچ نماد معتبری در فایل یافت نشد. لطفاً ساختار ستون‌های نماد و ارزش روز را بررسی کنید."))
            }

            portfolioDao.clearAll()
            portfolioDao.insertAll(items)
            Result.success(items.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** خلاصه‌ی آنچه از فایل دسته‌بندی واقعاً اعمال شد. */
    data class CategoryImportSummary(
        val appliedCount: Int,
        /** ردیف‌هایی که نوع صندوقشان خوانا نبود و عمداً رد شدند. */
        val skippedSymbols: List<String>
    )

    /**
     * وضعیت امروز سبد را ثبت می‌کند: جمع کل و سود برای نمودار روند، و ترکیب
     * نمادها برای نمودار ترکیب و فهرست تغییرات.
     *
     * کلید، **روز** است: چند بار بارگذاری در یک روز فقط آخرین وضعیت را نگه
     * می‌دارد، وگرنه نمودار پر از نقاط تکراری می‌شد. هر دو جدول در یک تراکنش
     * نوشته می‌شوند تا جمع کل و ترکیب یک روز هیچ‌وقت از دو لحظه متفاوت نباشند.
     */
    suspend fun recordSnapshot(trIndex: Double) = withContext(Dispatchers.IO) {
        val items = portfolioDao.getAllPortfolioItemsDirect()
        val now = System.currentTimeMillis()
        val capture = PortfolioHistory.capture(
            items = items,
            day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)),
            epochMs = now,
            trIndex = trIndex
        ) ?: return@withContext

        snapshotDao.recordCapture(capture.snapshot, capture.holdings)
    }

    /**
     * ردیف‌های تغییرکرده را در یک تراکنش می‌نویسد و تعدادشان را برمی‌گرداند.
     * برای اعمال سفارش‌هایی که کاربر واقعاً در کارگزاری اجرا کرده.
     */
    suspend fun applyPortfolioChanges(changed: List<PortfolioEntity>): Int =
        withContext(Dispatchers.IO) {
            if (changed.isEmpty()) return@withContext 0
            portfolioDao.updateAll(changed)
            changed.size
        }

    // ------------------------------------------------------------------
    // پشتیبان‌گیری و بازیابی
    // ------------------------------------------------------------------

    /** خلاصه‌ی آنچه از فایل پشتیبان بازیابی شد. */
    data class RestoreSummary(
        val itemCount: Int,
        val categoryCount: Int,
        val settingsRestored: Boolean
    )

    /** کل داده اپ را به‌صورت متن JSON برمی‌گرداند. */
    suspend fun exportBackupJson(createdAt: String): String = withContext(Dispatchers.IO) {
        PortfolioBackup.encode(
            settings = settingsDao.getSettingsDirect(),
            portfolio = portfolioDao.getAllPortfolioItemsDirect(),
            fundCategories = fundCategoryDao.getAllCategoriesDirect(),
            createdAt = createdAt
        )
    }

    /** نوشتن یک متن (مثلاً CSV سفارش‌ها) در فایلی که کاربر انتخاب کرده. */
    suspend fun writeTextToUri(context: Context, uri: Uri, text: String): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                } ?: return@withContext Result.failure(
                    Exception("نوشتن در فایل انتخابی ممکن نشد.")
                )
                Result.success(text.length)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun writeBackupToUri(context: Context, uri: Uri, createdAt: String): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val text = exportBackupJson(createdAt)
                val written = context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                    text.length
                } ?: return@withContext Result.failure(Exception("نوشتن در فایل انتخابی ممکن نشد."))
                Result.success(written)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * بازیابی، سبد و دسته‌بندی‌های فعلی را **جایگزین** می‌کند.
     *
     * جایگزینی عمدی است: یک بازیابی نیمه‌کاره که با داده فعلی قاطی شود،
     * ردیف‌های تکراری و ارقام بی‌معنا می‌سازد. تنظیمات فقط اگر در فایل باشد
     * بازنویسی می‌شود.
     */
    suspend fun restoreBackupFromUri(context: Context, uri: Uri): Result<RestoreSummary> =
        withContext(Dispatchers.IO) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: return@withContext Result.failure(Exception("خواندن فایل انتخابی ممکن نشد."))

                val decoded = PortfolioBackup.decode(text).getOrElse { error ->
                    return@withContext Result.failure(error)
                }

                decoded.settings?.let { settingsDao.insertOrUpdate(it) }

                if (decoded.portfolio.isNotEmpty()) {
                    portfolioDao.clearAll()
                    portfolioDao.insertAll(decoded.portfolio)
                }

                if (decoded.fundCategories.isNotEmpty()) {
                    fundCategoryDao.clearAll()
                    fundCategoryDao.insertAll(decoded.fundCategories)
                }

                Result.success(
                    RestoreSummary(
                        itemCount = decoded.portfolio.size,
                        categoryCount = decoded.fundCategories.size,
                        settingsRestored = decoded.settings != null
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun importCategoriesFromUri(
        context: Context,
        uri: Uri
    ): Result<CategoryImportSummary> = withContext(Dispatchers.IO) {
        try {
            val rows = ExcelAndCsvParser.parseRowsFromUri(context, uri)
            if (rows.isEmpty()) {
                return@withContext Result.failure(Exception("فایل دسته‌بندی خالی است"))
            }

            val parsed = ExcelAndCsvParser.extractFundCategories(rows)
            if (parsed.categories.isEmpty()) {
                val reason = if (parsed.unrecognizedSymbols.isEmpty()) {
                    "ستون‌های نماد و نوع صندوق شناسایی نشدند."
                } else {
                    "هیچ ردیفی نوع صندوق خوانا نداشت. مقادیر مجاز: طلا، سهامی، مختلط، درآمد ثابت."
                }
                return@withContext Result.failure(Exception(reason))
            }

            fundCategoryDao.insertAll(parsed.categories)

            // Also update any existing portfolio items with new category definitions.
            // فقط ردیف‌هایی که واقعاً تغییر می‌کنند نوشته می‌شوند، و دسته‌ای که کاربر
            // خودش دستی تأیید کرده دست نمی‌خورد.
            val categoryMap = parsed.categories.associate { it.symbol to it.category }
            val existingItems = portfolioDao.getAllPortfolioItemsDirect()
            val changedItems = existingItems.mapNotNull { item ->
                if (item.isCategoryManuallySet) return@mapNotNull null
                val newCat = categoryMap[item.symbol.trim()] ?: return@mapNotNull null
                if (newCat != item.assetCategory) item.copy(assetCategory = newCat) else null
            }
            if (changedItems.isNotEmpty()) {
                portfolioDao.updateAll(changedItems)
            }

            Result.success(
                CategoryImportSummary(
                    appliedCount = parsed.categories.size,
                    skippedSymbols = parsed.unrecognizedSymbols
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
