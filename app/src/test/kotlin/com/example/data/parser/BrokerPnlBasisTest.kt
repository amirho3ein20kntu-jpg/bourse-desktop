package com.example.data.parser

import com.example.domain.portfolio.PortfolioPnl
import com.example.domain.sync.PortfolioPriceSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * محافظ عددی روی خروجی واقعی کارگزاری.
 *
 * سربرگ‌ها و ارقام این تست عیناً از یک فایل واقعی کاربر آمده‌اند، از جمله ستون
 * «سود و زیان فعلی» خود کارگزاری که مرجع درستی ماست. سه اشکالی که این تست جلوی
 * برگشتشان را می‌گیرد:
 *
 *  ۱. انتخاب ستون «سر به سر» به‌جای «میانگین خرید با لحاظ کارمزد». ستون
 *     «ارزش فعلی» کارمزد فروش را از قبل کم کرده و «سر به سر» هم همان کارمزد را
 *     در خود دارد، پس کم کردن این دو از هم کارمزد را دو بار می‌شمرد و زیان
 *     کاربر را روی همین فایل از ۱ میلیون تومان به ۲.۶ میلیون تومان می‌برد.
 *  ۲. جمع زدن ارزش همه ردیف‌ها در برابر اصل سرمایه‌ی فقط بعضی ردیف‌ها.
 *  ۳. بازمحاسبه با قیمت زنده بدون کارمزد فروش، که عدد را بدون هیچ تکان بازار
 *     جابه‌جا می‌کرد.
 */
class BrokerPnlBasisTest {

    /** سربرگ و هشت ردیف واقعی؛ ستون‌های بی‌ربط خالی گذاشته شده‌اند. */
    private val rows: List<List<String>> = listOf(
            listOf("نام نماد", "", "تعداد دارایی", "", "", "ارزش فعلی", "آخرین قیمت", "", "", "", "", "", "", "", "سود و زیان فعلی", "", "قیمت سر به سر بر حسب میانگین خرید در آخرین دوره", "", "قیمت میانگین خرید در آخرین دوره با لحاظ کارمزد"),
            listOf("اطلس", "", "6303", "", "", "978491082.36", "155600", "", "", "", "", "", "", "", "-21513796.68900001", "", "159021.132", "", "158655.383"),
            listOf("آگاس", "", "734", "", "", "362641535.6718", "495201", "", "", "", "", "", "", "", "-29811865.300200045", "", "535910.252", "", "534677.658"),
            listOf("افران", "", "18808", "", "", "1012456127.08205", "53851", "", "", "", "", "", "", "", "3392332.0740499496", "", "53670.567", "", "53650.776"),
            listOf("عیار", "", "949", "", "", "634108716.3268", "668989", "", "", "", "", "", "", "", "27375101.878799915", "", "640108.082", "", "639339.952"),
            listOf("زیتون", "", "23197", "", "", "1012197415.375875", "43690", "", "", "", "", "", "", "", "-682075.8891251087", "", "43719.441", "", "43664.245"),
            listOf("درخشان", "", "71861", "", "", "3115742626.788", "43410", "", "", "", "", "", "", "", "165906065.5880003", "", "41098.518", "", "41049.2"),
            listOf("غزنجان", "", "110", "", "", "800294.88", "7340", "", "", "", "", "", "", "", "35389.859999999986", "", "7015.418", "", "6953.682"),
            listOf("سهامدار", "", "98357", "", "", "2877292568.1269", "29321", "", "", "", "", "", "", "", "-154702607.38310003", "", "30897.494", "", "30826.43")
    )

    /** عددی که خود کارگزاری در ستون «سود و زیان فعلی» گزارش می‌کند (ریال). */
    private val brokerReportedPnlRial = -10_001_455.86

    @Test
    fun `ستون میانگین خرید با کارمزد بر سر به سر ترجیح داده می‌شود`() {
        val items = ExcelAndCsvParser.extractPortfolioItems(rows, emptyMap())
        val atlas = items.first { it.symbol == "اطلس" }

        // ستون S، نه ستون Q که ۱۵۹۰۲۱.۱۳۲ است
        assertEquals(158_655.383, atlas.averagePrice, 0.001)
    }

