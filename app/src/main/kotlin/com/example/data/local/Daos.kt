package com.example.data.local

import com.example.data.model.AssetCategory
import com.example.data.model.FundCategoryEntity
import com.example.data.model.HoldingSnapshotEntity
import com.example.data.model.InflationRateEntity
import com.example.data.model.PortfolioEntity
import com.example.data.model.PortfolioSnapshotEntity
import com.example.data.model.SettingsEntity
import com.example.data.model.SymbolIsinEntity
import kotlinx.coroutines.flow.Flow
import java.sql.ResultSet

private fun category(v: String?): AssetCategory =
    try { if (v != null) AssetCategory.valueOf(v) else AssetCategory.STOCK } catch (_: Exception) { AssetCategory.STOCK }

class PortfolioDao(private val db: Db, private val scope: PortfolioScope) {
    private fun map(r: ResultSet) = PortfolioEntity(
        id = r.getLong("id"),
        symbol = r.getString("symbol"),
        assetCategory = category(r.getString("assetCategory")),
        currentValue = r.getDouble("currentValue"),
        quantity = r.getLong("quantity"),
        lastPrice = r.getDouble("lastPrice"),
        averagePrice = r.getDouble("averagePrice"),
        netValueRatio = r.getDouble("netValueRatio"),
        manualTargetPercent = r.doubleOrNull("manualTargetPercent"),
        isValueManuallySet = r.bool("isValueManuallySet"),
        isCategoryManuallySet = r.bool("isCategoryManuallySet"),
        isHoldLocked = r.bool("isHoldLocked")
    )

    private val all = "SELECT * FROM portfolio_items WHERE portfolioId = ? ORDER BY currentValue DESC"

    fun getAllPortfolioItems(): Flow<List<PortfolioEntity>> = db.observeScoped(scope, "portfolio_items") { db.query(all, scope.current, map = ::map) }
    suspend fun getAllPortfolioItemsDirect(): List<PortfolioEntity> = db.query(all, scope.current, map = ::map)
    suspend fun getItemById(id: Long): PortfolioEntity? =
        db.query("SELECT * FROM portfolio_items WHERE id = ? AND portfolioId = ? LIMIT 1", id, scope.current, map = ::map).firstOrNull()

    private val upsertSql = """INSERT OR REPLACE INTO portfolio_items
        (id, portfolioId, symbol, assetCategory, currentValue, quantity, lastPrice, averagePrice, netValueRatio,
         manualTargetPercent, isValueManuallySet, isCategoryManuallySet, isHoldLocked)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"""

    private fun args(i: PortfolioEntity, keepId: Boolean = true): Array<Any?> = arrayOf(
        if (keepId && i.id != 0L) i.id else null, scope.current, i.symbol, i.assetCategory, i.currentValue, i.quantity, i.lastPrice,
        i.averagePrice, i.netValueRatio, i.manualTargetPercent, i.isValueManuallySet, i.isCategoryManuallySet, i.isHoldLocked
    )

    suspend fun insertItem(item: PortfolioEntity): Long = db.exec(upsertSql, *args(item), table = "portfolio_items")
    suspend fun insertAll(items: List<PortfolioEntity>) {
        db.transaction("portfolio_items") { items.forEach { db.exec(upsertSql, *args(it)) } }
    }
    suspend fun updateItem(item: PortfolioEntity) { db.exec(upsertSql, *args(item), table = "portfolio_items") }
    suspend fun updateAll(items: List<PortfolioEntity>) = insertAll(items)
    suspend fun deleteItemById(id: Long) { db.exec("DELETE FROM portfolio_items WHERE id = ? AND portfolioId = ?", id, scope.current, table = "portfolio_items") }
    suspend fun deleteItem(item: PortfolioEntity) = deleteItemById(item.id)
    suspend fun clearAll() { db.exec("DELETE FROM portfolio_items WHERE portfolioId = ?", scope.current, table = "portfolio_items") }
}

