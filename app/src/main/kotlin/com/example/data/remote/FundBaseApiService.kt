package com.example.data.remote

import com.example.platform.AppLog as Log
import com.example.BuildConfig
import com.example.data.model.AssetCategory
import com.example.data.model.FundTechnicalInsight
import com.example.data.model.FundReturns
import com.example.data.model.FundConsistency
import com.example.data.model.FundRiskStats
import com.example.data.model.FundTechnicalState
import com.example.data.model.PortfolioEntity
import com.example.data.parser.DefaultEtfDatabase
import com.example.domain.sync.PortfolioPriceSync
import com.example.domain.sync.PriceSyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Data holder for fund market data fetched from FundBase Supabase API.
 */
data class FundBaseLiveData(
    val fundId: String,
    val symbol: String,
    val currentPriceRial: Double,
    val navRial: Double,
    val bubblePercent: Double,
    val bubbleAmountRial: Double,
    val tradingValueRial: Double,
    val tradingVolume: Long,
    val priceChangePercent: Double,
    val categoryId: String? = null,
    val categorySlug: String? = null,
    val timestamp: String? = null,
    val liquidityRial: Double = 0.0,
    val netAssetRial: Double = 0.0,
    val establishedDate: String? = null,
    val manager: String? = null
)

/**
 * Service to interact with the FundBase Supabase REST API for Iran ETF market data.
 * Endpoint: https://fyeguwhlfqhomqpkbgxb.supabase.co/rest/v1/fund_live_data
 */
object FundBaseApiService {

    private const val TAG = "FundBaseApiService"
    private const val SUPABASE_URL =
        "https://fyeguwhlfqhomqpkbgxb.supabase.co/rest/v1/fund_live_data?select=fund_id,current_price,close_price,nav,bubble_percent,bubble_amount,trading_value,trading_volume,price_change_percent,liquidity,net_asset,funds(id,name,slug,category_id,established_date,initiation_date,manager)&trading_value=gt.0"

    /**
     * کلید anon سرویس FundBase. از `.env`/`.env.example` می‌آید (پلاگین secrets)
     * تا با چرخیدن کلید فقط همان فایل عوض شود، نه این سورس.
     */
    private val API_KEY: String = BuildConfig.FUNDBASE_API_KEY

    /**
     * وضعیت تکنیکال همه صندوق‌ها در یک درخواست (۳۱۸ ردیف).
     * جایگزین درخواست جداگانه به‌ازای هر نماد شد.
     */
    private const val TA_STATE_URL =
        "https://fyeguwhlfqhomqpkbgxb.supabase.co/rest/v1/ta_symbol_state?asset_type=eq.fund&select=asset_key,last_price,last_dt,trend_d,trend_w,trend_m,rsi14_d,price_vs_sma50_pct,price_vs_sma200_pct,nearest_support,nearest_support_dist_pct,nearest_resistance,nearest_resistance_dist_pct,last_signal,state_label"

    /**
     * بازدهی دوره‌ای صندوق‌ها.
     *
     * سایت FundBase برای **هر صندوق یک درخواست جدا** می‌زند
     * (`fund_id=eq.…&limit=1`). برای جدول مقایسه‌ای با ۳۰۰ صندوق یعنی ۳۰۰
     * رفت‌وبرگشت — همان چیزی که محافظ‌های anti-ban اینجا قرار است جلویش را
     * بگیرد. به‌جایش دو درخواست می‌زنیم: اول آخرین تاریخ موجود، بعد همه ردیف‌های
     * همان تاریخ.
     */
    private const val RETURNS_LATEST_DATE_URL =
        "https://fyeguwhlfqhomqpkbgxb.supabase.co/rest/v1/fund_return_data?select=date&order=date.desc&limit=1"

    private const val RETURNS_BY_DATE_URL =
        "https://fyeguwhlfqhomqpkbgxb.supabase.co/rest/v1/fund_return_data?select=fund_id,date,jalali_date,return_daily,return_weekly,return_monthly,return_quarterly,return_biannual,return_yearly&date=eq."

    /** بازدهی روزی یک‌بار محاسبه می‌شود؛ شش ساعت کش کافی است. */
    private const val RETURNS_CACHE_TTL_MS = 6L * 60 * 60 * 1000

    @Volatile
    private var cachedReturns: Map<String, FundReturns> = emptyMap()

    @Volatile
    private var lastReturnsFetchEpochMs: Long = 0L

    /** تاریخ شمسی داده بازدهی، همان‌طور که API می‌دهد. */
    @Volatile
    var returnsAsOfJalaliDate: String = ""
        private set

    private val returnsMutex = Mutex()

    // Cache of latest fetched data keyed by fund symbol (Persian name)
    @Volatile
    private var cachedFundMap: Map<String, FundBaseLiveData> = emptyMap()

    @Volatile
    var lastFetchTimestamp: String = ""
        private set

    @Volatile
    private var lastFetchEpochMs: Long = 0L

    /**
     * فاصله زمانی تا آخرین دریافت **موفق** داده بازار.
     * `null` یعنی هنوز هیچ دریافت موفقی انجام نشده و آنچه در دست است داده‌ای نیست.
     * UI باید بر اساس همین مقدار تصمیم بگیرد که برچسب «زنده» بزند یا «کش».
     */
    val marketDataAgeMs: Long?
        get() = if (lastFetchEpochMs == 0L) null else System.currentTimeMillis() - lastFetchEpochMs

