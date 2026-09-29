package com.example.domain.backup

import com.example.data.model.AssetCategory
import com.example.data.model.FundCategoryEntity
import com.example.data.model.PortfolioEntity
import com.example.data.model.SettingsEntity
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * پشتیبان‌گیری و بازیابی سبد — بدون وابستگی به اندروید، تا قابل تست باشد.
 *
 * تا امروز اگر گوشی عوض می‌شد یا اپ پاک می‌شد، کل سبد و تنظیمات از بین می‌رفت.
 * ضمناً «اعمال سفارش‌ها» داده واقعی کاربر را تغییر می‌دهد؛ بدون یک تور ایمنی
 * چنین قابلیتی نباید وجود داشته باشد.
 *
 * قالب فایل عمداً ساده و نسخه‌دار است تا نسخه‌های بعدی اپ بتوانند فایل‌های
 * قدیمی را بخوانند.
 */
object PortfolioBackup {

    /** نسخه قالب فایل پشتیبان. با هر تغییر ناسازگار یکی بالا می‌رود. */
    const val FORMAT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class BackupFile(
        val formatVersion: Int = FORMAT_VERSION,
        /** برای خوانایی انسان؛ منطق به آن تکیه نمی‌کند. */
        val createdAt: String = "",
        val settings: BackupSettings? = null,
        val portfolio: List<BackupItem> = emptyList(),
        val fundCategories: List<BackupCategory> = emptyList()
    )

    @Serializable
    data class BackupItem(
        val symbol: String,
        val category: String,
        val currentValue: Double,
        val quantity: Long = 0L,
        val lastPrice: Double = 0.0,
        val averagePrice: Double = 0.0,
        val manualTargetPercent: Double? = null,
        val isValueManuallySet: Boolean = false,
        val isCategoryManuallySet: Boolean = false,
        /** قفل نگه‌داری. پشتیبان‌های قدیمی این کلید را ندارند و false می‌گیرند. */
        val isHoldLocked: Boolean = false,
        /**
         * نسبت ارزش خالص به ناخالص. پشتیبان‌های قدیمی این کلید را ندارند و
         * ۱٫۰ می‌گیرند، یعنی همان رفتار قبلی تا بارگذاری بعدی اکسل.
         */
        val netValueRatio: Double = 1.0
    )

    @Serializable
    data class BackupCategory(
        val symbol: String,
        val category: String,
        val fundName: String = ""
    )

    @Serializable
    data class BackupSettings(
        val riskTolerance: Double,
        val timeHorizonMonths: Int,
        val emlStock3M: Double,
        val emlGold3M: Double,
        val emlStock6M: Double,
        val emlGold6M: Double,
        val emlStock12M: Double,
        val emlGold12M: Double,
        val rebalanceThresholdPercent: Double,
        val isManualWeightsEnabled: Boolean,
        val manualGoldWeight: Double,
        val manualStockWeight: Double,
        val manualMixedWeight: Double,
        val manualFixedWeight: Double,
        val isLivePriceSyncEnabled: Boolean
    )

    /** خلاصه‌ی آنچه از فایل پشتیبان خوانده شد. */
    data class RestoreResult(
        val settings: SettingsEntity?,
        val portfolio: List<PortfolioEntity>,
        val fundCategories: List<FundCategoryEntity>
    )

    // ------------------------------------------------------------------
    // خروجی گرفتن
    // ------------------------------------------------------------------

