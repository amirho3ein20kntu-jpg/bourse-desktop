package com.example.data.remote

import com.example.platform.AppLog as Log
import com.example.domain.market.CbiInflationParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * نرخ تورم نقطه‌به‌نقطه از صفحه عمومی بانک مرکزی.
 *
 * **این سایت فقط از IP ایران باز می‌شود** (حتی فیلترشکن هم جواب نمی‌دهد؛
 * تأیید Amir در ۲۸ سپتامبر ۲۰۲۶). پس این سرویس فقط وقتی که خودِ اپ روی
 * گوشی کاربر (با IP ایران) اجرا می‌شود کار می‌کند — نه از محیط توسعه.
 *
 * صفحه بدون پارامتر، جدول سال جاری (سالی که در `<select>` پیش‌فرض انتخاب
 * شده) را برمی‌گرداند؛ برای سال‌های قبلی باید postback شبیه‌سازی شود که
 * فعلاً پیاده نشده — کاربر آن ماه‌ها را دستی وارد می‌کند.
 */
object CbiInflationApiService {

    private const val TAG = "CbiInflationApiService"
    private const val URL_STRING = "https://cbi.ir/Inflation/Inflation_FA.aspx"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    suspend fun fetchCurrentYearTable(): CbiInflationParser.ParsedPage? =
        withContext(Dispatchers.IO) {
            val html = getHtml() ?: return@withContext null
            val parsed = CbiInflationParser.parsePage(html)
            if (parsed.year == null || parsed.rows.isEmpty()) {
                Log.w(TAG, "صفحه cbi.ir دریافت شد ولی جدول سال/ماه پارس نشد.")
                return@withContext null
            }
            parsed
        }

    private fun getHtml(): String? = try {
        val connection = (URL(URL_STRING).openConnection() as HttpURLConnection).apply {
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
            Log.w(TAG, "cbi.ir HTTP $code")
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "خطا در دریافت نرخ تورم از cbi.ir: ${e.localizedMessage}")
        null
    }
}