    // Anti-ban / Throttling safeguards:
    // Minimum 15 seconds cooldown between real remote calls (re-uses cache in between)
    private const val CACHE_TTL_MS = 15_000L

    /** داده تکنیکال روزانه محاسبه می‌شود؛ ۱۰ دقیقه کش کافی است. */
    private const val TA_CACHE_TTL_MS = 600_000L

    // Rate-limit tracking (HTTP 429 backoff)
    @Volatile
    private var isBlockedOrRateLimited: Boolean = false

    @Volatile
    private var backoffUntilEpochMs: Long = 0L

    /**
     * هر منبع فقط یک درخواست همزمان.
     *
     * بررسی کش و بعد درخواست، یک الگوی check-then-act روی فیلدهای @Volatile بود:
     * چند رفرش همزمان (مثلاً باز شدن دیده‌بان درست بعد از استارت اپ) هر کدام
     * جداگانه کش را «منقضی» می‌دیدند و هم‌زمان به سرور می‌زدند — دقیقاً همان
     * چیزی که محافظ‌های anti-ban قرار بود جلویش را بگیرند.
     */
    private val liveFundsMutex = Mutex()
    private val technicalStatesMutex = Mutex()

    /** کشِ قابل استفاده، یا null اگر واقعاً باید به شبکه بزنیم. */
    private fun usableFundCache(forceRefresh: Boolean): Map<String, FundBaseLiveData>? {
        val now = System.currentTimeMillis()

        // 1. If currently under temporary rate-limit backoff, safely return cached data without hitting server
        if (now < backoffUntilEpochMs && cachedFundMap.isNotEmpty()) {
            Log.w(TAG, "Under rate-limit protection. Returning cached data (${(backoffUntilEpochMs - now) / 1000}s remaining).")
            return cachedFundMap
        }

        // 2. Cache hit: If data was retrieved within CACHE_TTL_MS and forceRefresh is false, serve cache
        if (!forceRefresh && (now - lastFetchEpochMs < CACHE_TTL_MS) && cachedFundMap.isNotEmpty()) {
            Log.d(TAG, "Serving fresh FundBase data from memory cache (${cachedFundMap.size} funds).")
            return cachedFundMap
        }

        return null
    }

    /**
     * Fetches all live fund data from FundBase Supabase API with anti-ban throttling,
     * in-memory caching, HTTP 429 backoff, and graceful fallback.
     *
     * فراخوان‌های همزمان روی یک درخواست جمع می‌شوند؛ دومی نتیجه‌ی اولی را
     * می‌گیرد، نه یک رفت‌وبرگشت تازه.
     */
    suspend fun fetchAllLiveFunds(forceRefresh: Boolean = false): Map<String, FundBaseLiveData> = withContext(Dispatchers.IO) {
        usableFundCache(forceRefresh)?.let { return@withContext it }

        liveFundsMutex.withLock {
            // پشت قفل دوباره بررسی می‌شود: ممکن است در همین فاصله فراخوانِ دیگری
            // داده را تازه کرده باشد. اینجا forceRefresh عمداً false است تا
            // نتیجه‌ی همان درخواست بازاستفاده شود، نه اینکه دوباره زده شود.
            usableFundCache(forceRefresh = false)?.let { return@withLock it }

            fetchLiveFundsFromNetwork()
        }
    }

    private fun fetchLiveFundsFromNetwork(): Map<String, FundBaseLiveData> {
        val now = System.currentTimeMillis()

        return try {
            val url = URL(SUPABASE_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("accept", "*/*")
                setRequestProperty("apikey", API_KEY)
                setRequestProperty("authorization", "Bearer $API_KEY")
                setRequestProperty("Origin", "https://app.fundbase.ir")
                setRequestProperty("Referer", "https://app.fundbase.ir/")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36")
                setRequestProperty("x-client-info", "supabase-js-web/2.105.3")
                setRequestProperty("sec-fetch-dest", "empty")
                setRequestProperty("sec-fetch-mode", "cors")
                setRequestProperty("sec-fetch-site", "cross-site")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
                val jsonString = reader.use { it.readText() }
                val jsonArray = JSONArray(jsonString)

                val resultMap = mutableMapOf<String, FundBaseLiveData>()
                val timeNow = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(i) ?: continue
                    val fundsObj = item.optJSONObject("funds")
                    val rawName = fundsObj?.optString("name", "")?.trim() ?: ""
                    if (rawName.isEmpty()) continue

                    val currentPrice = item.optDouble("current_price", 0.0)
                    val closePrice = item.optDouble("close_price", 0.0)
                    val price = if (currentPrice > 0) currentPrice else closePrice

                    val nav = item.optDouble("nav", price)
                    val bubblePercent = item.optDouble("bubble_percent", if (nav > 0) ((price - nav) / nav) * 100 else 0.0)
                    val bubbleAmount = item.optDouble("bubble_amount", price - nav)
                    val tradingVal = item.optDouble("trading_value", 0.0)
                    val tradingVol = item.optLong("trading_volume", 0L)
                    val priceChange = item.optDouble("price_change_percent", 0.0)
                    val fundId = item.optString("fund_id", "")
                    val liquidityRial = item.optDouble("liquidity", tradingVal)
                    val netAssetRial = item.optDouble("net_asset", 0.0)

                    val categoryId = fundsObj?.optString("category_id", null)
                    val estDate = fundsObj?.optString("established_date", null)
                        ?: fundsObj?.optString("initiation_date", null)
                    val manager = fundsObj?.optString("manager", null)

                    val liveData = FundBaseLiveData(
                        fundId = fundId,
                        symbol = rawName,
                        currentPriceRial = price,
                        navRial = nav,
                        bubblePercent = bubblePercent,
                        bubbleAmountRial = bubbleAmount,
                        tradingValueRial = tradingVal,
                        tradingVolume = tradingVol,
                        priceChangePercent = priceChange,
                        categoryId = categoryId,
                        categorySlug = fundsObj?.optString("slug", null),
                        timestamp = timeNow,
                        liquidityRial = liquidityRial,
                        netAssetRial = netAssetRial,
                        establishedDate = estDate,
                        manager = manager
                    )

                    resultMap[rawName] = liveData
                }

                cachedFundMap = resultMap
                lastFetchTimestamp = timeNow
                lastFetchEpochMs = now
                isBlockedOrRateLimited = false
                Log.d(TAG, "Successfully loaded ${resultMap.size} live ETF funds from FundBase (Single batch call).")
                resultMap
            } else if (responseCode == 429) {
                // HTTP 429 Too Many Requests: Enter 60-second backoff
                Log.w(TAG, "FundBase API responded with HTTP 429 (Rate limited). Entering 60-second backoff.")
                isBlockedOrRateLimited = true
                backoffUntilEpochMs = now + 60_000L
                cachedFundMap
            } else {
                Log.w(TAG, "FundBase API responded with HTTP $responseCode")
                cachedFundMap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from FundBase API: ${e.localizedMessage}")
            cachedFundMap
        }
    }

