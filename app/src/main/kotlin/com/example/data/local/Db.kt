package com.example.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * لایه‌ی نازک SQLite روی JDBC. جایگزین Room اندروید شده تا برای ساخت روی
 * ویندوز به هیچ کتابخانه‌ی Google نیازی نباشد. یک اتصال واحد داریم و همه‌ی
 * دسترسی‌ها پشت یک قفل سریال می‌شوند (SQLite هم‌زمانیِ نوشتن ندارد).
 */
class Db(path: String) {
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:$path").also {
        it.createStatement().use { s ->
            s.execute("PRAGMA journal_mode=WAL")
            s.execute("PRAGMA foreign_keys=ON")
        }
    }
    private val lock = Any()
    private val invalidations = MutableSharedFlow<String>(extraBufferCapacity = 64)

    fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> = synchronized(lock) {
        conn.prepareStatement(sql).use { st ->
            bind(st, args)
            st.executeQuery().use { rs ->
                val out = ArrayList<T>()
                while (rs.next()) out.add(map(rs))
                out
            }
        }
    }

    fun exec(sql: String, vararg args: Any?, table: String? = null): Long = synchronized(lock) {
        val id = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { st ->
            bind(st, args)
            st.executeUpdate()
            st.generatedKeys.use { if (it.next()) it.getLong(1) else 0L }
        }
        table?.let { invalidations.tryEmit(it) }
        id
    }

    /** چند دستور در یک تراکنش. */
    fun <T> transaction(vararg tables: String, block: () -> T): T = synchronized(lock) {
        conn.autoCommit = false
        try {
            val r = block()
            conn.commit()
            tables.forEach { invalidations.tryEmit(it) }
            r
        } catch (e: Throwable) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    fun script(vararg statements: String) = synchronized(lock) {
        conn.createStatement().use { s -> statements.forEach { s.execute(it) } }
    }

    fun <T> observe(vararg tables: String, load: () -> T): Flow<T> =
        merge(*tables.map { t -> invalidations.let { f -> flow { f.collect { if (it == t) emit(Unit) } } } }.toTypedArray())
            .onStart { emit(Unit) }
            .let { trigger -> flow { trigger.collect { emit(withContext(Dispatchers.IO) { load() }) } } }
            .flowOn(Dispatchers.IO)

    /** مثل [observe] ولی با عوض شدن پورتفوی انتخاب‌شده هم دوباره می‌خواند. */
    fun <T> observeScoped(scope: PortfolioScope, vararg tables: String, load: () -> T): Flow<T> =
        combine(observe(*tables) { Unit }, scope.id.distinctUntilChanged()) { _, _ -> Unit }
            .let { trigger -> flow { trigger.collect { emit(withContext(Dispatchers.IO) { load() }) } } }
            .flowOn(Dispatchers.IO)

    private fun bind(st: PreparedStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, a ->
            val idx = i + 1
            when (a) {
                null -> st.setObject(idx, null)
                is Boolean -> st.setInt(idx, if (a) 1 else 0)
                is Int -> st.setInt(idx, a)
                is Long -> st.setLong(idx, a)
                is Double -> st.setDouble(idx, a)
                is Enum<*> -> st.setString(idx, a.name)
                else -> st.setString(idx, a.toString())
            }
        }
    }
}

fun ResultSet.bool(col: String): Boolean = getInt(col) != 0
fun ResultSet.doubleOrNull(col: String): Double? = getDouble(col).takeUnless { wasNull() }

/** پورتفوی فعال. همه‌ی DAOهای سبد و تاریخچه با این شناسه فیلتر می‌شوند. */
class PortfolioScope(initial: Long = DEFAULT_ID) {
    val id = kotlinx.coroutines.flow.MutableStateFlow(initial)
    val current: Long get() = id.value

    companion object { const val DEFAULT_ID = 1L }
}
