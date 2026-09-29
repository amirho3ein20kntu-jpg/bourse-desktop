package com.example.data.local

import java.io.File

/** پایگاه‌داده‌ی محلی (SQLite با JDBC). اسکیمای نسخه‌ی ۱۰ اپ اندروید. */
class AppDatabase(file: File) {
    private val db = Db(file.absolutePath)

    init { migrate() }

    fun portfolioDao() = PortfolioDao(db)
    fun settingsDao() = SettingsDao(db)
    fun fundCategoryDao() = FundCategoryDao(db)
    fun portfolioSnapshotDao() = PortfolioSnapshotDao(db)
    fun symbolIsinDao() = SymbolIsinDao(db)
    fun inflationRateDao() = InflationRateDao(db)

    private fun migrate() {
        db.script(
            """CREATE TABLE IF NOT EXISTS portfolio_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, assetCategory TEXT NOT NULL,
                currentValue REAL NOT NULL, quantity INTEGER NOT NULL DEFAULT 0, lastPrice REAL NOT NULL DEFAULT 0.0,
                averagePrice REAL NOT NULL DEFAULT 0.0, netValueRatio REAL NOT NULL DEFAULT 1.0, manualTargetPercent REAL,
                isValueManuallySet INTEGER NOT NULL DEFAULT 0, isCategoryManuallySet INTEGER NOT NULL DEFAULT 0,
                isHoldLocked INTEGER NOT NULL DEFAULT 0)""",
            """CREATE TABLE IF NOT EXISTS pcmr_settings (
                id INTEGER PRIMARY KEY, riskTolerance REAL NOT NULL, timeHorizonMonths INTEGER NOT NULL,
                emlStock3M REAL NOT NULL, emlGold3M REAL NOT NULL, emlStock6M REAL NOT NULL, emlGold6M REAL NOT NULL,
                emlStock12M REAL NOT NULL, emlGold12M REAL NOT NULL, rebalanceThresholdPercent REAL NOT NULL,
                isManualWeightsEnabled INTEGER NOT NULL, manualGoldWeight REAL NOT NULL, manualStockWeight REAL NOT NULL,
                manualMixedWeight REAL NOT NULL, manualFixedWeight REAL NOT NULL,
                isLivePriceSyncEnabled INTEGER NOT NULL DEFAULT 1, isRebalanceAlertEnabled INTEGER NOT NULL DEFAULT 1,
                wasAboveThreshold INTEGER NOT NULL DEFAULT 0, lastAlertEpochMs INTEGER NOT NULL DEFAULT 0)""",
            "CREATE TABLE IF NOT EXISTS fund_categories (symbol TEXT PRIMARY KEY NOT NULL, category TEXT NOT NULL, fundName TEXT NOT NULL DEFAULT '')",
            """CREATE TABLE IF NOT EXISTS portfolio_snapshots (
                day TEXT PRIMARY KEY NOT NULL, epochMs INTEGER NOT NULL, totalValueRial REAL NOT NULL,
                principalRial REAL NOT NULL DEFAULT 0.0, basisValueRial REAL NOT NULL DEFAULT 0.0,
                trIndex REAL NOT NULL DEFAULT 0.0, symbolCount INTEGER NOT NULL DEFAULT 0)""",
            "CREATE TABLE IF NOT EXISTS symbol_isin (symbol TEXT PRIMARY KEY NOT NULL, isin TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL DEFAULT 0)",
            """CREATE TABLE IF NOT EXISTS holding_snapshots (
                day TEXT NOT NULL, symbol TEXT NOT NULL, assetCategory TEXT NOT NULL, quantity INTEGER NOT NULL DEFAULT 0,
                valueRial REAL NOT NULL, principalRial REAL NOT NULL DEFAULT 0.0, PRIMARY KEY(day, symbol))""",
            """CREATE TABLE IF NOT EXISTS inflation_rates (
                jalaliYear INTEGER NOT NULL, jalaliMonth INTEGER NOT NULL, ratePercent REAL NOT NULL,
                isManuallyEdited INTEGER NOT NULL DEFAULT 0, updatedAtEpochMs INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(jalaliYear, jalaliMonth))"""
        )
    }

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** %APPDATA%\PcmrRebalance روی ویندوز، ~/.pcmr-rebalance جای دیگر. */
        fun defaultDbFile(): File {
            val appData = System.getenv("APPDATA")
            val dir = if (appData != null) File(appData, "PcmrRebalance") else File(System.getProperty("user.home"), ".pcmr-rebalance")
            dir.mkdirs()
            return File(dir, "pcmr_database.db")
        }

        fun getDatabase(file: File = defaultDbFile()): AppDatabase =
            INSTANCE ?: synchronized(this) { INSTANCE ?: AppDatabase(file).also { INSTANCE = it } }
    }
}