    // ---------------------------------------------------------------------
    // بازدهی دوره‌ای (fund_return_data)
    // ---------------------------------------------------------------------

    private fun usableReturnsCache(forceRefresh: Boolean): Map<String, FundReturns>? {
        val now = System.currentTimeMillis()
        if (now < backoffUntilEpochMs && cachedReturns.isNotEmpty()) return cachedReturns
        if (!forceRefresh &&
            (now - lastReturnsFetchEpochMs < RETURNS_CACHE_TTL_MS) &&
            cachedReturns.isNotEmpty()
        ) {
            return cachedReturns
        }
        return null
    }

    /**
     * بازدهی همه صندوق‌ها را می‌گیرد، کلیدخورده با `fund_id`.
     * در صورت شکست، کش قبلی برمی‌گردد — هیچ عددی ساخته نمی‌شود.
     */
    suspend fun fetchAllReturns(forceRefresh: Boolean = false): Map<String, FundReturns> =
        withContext(Dispatchers.IO) {
            usableReturnsCache(forceRefresh)?.let { return@withContext it }

            returnsMutex.withLock {
                usableReturnsCache(forceRefresh = false)?.let { return@withLock it }
                fetchReturnsFromNetwork()
            }
        }

    private fun fetchReturnsFromNetwork(): Map<String, FundReturns> {
        val latestDate = fetchLatestReturnsDate() ?: return cachedReturns

        return try {
            val body = readJson(RETURNS_BY_DATE_URL + latestDate) ?: return cachedReturns
            val arr = JSONArray(body)
            val map = mutableMapOf<String, FundReturns>()
            var jalali = ""

            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val fundId = o.optString("fund_id", "").trim()
                if (fundId.isEmpty()) continue

                if (jalali.isEmpty()) jalali = o.optString("jalali_date", "")

                map[fundId] = FundReturns(
                    fundId = fundId,
                    date = o.optString("date", ""),
                    jalaliDate = o.optString("jalali_date", ""),
                    daily = o.optDoubleOrNull("return_daily"),
                    weekly = o.optDoubleOrNull("return_weekly"),
                    monthly = o.optDoubleOrNull("return_monthly"),
                    quarterly = o.optDoubleOrNull("return_quarterly"),
                    biannual = o.optDoubleOrNull("return_biannual"),
                    yearly = o.optDoubleOrNull("return_yearly")
                )
            }

            if (map.isEmpty()) return cachedReturns

            cachedReturns = map
            returnsAsOfJalaliDate = jalali
            lastReturnsFetchEpochMs = System.currentTimeMillis()
            Log.d(TAG, "Loaded ${map.size} fund return rows for $latestDate.")
            map
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching fund_return_data: ${e.localizedMessage}")
            cachedReturns
        }
    }

    /** آخرین تاریخی که برایش بازدهی محاسبه شده. */
    private fun fetchLatestReturnsDate(): String? = try {
        val body = readJson(RETURNS_LATEST_DATE_URL)
        val arr = if (body != null) JSONArray(body) else null
        arr?.optJSONObject(0)?.optString("date", "")?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.e(TAG, "Error reading latest return date: ${e.localizedMessage}")
        null
    }

    /** بازدهی یک صندوق بر اساس شناسه‌اش؛ null یعنی داده‌ای نیست. */
    fun getReturnsForFund(fundId: String?): FundReturns? {
        if (fundId.isNullOrBlank()) return null
        return cachedReturns[fundId.trim()]
    }

    // ---------------------------------------------------------------------
    // تداوم بازدهی (rpc/get_fund_return_consistency)
    // ---------------------------------------------------------------------

    private const val CONSISTENCY_URL =
        "https://fyeguwhlfqhomqpkbgxb.supabase.co/rest/v1/rpc/get_fund_return_consistency"

    /**
     * همان مقداری که خود FundBase می‌فرستد. «حداقل ۳۰ روز سابقه» در توضیح
     * ستون، همین پارامتر است — نه یک قاعده جداگانه که ما تفسیرش کنیم.
     */
    private const val CONSISTENCY_MIN_DAYS = 30

    /** امتیاز روزی یک‌بار عوض می‌شود؛ شش ساعت کش کافی است. */
    private const val CONSISTENCY_CACHE_TTL_MS = 6L * 60 * 60 * 1000

    @Volatile
    private var cachedConsistency: Map<String, FundConsistency> = emptyMap()

    @Volatile
    private var lastConsistencyFetchEpochMs: Long = 0L

    private val consistencyMutex = Mutex()

    /**
     * امتیاز تداوم بازدهی همه صندوق‌ها.
     *
     * **این عدد محاسبه نمی‌شود، خوانده می‌شود.** نسخه قبلی خودش حسابش می‌کرد
     * بر اساس تعریفی که FundBase در ⓘ ستون نوشته («میانگین رتبه سه بازه»)،
     * ولی آن تعریف عددهای خود سایت را تولید نمی‌کرد: «دوایکس» که در هر سه
     * بازه اول است باید ۱۰۰ می‌گرفت و سایت ۵۰ داده بود. حالا از همان تابعی
     * خوانده می‌شود که سایت صدا می‌زند، پس عدد برنامه و عدد سایت یکی است.
     *
     * تابع یک دسته در هر فراخوانی می‌گیرد، پس به‌ازای **هر دسته‌ای که واقعاً
     * در داده زنده هست** یک درخواست می‌رود — نه به‌ازای هر صندوق. حدود ده
     * درخواست، شش ساعت یک‌بار.
     */
    suspend fun fetchAllConsistency(forceRefresh: Boolean = false): Map<String, FundConsistency> =
        withContext(Dispatchers.IO) {
            usableConsistencyCache(forceRefresh)?.let { return@withContext it }

            consistencyMutex.withLock {
                usableConsistencyCache(forceRefresh = false)?.let { return@withLock it }
                fetchConsistencyFromNetwork()
            }
        }

    private fun usableConsistencyCache(forceRefresh: Boolean): Map<String, FundConsistency>? {
        if (forceRefresh || cachedConsistency.isEmpty()) return null
        val isFresh = System.currentTimeMillis() - lastConsistencyFetchEpochMs < CONSISTENCY_CACHE_TTL_MS
        return if (isFresh) cachedConsistency else null
    }

    private fun fetchConsistencyFromNetwork(): Map<String, FundConsistency> {
        val categoryIds = cachedFundMap.values
            .asSequence()
            .mapNotNull { it.categoryId?.trim()?.takeIf { id -> id.isNotEmpty() } }
            .toSet()

        if (categoryIds.isEmpty()) return cachedConsistency

        val merged = mutableMapOf<String, FundConsistency>()
        for (categoryId in categoryIds) {
            val body = postJson(
                CONSISTENCY_URL,
                """{"p_category_id":"$categoryId","p_months":null,"p_min_days":$CONSISTENCY_MIN_DAYS}"""
            ) ?: continue
            merged.putAll(parseConsistencyRows(body))
        }

        // یک دریافت ناموفق نباید کش سالم قبلی را پاک کند.
        if (merged.isEmpty()) return cachedConsistency

        cachedConsistency = merged
        lastConsistencyFetchEpochMs = System.currentTimeMillis()
        Log.d(TAG, "Loaded consistency scores for ${merged.size} funds across ${categoryIds.size} categories.")
        return merged
    }

    /**
     * ردیف‌های پاسخ را می‌خواند. `internal` است تا تست بتواند مستقیم صدایش
     * بزند؛ نگاشت نام فیلدها همان چیزی است که در اشتباه‌شدنش، ستون بی‌صدا
     * خالی می‌ماند.
     */
    internal fun parseConsistencyRows(body: String): Map<String, FundConsistency> = try {
        val arr = JSONArray(body.trim())
        val result = mutableMapOf<String, FundConsistency>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val fundId = o.optString("fund_id", "").trim()
            val score = o.optDoubleOrNull("consistency_score")
            if (fundId.isEmpty() || score == null) continue

            result[fundId] = FundConsistency(
                fundId = fundId,
                score = score,
                rankInCategory = o.optIntOrNull("rank_in_category"),
                totalFunds = o.optIntOrNull("total_funds"),
                sampleDays = o.optIntOrNull("sample_days")
            )
        }
        result
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing consistency rows: ${e.localizedMessage}")
        emptyMap()
    }

    /** امتیاز تداوم بازدهی یک صندوق؛ `null` یعنی FundBase برایش عددی نداده. */
    fun getConsistency(fundId: String?): FundConsistency? {
        if (fundId.isNullOrBlank()) return null
        return cachedConsistency[fundId.trim()]
    }

    // ---------------------------------------------------------------------
    // آمار ریسک (rpc/get_fund_risk_stats)
    // ---------------------------------------------------------------------

    private const val RISK_STATS_URL =
        "https://fyeguwhlfqhomqpkbgxb.supabase.co/rest/v1/rpc/get_fund_risk_stats"

    /** آمار ریسک روزی یک‌بار عوض می‌شود؛ شش ساعت کش کافی است. */
    private const val RISK_CACHE_TTL_MS = 6L * 60 * 60 * 1000

    @Volatile
    private var cachedRiskStats: Map<String, FundRiskStats> = emptyMap()

    @Volatile
    private var riskStatsFetchedAtMs: Map<String, Long> = emptyMap()

    private val riskMutex = Mutex()

    /**
     * آمار ریسک یک صندوق.
     *
     * **این تابع عمداً به‌ازای هر صندوق جدا صدا زده می‌شود و نه یک‌جا برای
     * همه.** خودِ RPC یک صندوق در هر فراخوانی می‌گیرد (`p_fund_id`)، پس
     * گرفتنش برای ۳۰۰ صندوق یعنی ۳۰۰ درخواست — دقیقاً همان چیزی که محافظ‌های
     * anti-ban اینجا قرار است جلویش را بگیرد. به‌جایش فقط وقتی صدا زده
     * می‌شود که کاربر کارت یک صندوق را باز کند، و نتیجه شش ساعت کش می‌ماند.
     *
     * در صورت هر خطایی `null` برمی‌گردد — هیچ عددی ساخته نمی‌شود.
     */
    suspend fun fetchRiskStats(
        fundId: String?,
        windowDays: Int = FundRiskStats.DEFAULT_WINDOW_DAYS,
        forceRefresh: Boolean = false
    ): FundRiskStats? = withContext(Dispatchers.IO) {
        val id = fundId?.trim().orEmpty()
        if (id.isEmpty()) return@withContext null

        usableRiskCache(id, forceRefresh)?.let { return@withContext it }

        riskMutex.withLock {
            usableRiskCache(id, forceRefresh = false)?.let { return@withLock it }

            val body = postJson(RISK_STATS_URL, """{"p_fund_id":"$id","p_window_days":$windowDays}""")
                ?: return@withLock null

            val stats = parseRiskStats(id, windowDays, body) ?: return@withLock null

            cachedRiskStats = cachedRiskStats + (id to stats)
            riskStatsFetchedAtMs = riskStatsFetchedAtMs + (id to System.currentTimeMillis())
            stats
        }
    }

    private fun usableRiskCache(fundId: String, forceRefresh: Boolean): FundRiskStats? {
        if (forceRefresh) return null
        val cached = cachedRiskStats[fundId] ?: return null
        val fetchedAt = riskStatsFetchedAtMs[fundId] ?: return null
        val isFresh = System.currentTimeMillis() - fetchedAt < RISK_CACHE_TTL_MS
        return if (isFresh) cached else null
    }

    /**
     * پاسخ RPC را می‌خواند.
     *
     * دو نکته که اگر رعایت نشوند عدد اشتباه نشان داده می‌شود:
     *
     * - PostgREST بسته به تعریف تابع، یا یک شیء می‌دهد یا آرایه‌ای با یک
     *   عضو. هر دو پذیرفته می‌شوند تا تغییر تعریف سمت سرور، این را نشکند.
     *
     * - **افت و نوسان کسری‌اند، شارپ نیست.** API `-0.2245` می‌دهد که یعنی
     *   ‎−۲۲.۴۵٪‎، ولی `sharpe: 4.71` همان ۴.۷۱ است. ضرب‌کردن شارپ در ۱۰۰،
     *   عددی می‌ساخت که هیچ معنایی ندارد.
     */
    private fun parseRiskStats(fundId: String, windowDays: Int, body: String): FundRiskStats? = try {
        val trimmed = body.trim()
        val obj = when {
            trimmed.startsWith("[") -> JSONArray(trimmed).optJSONObject(0)
            trimmed.startsWith("{") -> JSONObject(trimmed)
            else -> null
        }

        if (obj == null) {
            null
        } else {
            val stats = FundRiskStats(
                fundId = fundId,
                windowDays = windowDays,
                sampleSize = obj.optIntOrNull("sample_size"),
                maxDrawdownPct = obj.optDoubleOrNull("max_drawdown")?.times(100),
                sharpeRatio = obj.optDoubleOrNull("sharpe"),
                annualVolatilityPct = obj.optDoubleOrNull("annualized_volatility")?.times(100)
            )
            if (stats.hasAnyStat) stats else null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing risk stats for $fundId: ${e.localizedMessage}")
        null
    }

    /** یک POST با بدنه JSON و همان هدرهای مشترک. */
    private fun postJson(url: String, payload: String): String? {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("accept", "*/*")
                setRequestProperty("apikey", API_KEY)
                setRequestProperty("authorization", "Bearer $API_KEY")
                setRequestProperty("content-type", "application/json")
                setRequestProperty("content-profile", "public")
                setRequestProperty("Origin", "https://app.fundbase.ir")
                setRequestProperty("Referer", "https://app.fundbase.ir/")
            }

            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                429 -> {
                    Log.w(TAG, "Rate limited (429). Backing off 60s.")
                    backoffUntilEpochMs = System.currentTimeMillis() + 60_000L
                    null
                }
                else -> {
                    Log.w(TAG, "POST failed with HTTP $code: $url")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST error: ${e.localizedMessage}")
            null
        }
    }

    /**
     * یک GET ساده با همان هدرهای مشترک. بدنه پاسخ یا null در صورت خطا.
     * کد ۴۲۹ مثل بقیه مسیرها backoff می‌سازد.
     */
    private fun readJson(url: String): String? {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 12000
                setRequestProperty("accept", "*/*")
                setRequestProperty("apikey", API_KEY)
                setRequestProperty("authorization", "Bearer $API_KEY")
                setRequestProperty("accept-profile", "public")
                setRequestProperty("Origin", "https://app.fundbase.ir")
                setRequestProperty("Referer", "https://app.fundbase.ir/")
            }

            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                429 -> {
                    Log.w(TAG, "Rate limited (429). Backing off 60s.")
                    backoffUntilEpochMs = System.currentTimeMillis() + 60_000L
                    null
                }
                else -> {
                    Log.w(TAG, "Request failed with HTTP $code: $url")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request error: ${e.localizedMessage}")
            null
        }
    }

    // ---------------------------------------------------------------------
    // وضعیت تکنیکال واقعی (ta_symbol_state)
    // ---------------------------------------------------------------------

    @Volatile
    private var cachedTechnicalStates: Map<String, FundTechnicalState> = emptyMap()

    @Volatile
    private var lastTaFetchEpochMs: Long = 0L

    /** آخرین تاریخی که داده تکنیکال برایش محاسبه شده (از خود API) */
    @Volatile
    var technicalAsOfDate: String = ""
        private set

    /**
     * همه وضعیت‌های تکنیکال صندوق‌ها را در یک درخواست می‌گیرد.
     * در صورت شکست، نقشه خالی/کش قبلی برمی‌گردد — هیچ داده جایگزینی ساخته نمی‌شود.
     */
    suspend fun fetchAllTechnicalStates(forceRefresh: Boolean = false): Map<String, FundTechnicalState> =
        withContext(Dispatchers.IO) {
            usableTechnicalCache(forceRefresh)?.let { return@withContext it }

            technicalStatesMutex.withLock {
                usableTechnicalCache(forceRefresh = false)?.let { return@withLock it }
                fetchTechnicalStatesFromNetwork()
            }
        }

    /** کشِ قابل استفاده وضعیت تکنیکال، یا null اگر باید به شبکه بزنیم. */
    private fun usableTechnicalCache(forceRefresh: Boolean): Map<String, FundTechnicalState>? {
        val now = System.currentTimeMillis()
        if (now < backoffUntilEpochMs && cachedTechnicalStates.isNotEmpty()) {
            return cachedTechnicalStates
        }
        if (!forceRefresh && (now - lastTaFetchEpochMs < TA_CACHE_TTL_MS) && cachedTechnicalStates.isNotEmpty()) {
            return cachedTechnicalStates
        }
        return null
    }

    private fun fetchTechnicalStatesFromNetwork(): Map<String, FundTechnicalState> {
        val now = System.currentTimeMillis()

        return try {
                val connection = (URL(TA_STATE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 12000
                    setRequestProperty("accept", "*/*")
                    setRequestProperty("apikey", API_KEY)
                    setRequestProperty("authorization", "Bearer $API_KEY")
                    setRequestProperty("Origin", "https://app.fundbase.ir")
                    setRequestProperty("Referer", "https://app.fundbase.ir/")
                }

                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val arr = JSONArray(body)
                        val map = mutableMapOf<String, FundTechnicalState>()
                        var newestDate = ""

                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val key = o.optString("asset_key", "").trim()
                            if (key.isEmpty()) continue

                            val signal = o.optJSONObject("last_signal")
                            val lastDt = o.optString("last_dt", "")
                            if (lastDt > newestDate) newestDate = lastDt

                            map[key] = FundTechnicalState(
                                symbol = key,
                                hasData = true,
                                lastPrice = o.optDoubleOrNull("last_price"),
                                lastDate = lastDt,
                                trendDaily = o.optString("trend_d", ""),
                                trendWeekly = o.optString("trend_w", ""),
                                trendMonthly = o.optString("trend_m", ""),
                                rsi14 = o.optDoubleOrNull("rsi14_d"),
                                priceVsSma50Pct = o.optDoubleOrNull("price_vs_sma50_pct"),
                                priceVsSma200Pct = o.optDoubleOrNull("price_vs_sma200_pct"),
                                nearestSupport = o.optDoubleOrNull("nearest_support"),
                                nearestSupportDistPct = o.optDoubleOrNull("nearest_support_dist_pct"),
                                nearestResistance = o.optDoubleOrNull("nearest_resistance"),
                                nearestResistanceDistPct = o.optDoubleOrNull("nearest_resistance_dist_pct"),
                                stateLabel = o.optString("state_label", ""),
                                lastSignalDirection = signal?.optString("direction", "") ?: "",
                                lastSignalStrength = if (signal != null && !signal.isNull("strength")) {
                                    signal.optInt("strength")
                                } else null,
                                lastSignalType = signal?.optString("signal_type", "") ?: ""
                            )
                        }

                        cachedTechnicalStates = map
                        technicalAsOfDate = newestDate
                        lastTaFetchEpochMs = now
                        Log.d(TAG, "Loaded ${map.size} technical states from ta_symbol_state.")
                        map
                    }
                    429 -> {
                        Log.w(TAG, "ta_symbol_state rate limited (429). Backing off 60s.")
                        backoffUntilEpochMs = now + 60_000L
                        cachedTechnicalStates
                    }
                    else -> {
                        Log.w(TAG, "ta_symbol_state responded with HTTP ${connection.responseCode}")
                        cachedTechnicalStates
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching ta_symbol_state: ${e.localizedMessage}")
                cachedTechnicalStates
            }
        }

    /** JSON null را به null کاتلین تبدیل می‌کند تا 0.0 جعلی تولید نشود. */
    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        val v = optDouble(name, Double.NaN)
        return if (v.isNaN()) null else v
    }

    /** مثل بالا، برای عدد صحیح. نبودِ فیلد یعنی null، نه صفر. */
    private fun JSONObject.optIntOrNull(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        val v = optDouble(name, Double.NaN)
        return if (v.isNaN()) null else v.toInt()
    }

    /**
     * وضعیت تکنیکال یک نماد. اگر نباشد، شیئی با `hasData = false` برمی‌گردد
     * تا UI بتواند «داده در دسترس نیست» نشان دهد.
     */
    fun getTechnicalState(symbol: String): FundTechnicalState {
        val trimmed = symbol.trim()
        cachedTechnicalStates[trimmed]?.let { return it }

        val normalized = normalizePersian(trimmed)
        for ((key, state) in cachedTechnicalStates) {
            if (normalizePersian(key) == normalized) return state
        }
        return FundTechnicalState(symbol = trimmed, hasData = false)
    }

    /**
     * Returns cached or immediate lookup for a given symbol with comprehensive Persian normalization.
     */
    fun getCachedFund(symbol: String): FundBaseLiveData? {
        val trimmed = symbol.trim()
        val direct = cachedFundMap[trimmed]
        if (direct != null) return direct

        // Normalize ZWNJ, spaces, Arabic kaf/yeh
        val normalized = normalizePersian(trimmed)
        for ((key, data) in cachedFundMap) {
            if (normalizePersian(key) == normalized) {
                return data
            }
        }

        // نام‌های جایگزینِ همان صندوق.
        // توجه: «سپهر» (مختلط) و «سپر» (درآمد ثابت) دو صندوق متفاوت‌اند و
        // نباید به هم نگاشت شوند — نگاشت قبلی قیمت نماد اشتباه برمی‌گرداند.
        val alias = when (trimmed) {
            "دارایکم" -> "دارا یکم"
            "دارا یکم" -> "دارایکم"
            else -> null
        }
        if (alias != null) {
            return cachedFundMap[alias]
        }

        return null
    }

    fun normalizePersian(text: String): String {
        return text.trim()
            .replace("‌", "") // Remove ZWNJ
            .replace(" ", "")  // Remove spaces
            .replace("ي", "ی") // Arabic yeh to Persian
            .replace("ك", "ک") // Arabic kaf to Persian
            .replace("آ", "ا") // Alif with madda to regular alif
    }

    /**
     * Map FundBase official category_id UUIDs to PCMR AssetCategory.
     */
    fun mapFundBaseCategory(categoryId: String?, symbol: String = ""): AssetCategory {
        return when (categoryId) {
            // Gold / Commodities (طلا، نقره، کالایی)
            "b4096a4d-122e-4bf3-821b-7ce484d1cb5b", // طلا
            "f5827124-ec66-4c4d-b648-1ad4cd100411", // نقره
            "e6e707d2-c6a0-4c97-806b-2282d8cc475b"  // در اوراق بهادار مبتنی بر سپرده کالایی
            -> AssetCategory.GOLD

            // Mixed (مختلط)
            "bced63f2-638f-4b75-a49d-5d5d19fa50ff" // مختلط
            -> AssetCategory.MIXED

            // Fixed Income (درآمد ثابت، با تضمین اصل سرمایه)
            "e26fe476-bb85-4994-a35c-aec2fe36fcd2", // در اوراق بهادار با درآمد ثابت
            "4dd0f0db-05ed-42bb-a167-478221865e54"  // با تضمین اصل مبلغ سرمایه گذاری
            -> AssetCategory.FIXED_INCOME

            // Equity & Leveraged & Sector & Index (سهامی عادی، بخشی، شاخصی، اهرمی، جسورانه، املاک و...)
            "37345721-b243-4e9a-80d8-c9b03b558615", // در سهام (سهامی عادی)
            "39963705-9e8e-4a5d-9749-be751c4d08b7", // در سهام-بخشی
            "26eb984b-7ce0-4837-9e95-db0a68b83594", // در سهام-سهامی اهرمی
            "49bfbe45-6702-45bc-9680-7d701febdc2a", // در سهام-شاخصی
            "3bc39697-060c-4a9d-9e84-d52530933054", // جسورانه
            "4b792ea2-4f02-44da-a8da-6a5a805f1bee", // املاک و مستغلات
            "c08c3947-8c7b-4c13-b63d-3f947e9480bb", // زمین و ساختمان
            "ebd91877-1224-4e50-bb42-9b6a2751a391"  // صندوق در صندوق
            -> AssetCategory.STOCK

            else -> com.example.data.parser.DefaultEtfDatabase.getCategoryForSymbol(symbol)
        }
    }

    /**
     * Checks whether the fund is strictly a leveraged stock ETF according to FundBase category_id or symbol name.
     */
    fun isFundBaseLeveraged(categoryId: String?, symbol: String): Boolean {
        if (categoryId == "26eb984b-7ce0-4837-9e95-db0a68b83594") { // در سهام-سهامی اهرمی
            return true
        }
        return com.example.data.parser.DefaultEtfDatabase.isLeveragedFund(symbol)
    }

    /**
     * قیمت روز نمادها را از داده زنده به‌روز می‌کند.
     * قاعده‌اش در [PortfolioPriceSync] است تا بدون شبکه و اندروید تست شود.
     */
    fun syncPortfolioPrices(
        portfolio: List<PortfolioEntity>,
        revalueFromQuantity: Boolean
    ): PriceSyncResult = PortfolioPriceSync.apply(
        portfolio = portfolio,
        revalueFromQuantity = revalueFromQuantity,
        priceLookup = { symbol ->
            getCachedFund(symbol)?.currentPriceRial?.takeIf { it > 0.0 }
        }
    )

    private val cachedTechnicalMap = ConcurrentHashMap<String, FundTechnicalInsight>()

    /**
     * Fetches technical analysis insights for a given fund from FundBase:
     * - Trend in multiple timeframes: Daily (روزانه), Weekly (هفتگی), Monthly (ماهانه)
     * - Short summary ("جمع‌بندی کوتاه") from FundBase AI
     * 
     * If not in database or offline, intelligently synthesizes a high-accuracy summary
     * based on the symbol's actual metrics and category.
     */
    suspend fun fetchFundTechnicalInsight(
        symbol: String,
        fallbackCategory: AssetCategory? = null,
        fallbackBubblePercent: Double = 0.0,
        fallbackPriceChangePercent: Double = 0.0
    ): FundTechnicalInsight = withContext(Dispatchers.IO) {
        val trimmed = symbol.trim()
        if (trimmed.isEmpty()) {
            return@withContext FundTechnicalInsight(symbol = symbol)
        }

        // 1. Check in-memory cache
        cachedTechnicalMap[trimmed]?.let { return@withContext it }

        // ۲. روندها از کش گروهی ta_symbol_state خوانده می‌شوند
        //    (قبلاً به‌ازای هر نماد یک درخواست جدا زده می‌شد).
        val state = getTechnicalState(trimmed)
        var trendDaily = state.trendDaily.ifBlank { "" }
        var trendWeekly = state.trendWeekly.ifBlank { "" }
        val trendMonthly = state.trendMonthly.ifBlank { "" }
        val rsiDaily: Double? = state.rsi14
        var summaryText = ""
        var isFromLiveApi = state.hasData

        val candidates = listOf(
            trimmed,
            trimmed.replace(" ", ""),
            trimmed.replace("دارایکم", "دارا یکم")
        ).distinct()

        // 3. Query Supabase ta_ai_interpretations
        for (candidate in candidates) {
            try {
                val enc = URLEncoder.encode(candidate, "UTF-8")
                val interpUrl = URL("https://fyeguwhlfqhomqpkbgxb.supabase.co/rest/v1/ta_ai_interpretations?asset_key=eq.$enc&select=asset_key,payload")
                val conn = (interpUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("apikey", API_KEY)
                    setRequestProperty("authorization", "Bearer $API_KEY")
                    setRequestProperty("Origin", "https://app.fundbase.ir")
                    setRequestProperty("Referer", "https://app.fundbase.ir/")
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        val payload = obj.optJSONObject("payload")
                        val fetchedSummary = payload?.optString("summary", "")?.trim() ?: ""
                        if (fetchedSummary.isNotEmpty() && !fetchedSummary.contains("داده‌های آماری و تکنیکال برای نماد")) {
                            summaryText = fetchedSummary
                            isFromLiveApi = true
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching ta_ai_interpretations for $candidate: ${e.message}")
            }
        }

        // ۴. اگر FundBase جمع‌بندی نداشت، فقط از داده واقعیِ همین نماد توصیف می‌سازیم.
        //    وقتی هیچ داده تکنیکالی نیست، صراحتاً اعلام می‌شود — روند حدس زده نمی‌شود.
        if (summaryText.isEmpty()) {
            summaryText = if (!state.hasData) {
                "برای نماد $trimmed داده تکنیکالی در FundBase موجود نیست. " +
                        "تنها اطلاعات قابل اتکا برای این نماد، قیمت، NAV و حباب لحظه‌ای است."
            } else {
                val parts = mutableListOf<String>()

                if (state.stateLabel.isNotBlank()) {
                    parts.add("وضعیت اعلامی: ${state.stateLabel}.")
                }

                val timeframes = mutableListOf<String>()
                if (trendDaily.isNotBlank()) timeframes.add("روزانه ${FundTechnicalInsight.trendLabel(trendDaily)}")
                if (trendWeekly.isNotBlank()) timeframes.add("هفتگی ${FundTechnicalInsight.trendLabel(trendWeekly)}")
                if (trendMonthly.isNotBlank()) timeframes.add("ماهانه ${FundTechnicalInsight.trendLabel(trendMonthly)}")
                if (timeframes.isNotEmpty()) {
                    parts.add("روند در بازه‌های زمانی: ${timeframes.joinToString("، ")}.")
                }

                state.priceVsSma200Pct?.let {
                    parts.add("قیمت ${String.format(Locale.US, "%.1f", it)}٪ نسبت به میانگین ۲۰۰ روزه فاصله دارد.")
                }
                state.rsi14?.let {
                    parts.add("RSI برابر ${String.format(Locale.US, "%.0f", it)} (${state.rsiLabel}).")
                }
                state.nearestSupportDistPct?.let {
                    parts.add("نزدیک‌ترین حمایت ${String.format(Locale.US, "%.1f", abs(it))}٪ پایین‌تر است.")
                }
                if (state.lastDate.isNotBlank()) {
                    parts.add("داده تا تاریخ ${state.lastDate}.")
                }

                parts.joinToString(" ")
            }
        }

        val result = FundTechnicalInsight(
            symbol = trimmed,
            trendDaily = trendDaily,
            trendWeekly = trendWeekly,
            trendMonthly = trendMonthly,
            rsiDaily = rsiDaily,
            summary = summaryText,
            isLiveApi = isFromLiveApi,
            isLoading = false,
            lastUpdated = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        )

        cachedTechnicalMap[trimmed] = result
        result
    }
}
