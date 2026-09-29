package com.example.data.remote

import com.example.platform.AppLog as Log
import com.example.data.model.AlanchandQuotes
import com.example.domain.market.AlanchandParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * قیمت طلا، سکه و دلار آزاد از صفحات عمومی alanchand.com.
 *
 * این سایت API رسمی مستندی برای این دو صفحه ندارد؛ این سرویس صفحه HTML را
 * می‌گیرد و [AlanchandParser] جدول قیمت را از آن بیرون می‌کشد — همان‌طور که
 * مرورگر کاربر می‌بیند. اگر سایت طراحی صفحه را عوض کند، پارسر مقدار `null`
 * برمی‌گرداند، نه عدد غلط.
 */
object AlanchandApiService {

    private const val TAG = "AlanchandApiService"
    private const val GOLD_URL = "https://alanchand.com/gold-price"
    private const val CURRENCY_URL = "https://alanchand.com/currencies-price"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    /** قیمت این صفحات معمولاً هر چند دقیقه یک‌بار به‌روز می‌شود؛ کش ۵ دقیقه‌ای کافی است. */
    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    @Volatile
    private var cached: AlanchandQuotes? = null

    private val mutex = Mutex()

    private fun usableCache(forceRefresh: Boolean): AlanchandQuotes? {
        val current = cached ?: return null
        if (forceRefresh) return null
        val age = System.currentTimeMillis() - current.fetchedAtEpochMs
        return if (age < CACHE_TTL_MS) current else null
    }

    suspend fun fetchQuotes(forceRefresh: Boolean = false): AlanchandQuotes? =
        withContext(Dispatchers.IO) {
            usableCache(forceRefresh)?.let { return@withContext it }

            mutex.withLock {
                usableCache(forceRefresh = false)?.let { return@withLock it }
                fetchFromNetwork()
            }
        }

    /** آخرین نتیجه دریافت‌شده، یا `null` اگر هنوز هیچ درخواست موفقی نبوده. */
    fun getCached(): AlanchandQuotes? = cached

    private fun fetchFromNetwork(): AlanchandQuotes? {
        val goldHtml = getHtml(GOLD_URL)
        val currencyHtml = getHtml(CURRENCY_URL)

        if (goldHtml == null && currencyHtml == null) {
            Log.w(TAG, "هیچ‌کدام از صفحات آلان‌چند دریافت نشد.")
            return cached
        }

        val gold = goldHtml?.let { AlanchandParser.parseGoldPage(it) }
        val currency = currencyHtml?.let { AlanchandParser.parseCurrencyPage(it) }

        val result = AlanchandQuotes(
            gram18Toman = gold?.gram18Toman,
            quarterCoinToman = gold?.quarterCoinToman,
            halfCoinToman = gold?.halfCoinToman,
            fullCoinToman = gold?.fullCoinToman,
            usdSellToman = currency?.usdSellToman,
            goldUpdateText = gold?.updateText,
            currencyUpdateText = currency?.updateText,
            fetchedAtEpochMs = System.currentTimeMillis()
        )

        if (result.hasAnyValue) {
            cached = result
            return result
        }

        Log.w(TAG, "صفحات آلان‌چند دریافت شد ولی هیچ قیمتی از جدول پارس نشد.")
        return cached
    }

    private fun getHtml(urlString: String): String? = try {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
        }
        val code = connection.responseCode
        if (code == HttpURLConnection.HTTP_OK) {
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
        } else {
            Log.w(TAG, "آلان‌چند HTTP $code برای $urlString")
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "خطا در دریافت $urlString: ${e.localizedMessage}")
        null
    }
}