class SettingsDao(private val db: Db) {
    private fun map(r: ResultSet) = SettingsEntity(
        id = r.getInt("id"),
        riskTolerance = r.getDouble("riskTolerance"),
        timeHorizonMonths = r.getInt("timeHorizonMonths"),
        emlStock3M = r.getDouble("emlStock3M"), emlGold3M = r.getDouble("emlGold3M"),
        emlStock6M = r.getDouble("emlStock6M"), emlGold6M = r.getDouble("emlGold6M"),
        emlStock12M = r.getDouble("emlStock12M"), emlGold12M = r.getDouble("emlGold12M"),
        rebalanceThresholdPercent = r.getDouble("rebalanceThresholdPercent"),
        isManualWeightsEnabled = r.bool("isManualWeightsEnabled"),
        manualGoldWeight = r.getDouble("manualGoldWeight"), manualStockWeight = r.getDouble("manualStockWeight"),
        manualMixedWeight = r.getDouble("manualMixedWeight"), manualFixedWeight = r.getDouble("manualFixedWeight"),
        isLivePriceSyncEnabled = r.bool("isLivePriceSyncEnabled"),
        isRebalanceAlertEnabled = r.bool("isRebalanceAlertEnabled"),
        wasAboveThreshold = r.bool("wasAboveThreshold"),
        lastAlertEpochMs = r.getLong("lastAlertEpochMs")
    )

    private val q = "SELECT * FROM pcmr_settings WHERE id = 1 LIMIT 1"
    fun getSettingsFlow(): Flow<SettingsEntity?> = db.observe("pcmr_settings") { db.query(q, map = ::map).firstOrNull() }
    suspend fun getSettingsDirect(): SettingsEntity? = db.query(q, map = ::map).firstOrNull()

