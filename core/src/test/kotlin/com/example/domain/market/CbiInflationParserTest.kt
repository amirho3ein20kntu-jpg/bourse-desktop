package com.example.domain.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CbiInflationParserTest {

    /**
     * برش واقعی از HTML صفحه `cbi.ir/Inflation/Inflation_FA.aspx` که Amir در
     * ۲۸ سپتامبر ۲۰۲۶ فرستاد (ASP.NET WebForms، جدول «نرخ تورم منتهی به ماه»
     * برای سال پیش‌فرض انتخاب‌شده در dropdown، به‌علاوه جدول دوم «نرخ تورم
     * سالانه» که کلید ستون اولش سال است نه نام ماه).
     */
    private val realPageSnippet = """
        <select name="ctl00${'$'}ucBody${'$'}ucContent${'$'}ctl00${'$'}ddlYear" id="ctl00_ucBody_ucContent_ctl00_ddlYear" class="form-control">
	<option value="1403">۱۴۰۳</option>
	<option value="1404">۱۴۰۴</option>
	<option selected="selected" value="1405">۱۴۰۵</option>

</select>
        <table class="table">
            <tbody><tr style="background-color: #2470ab; color: white">
                <td>ماه</td>
                <td>نرخ تورم</td>
            </tr>
            <tr>
                <td nowrap="">
                    شهریور
                </td>
                <td class="DirLtr">
                    ۶۸.۴
                </td>
            </tr>
            <tr>
                <td nowrap="">
                    مرداد
                </td>
                <td class="DirLtr">
                    ۶۵.۱
                </td>
            </tr>
            <tr>
                <td nowrap="">
                    تیر
                </td>
                <td class="DirLtr">
                    ۶۱.۴
                </td>
            </tr>
            <tr>
                <td nowrap="">
                    خرداد
                </td>
                <td class="DirLtr">
                    ۵۷.۷
                </td>
            </tr>
            <tr>
                <td nowrap="">
                    اردیبهشت
                </td>
                <td class="DirLtr">
                    ۵۳.۹
                </td>
            </tr>
            <tr>
                <td nowrap="">
                    فروردین
                </td>
                <td class="DirLtr">
                    ۵۰.۶
                </td>
            </tr>
            </tbody></table>

        <h4>نرخ تورم سالانه</h4>
        <table class="table">
            <tbody><tr style="background-color: #2470ab; color: white">
                <td>سال</td>
                <td>نرخ تورم</td>
            </tr>
            <tr>
                <td nowrap="">۱۴۰۴</td>
                <td class="DirLtr">۴۸.۳</td>
            </tr>
            <tr>
                <td nowrap="">1401(تغییر سال پایه)</td>
                <td class="DirLtr">۵۳.۱</td>
            </tr>
            </tbody></table>
    """.trimIndent()

    @Test
    fun parsePage_readsSelectedYearFromDropdown() {
        val result = CbiInflationParser.parsePage(realPageSnippet)
        assertEquals(1405, result.year)
    }

    @Test
    fun parsePage_readsAllSixMonthlyRows() {
        val result = CbiInflationParser.parsePage(realPageSnippet)
        assertEquals(6, result.rows.size)
    }

    @Test
    fun parsePage_mapsMonthNameToJalaliMonthIndexAndRate() {
        val result = CbiInflationParser.parsePage(realPageSnippet)
        val shahrivar = result.rows.first { it.jalaliMonth == 6 }
        assertEquals(1405, shahrivar.jalaliYear)
        assertEquals(68.4, shahrivar.ratePercent, 0.001)

        val farvardin = result.rows.first { it.jalaliMonth == 1 }
        assertEquals(50.6, farvardin.ratePercent, 0.001)
    }

    @Test
    fun parsePage_ignoresTheAnnualTableKeyedByYearNotMonth() {
        // ستون اول جدول دوم عدد سال است («۱۴۰۴»)، نه نام ماه — نباید به‌عنوان
        // ردیف ماهانه اشتباه پارس شود.
        val result = CbiInflationParser.parsePage(realPageSnippet)
        assertTrue(result.rows.none { it.ratePercent == 48.3 || it.ratePercent == 53.1 })
    }

    @Test
    fun parsePage_missingYearSelectionReturnsNoRows() {
        val noSelect = "<table><tr><td>شهریور</td><td>۶۸.۴</td></tr></table>"
        val result = CbiInflationParser.parsePage(noSelect)
        assertNull(result.year)
        assertTrue(result.rows.isEmpty())
    }

    @Test
    fun extractSignedNumber_handlesPersianDigitsAndNegativeRates() {
        assertEquals(68.4, CbiInflationParser.extractSignedNumber("۶۸.۴")!!, 0.001)
        assertEquals(-2.1, CbiInflationParser.extractSignedNumber("-۲.۱")!!, 0.001)
    }

    @Test
    fun extractSelectedYear_readsAttributeOrderRegardlessOfSpacing() {
        val html = """<option value="1405" selected="selected">۱۴۰۵</option>"""
        assertEquals(1405, CbiInflationParser.extractSelectedYear(html))
    }
}