    @Test
    fun `سود سبد با عدد خود کارگزاری یکی است`() {
        val items = ExcelAndCsvParser.extractPortfolioItems(rows, emptyMap())

        assertEquals(8, items.size)
        assertEquals(brokerReportedPnlRial, PortfolioPnl.calculate(items).profitRial, 1.0)
    }

    @Test
    fun `نرخ کارمزد فروش از خود فایل در می‌آید و برای هر نماد فرق دارد`() {
        val items = ExcelAndCsvParser.extractPortfolioItems(rows, emptyMap())

        // صندوق سهامی حدود ۰.۲۳٪
        assertEquals(0.9977, items.first { it.symbol == "اطلس" }.netValueRatio, 0.0001)
        // درآمد ثابت حدود ۰.۰۴٪
        assertEquals(0.99963, items.first { it.symbol == "افران" }.netValueRatio, 0.0001)
        // سهم عادی حدود ۰.۸۸٪
        assertEquals(0.9912, items.first { it.symbol == "غزنجان" }.netValueRatio, 0.0001)
    }

    @Test
    fun `رفرش قیمت با همان قیمت‌ها سود را جابه‌جا نمی‌کند`() {
        // پارسر همه ردیف‌ها را با شناسه صفر می‌سازد (Room بعداً شناسه می‌دهد)،
        // پس برای این تست شناسه‌ها را خودمان می‌گذاریم.
        val items = ExcelAndCsvParser.extractPortfolioItems(rows, emptyMap())
            .mapIndexed { index, item -> item.copy(id = index + 1L) }
        val livePrices = items.associate { it.symbol to it.lastPrice }

        val synced = PortfolioPriceSync.apply(
            portfolio = items,
            revalueFromQuantity = true,
            priceLookup = { livePrices[it] }
        )

        // هر ردیفی که تغییر کرده جایگزین می‌شود، بقیه دست‌نخورده می‌مانند
        val changedById = synced.items.associateBy { it.id }
        val after = items.map { changedById[it.id] ?: it }

        assertEquals(
            brokerReportedPnlRial,
            PortfolioPnl.calculate(after).profitRial,
            // یک ریال؛ یعنی فقط خطای گِرد کردن ممیز شناور مجاز است
            1.0
        )
    }

    @Test
    fun `فایلی که فقط ستون سر به سر دارد همچنان کار می‌کند`() {
        // ستون میانگین خرید با کارمزد را حذف می‌کنیم
        val narrowed = rows.map { it.take(18) }
        val items = ExcelAndCsvParser.extractPortfolioItems(narrowed, emptyMap())

        assertEquals(8, items.size)
        assertEquals(159_021.132, items.first { it.symbol == "اطلس" }.averagePrice, 0.001)
    }

    @Test
    fun `سربرگ‌ها درست امتیاز می‌گیرند`() {
        val withFee = ExcelAndCsvParser
            .scoreAveragePriceHeader("قیمت میانگین خرید در آخرین دوره با لحاظ کارمزد")
        val breakEven = ExcelAndCsvParser
            .scoreAveragePriceHeader("قیمت سر به سر بر حسب میانگین خرید در آخرین دوره")
        val unadjusted = ExcelAndCsvParser.scoreAveragePriceHeader(
            "قیمت میانگین خرید در آخرین دوره تعدیل نشده توسط سود نقدی مجمع با لحاظ کارمزد"
        )

        assertTrue(withFee!! > breakEven!!)
        assertTrue(withFee > unadjusted!!)
        // ستون‌های فروش و ستون‌های بی‌ربط اصلاً نامزد نیستند
        assertEquals(null, ExcelAndCsvParser.scoreAveragePriceHeader("قیمت میانگین کل فروش با لحاظ کارمزد"))
        assertEquals(null, ExcelAndCsvParser.scoreAveragePriceHeader("ارزش فعلی"))
    }
}