    suspend fun insertOrUpdate(s: SettingsEntity) {
        db.exec(
            """INSERT OR REPLACE INTO pcmr_settings
            (id, riskTolerance, timeHorizonMonths, emlStock3M, emlGold3M, emlStock6M, emlGold6M, emlStock12M, emlGold12M,
             rebalanceThresholdPercent, isManualWeightsEnabled, manualGoldWeight, manualStockWeight, manualMixedWeight,
             manualFixedWeight, isLivePriceSyncEnabled, isRebalanceAlertEnabled, wasAboveThreshold, lastAlertEpochMs)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            s.id, s.riskTolerance, s.timeHorizonMonths, s.emlStock3M, s.emlGold3M, s.emlStock6M, s.emlGold6M,
            s.emlStock12M, s.emlGold12M, s.rebalanceThresholdPercent, s.isManualWeightsEnabled, s.manualGoldWeight,
            s.manualStockWeight, s.manualMixedWeight, s.manualFixedWeight, s.isLivePriceSyncEnabled,
            s.isRebalanceAlertEnabled, s.wasAboveThreshold, s.lastAlertEpochMs, table = "pcmr_settings"
        )
    }
    suspend fun update(s: SettingsEntity) = insertOrUpdate(s)
}

class FundCategoryDao(private val db: Db) {
    private fun map(r: ResultSet) =
        FundCategoryEntity(r.getString("symbol"), category(r.getString("category")), r.getString("fundName") ?: "")

    fun getAllFundCategoriesFlow(): Flow<List<FundCategoryEntity>> =
        db.observe("fund_categories") { db.query("SELECT * FROM fund_categories", map = ::map) }
    suspend fun getAllCategoriesDirect(): List<FundCategoryEntity> = db.query("SELECT * FROM fund_categories", map = ::map)
    suspend fun getCategoryBySymbol(symbol: String): FundCategoryEntity? =
        db.query("SELECT * FROM fund_categories WHERE symbol = ? LIMIT 1", symbol, map = ::map).firstOrNull()

    private val up = "INSERT OR REPLACE INTO fund_categories (symbol, category, fundName) VALUES (?,?,?)"
    suspend fun insertOrUpdate(f: FundCategoryEntity) { db.exec(up, f.symbol, f.category, f.fundName, table = "fund_categories") }
    suspend fun insertAll(list: List<FundCategoryEntity>) {
        db.transaction("fund_categories") { list.forEach { db.exec(up, it.symbol, it.category, it.fundName) } }
    }
    suspend fun deleteCategory(symbol: String) { db.exec("DELETE FROM fund_categories WHERE symbol = ?", symbol, table = "fund_categories") }
    suspend fun clearAll() { db.exec("DELETE FROM fund_categories", table = "fund_categories") }
}

class PortfolioSnapshotDao(private val db: Db, private val scope: PortfolioScope) {
    private fun snap(r: ResultSet) = PortfolioSnapshotEntity(
        day = r.getString("day"), epochMs = r.getLong("epochMs"), totalValueRial = r.getDouble("totalValueRial"),
        principalRial = r.getDouble("principalRial"), basisValueRial = r.getDouble("basisValueRial"),
        trIndex = r.getDouble("trIndex"), symbolCount = r.getInt("symbolCount")
    )
    private fun hold(r: ResultSet) = HoldingSnapshotEntity(
        day = r.getString("day"), symbol = r.getString("symbol"), assetCategory = category(r.getString("assetCategory")),
        quantity = r.getLong("quantity"), valueRial = r.getDouble("valueRial"), principalRial = r.getDouble("principalRial")
    )

    fun getAllSnapshots(): Flow<List<PortfolioSnapshotEntity>> =
        db.observeScoped(scope, "portfolio_snapshots") { db.query("SELECT * FROM portfolio_snapshots WHERE portfolioId = ? ORDER BY epochMs ASC", scope.current, map = ::snap) }
    suspend fun getLatest(): PortfolioSnapshotEntity? =
        db.query("SELECT * FROM portfolio_snapshots WHERE portfolioId = ? ORDER BY epochMs DESC LIMIT 1", scope.current, map = ::snap).firstOrNull()

    private val upSnap = """INSERT OR REPLACE INTO portfolio_snapshots
        (portfolioId, day, epochMs, totalValueRial, principalRial, basisValueRial, trIndex, symbolCount) VALUES (?,?,?,?,?,?,?,?)"""
    private val upHold = """INSERT OR REPLACE INTO holding_snapshots
        (portfolioId, day, symbol, assetCategory, quantity, valueRial, principalRial) VALUES (?,?,?,?,?,?,?)"""

    suspend fun upsert(s: PortfolioSnapshotEntity) {
        db.exec(upSnap, scope.current, s.day, s.epochMs, s.totalValueRial, s.principalRial, s.basisValueRial, s.trIndex, s.symbolCount, table = "portfolio_snapshots")
    }
    suspend fun clearAll() { db.exec("DELETE FROM portfolio_snapshots WHERE portfolioId = ?", scope.current, table = "portfolio_snapshots") }

    fun getAllHoldings(): Flow<List<HoldingSnapshotEntity>> =
        db.observeScoped(scope, "holding_snapshots") { db.query("SELECT * FROM holding_snapshots WHERE portfolioId = ? ORDER BY day ASC, valueRial DESC", scope.current, map = ::hold) }
    suspend fun deleteHoldingsForDay(day: String) { db.exec("DELETE FROM holding_snapshots WHERE portfolioId = ? AND day = ?", scope.current, day, table = "holding_snapshots") }
    suspend fun insertHoldings(list: List<HoldingSnapshotEntity>) {
        db.transaction("holding_snapshots") {
            list.forEach { db.exec(upHold, scope.current, it.day, it.symbol, it.assetCategory, it.quantity, it.valueRial, it.principalRial) }
        }
    }
    suspend fun clearAllHoldings() { db.exec("DELETE FROM holding_snapshots WHERE portfolioId = ?", scope.current, table = "holding_snapshots") }

    suspend fun recordCapture(snapshot: PortfolioSnapshotEntity, holdings: List<HoldingSnapshotEntity>) {
        db.transaction("portfolio_snapshots", "holding_snapshots") {
            db.exec(upSnap, scope.current, snapshot.day, snapshot.epochMs, snapshot.totalValueRial, snapshot.principalRial,
                snapshot.basisValueRial, snapshot.trIndex, snapshot.symbolCount)
            db.exec("DELETE FROM holding_snapshots WHERE portfolioId = ? AND day = ?", scope.current, snapshot.day)
            holdings.forEach { db.exec(upHold, scope.current, it.day, it.symbol, it.assetCategory, it.quantity, it.valueRial, it.principalRial) }
        }
    }
}

class SymbolIsinDao(private val db: Db) {
    private fun map(r: ResultSet) = SymbolIsinEntity(r.getString("symbol"), r.getString("isin"), r.getLong("updatedAtEpochMs"))
    fun observeAll(): Flow<List<SymbolIsinEntity>> = db.observe("symbol_isin") { db.query("SELECT * FROM symbol_isin", map = ::map) }
    suspend fun upsert(e: SymbolIsinEntity) {
        db.exec("INSERT OR REPLACE INTO symbol_isin (symbol, isin, updatedAtEpochMs) VALUES (?,?,?)", e.symbol, e.isin, e.updatedAtEpochMs, table = "symbol_isin")
    }
    suspend fun delete(symbol: String) { db.exec("DELETE FROM symbol_isin WHERE symbol = ?", symbol, table = "symbol_isin") }
}

class InflationRateDao(private val db: Db) {
    private fun map(r: ResultSet) = InflationRateEntity(
        r.getInt("jalaliYear"), r.getInt("jalaliMonth"), r.getDouble("ratePercent"),
        r.bool("isManuallyEdited"), r.getLong("updatedAtEpochMs")
    )
    fun observeAll(): Flow<List<InflationRateEntity>> = db.observe("inflation_rates") {
        db.query("SELECT * FROM inflation_rates ORDER BY jalaliYear DESC, jalaliMonth DESC", map = ::map)
    }
    suspend fun get(year: Int, month: Int): InflationRateEntity? =
        db.query("SELECT * FROM inflation_rates WHERE jalaliYear = ? AND jalaliMonth = ? LIMIT 1", year, month, map = ::map).firstOrNull()

    private val up = """INSERT OR REPLACE INTO inflation_rates
        (jalaliYear, jalaliMonth, ratePercent, isManuallyEdited, updatedAtEpochMs) VALUES (?,?,?,?,?)"""
    suspend fun upsert(e: InflationRateEntity) {
        db.exec(up, e.jalaliYear, e.jalaliMonth, e.ratePercent, e.isManuallyEdited, e.updatedAtEpochMs, table = "inflation_rates")
    }
    suspend fun upsertAll(list: List<InflationRateEntity>) {
        db.transaction("inflation_rates") { list.forEach { db.exec(up, it.jalaliYear, it.jalaliMonth, it.ratePercent, it.isManuallyEdited, it.updatedAtEpochMs) } }
    }
    suspend fun delete(year: Int, month: Int) {
        db.exec("DELETE FROM inflation_rates WHERE jalaliYear = ? AND jalaliMonth = ?", year, month, table = "inflation_rates")
    }
}

data class PortfolioInfo(val id: Long, val name: String)

/** فهرست پورتفوها («خودم»، «همسرم»، …). تنظیمات و دسته‌بندی صندوق‌ها بین همه مشترک است. */
class PortfolioDirectoryDao(private val db: Db) {
    private fun map(r: ResultSet) = PortfolioInfo(r.getLong("id"), r.getString("name"))

    fun observeAll(): Flow<List<PortfolioInfo>> =
        db.observe("portfolios") { db.query("SELECT * FROM portfolios ORDER BY id", map = ::map) }
    suspend fun getAll(): List<PortfolioInfo> = db.query("SELECT * FROM portfolios ORDER BY id", map = ::map)

    suspend fun create(name: String): Long =
        db.exec("INSERT INTO portfolios (name, createdAtEpochMs) VALUES (?,?)", name.trim(), System.currentTimeMillis(), table = "portfolios")
    suspend fun rename(id: Long, name: String) { db.exec("UPDATE portfolios SET name = ? WHERE id = ?", name.trim(), id, table = "portfolios") }

    /** پورتفو را با همه‌ی دارایی‌ها و تاریخچه‌اش حذف می‌کند. */
    suspend fun delete(id: Long) {
        db.transaction("portfolios", "portfolio_items", "portfolio_snapshots", "holding_snapshots") {
            db.exec("DELETE FROM portfolio_items WHERE portfolioId = ?", id)
            db.exec("DELETE FROM portfolio_snapshots WHERE portfolioId = ?", id)
            db.exec("DELETE FROM holding_snapshots WHERE portfolioId = ?", id)
            db.exec("DELETE FROM portfolios WHERE id = ?", id)
        }
    }

    /** وضعیت هشدار برای هر پورتفو جدا نگه داشته می‌شود تا هشدار یکی، دیگری را خاموش نکند. */
    fun alertState(id: Long): Pair<Boolean, Long> =
        db.query("SELECT wasAboveThreshold, lastAlertEpochMs FROM portfolios WHERE id = ?", id) { it.bool("wasAboveThreshold") to it.getLong("lastAlertEpochMs") }
            .firstOrNull() ?: (false to 0L)
    fun saveAlertState(id: Long, wasAbove: Boolean, lastAlertEpochMs: Long) {
        db.exec("UPDATE portfolios SET wasAboveThreshold = ?, lastAlertEpochMs = ? WHERE id = ?", wasAbove, lastAlertEpochMs, id)
    }

    fun savedSelection(): Long? = db.query("SELECT value FROM app_state WHERE key = 'selectedPortfolio'") { it.getString(1).toLongOrNull() }.firstOrNull()
    fun saveSelection(id: Long) { db.exec("INSERT OR REPLACE INTO app_state (key, value) VALUES ('selectedPortfolio', ?)", id.toString()) }
}