    fun encode(
        settings: SettingsEntity?,
        portfolio: List<PortfolioEntity>,
        fundCategories: List<FundCategoryEntity>,
        createdAt: String = ""
    ): String {
        val file = BackupFile(
            formatVersion = FORMAT_VERSION,
            createdAt = createdAt,
            settings = settings?.toBackup(),
            portfolio = portfolio.map { it.toBackup() },
            fundCategories = fundCategories.map { it.toBackup() }
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    // ------------------------------------------------------------------
    // بازیابی
    // ------------------------------------------------------------------

    /**
     * فایل پشتیبان را می‌خواند.
     *
     * شناسه‌ها عمداً بازیابی نمی‌شوند: ردیف‌ها با id صفر برمی‌گردند تا Room
     * خودش شناسه تازه بدهد و با رکوردهای موجود تداخل نکند.
     */
    fun decode(text: String): Result<RestoreResult> {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            return Result.failure(
                IllegalArgumentException("فایل پشتیبان خوانا نیست یا قالبش درست نیست.")
            )
        } catch (e: IllegalArgumentException) {
            return Result.failure(
                IllegalArgumentException("فایل پشتیبان خوانا نیست یا قالبش درست نیست.")
            )
        }

        if (file.formatVersion > FORMAT_VERSION) {
            return Result.failure(
                IllegalArgumentException(
                    "این فایل با نسخه جدیدتری از اپ ساخته شده (نسخه ${file.formatVersion}). " +
                            "اپ را به‌روزرسانی کنید."
                )
            )
        }

        val portfolio = file.portfolio
            .filter { it.symbol.isNotBlank() && it.currentValue > 0.0 }
            .map { it.toEntity() }

        val categories = file.fundCategories
            .filter { it.symbol.isNotBlank() }
            .map { it.toEntity() }

        if (portfolio.isEmpty() && categories.isEmpty() && file.settings == null) {
            return Result.failure(
                IllegalArgumentException("فایل پشتیبان هیچ داده قابل بازیابی ندارد.")
            )
        }

        return Result.success(
            RestoreResult(
                settings = file.settings?.toEntity(),
                portfolio = portfolio,
                fundCategories = categories
            )
        )
    }

    // ------------------------------------------------------------------
    // تبدیل‌ها
    // ------------------------------------------------------------------

    private fun PortfolioEntity.toBackup() = BackupItem(
        symbol = symbol,
        category = assetCategory.name,
        currentValue = currentValue,
        quantity = quantity,
        lastPrice = lastPrice,
        averagePrice = averagePrice,
        manualTargetPercent = manualTargetPercent,
        isValueManuallySet = isValueManuallySet,
        isCategoryManuallySet = isCategoryManuallySet,
        isHoldLocked = isHoldLocked,
        netValueRatio = netValueRatio
    )

    private fun BackupItem.toEntity() = PortfolioEntity(
        id = 0L,
        symbol = symbol.trim(),
        assetCategory = parseCategory(category),
        currentValue = currentValue,
        quantity = quantity,
        lastPrice = lastPrice,
        averagePrice = averagePrice,
        manualTargetPercent = manualTargetPercent,
        isValueManuallySet = isValueManuallySet,
        isCategoryManuallySet = isCategoryManuallySet,
        isHoldLocked = isHoldLocked,
        netValueRatio = netValueRatio
    )

    private fun FundCategoryEntity.toBackup() = BackupCategory(
        symbol = symbol,
        category = category.name,
        fundName = fundName
    )

    private fun BackupCategory.toEntity() = FundCategoryEntity(
        symbol = symbol.trim(),
        category = parseCategory(category),
        fundName = fundName
    )

    /** نام enum ناشناخته نباید کل بازیابی را بشکند. */
    private fun parseCategory(name: String): AssetCategory =
        AssetCategory.entries.firstOrNull { it.name == name.trim() } ?: AssetCategory.STOCK

    private fun SettingsEntity.toBackup() = BackupSettings(
        riskTolerance = riskTolerance,
        timeHorizonMonths = timeHorizonMonths,
        emlStock3M = emlStock3M,
        emlGold3M = emlGold3M,
        emlStock6M = emlStock6M,
        emlGold6M = emlGold6M,
        emlStock12M = emlStock12M,
        emlGold12M = emlGold12M,
        rebalanceThresholdPercent = rebalanceThresholdPercent,
        isManualWeightsEnabled = isManualWeightsEnabled,
        manualGoldWeight = manualGoldWeight,
        manualStockWeight = manualStockWeight,
        manualMixedWeight = manualMixedWeight,
        manualFixedWeight = manualFixedWeight,
        isLivePriceSyncEnabled = isLivePriceSyncEnabled
    )

    private fun BackupSettings.toEntity() = SettingsEntity(
        id = 1,
        riskTolerance = riskTolerance,
        timeHorizonMonths = timeHorizonMonths,
        emlStock3M = emlStock3M,
        emlGold3M = emlGold3M,
        emlStock6M = emlStock6M,
        emlGold6M = emlGold6M,
        emlStock12M = emlStock12M,
        emlGold12M = emlGold12M,
        rebalanceThresholdPercent = rebalanceThresholdPercent,
        isManualWeightsEnabled = isManualWeightsEnabled,
        manualGoldWeight = manualGoldWeight,
        manualStockWeight = manualStockWeight,
        manualMixedWeight = manualMixedWeight,
        manualFixedWeight = manualFixedWeight,
        isLivePriceSyncEnabled = isLivePriceSyncEnabled
    )
}
