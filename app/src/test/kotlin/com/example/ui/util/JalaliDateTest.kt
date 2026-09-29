package com.example.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JalaliDateTest {

    @Test
    fun `نوروز و اول مهر درست تبدیل می‌شوند`() {
        assertEquals(JalaliDate.Date(1405, 1, 1), JalaliDate.fromGregorian(2026, 3, 21))
        assertEquals(JalaliDate.Date(1404, 1, 1), JalaliDate.fromGregorian(2025, 3, 21))
        // ۱۴۰۳ سال پس از کبیسه است و نوروزش ۲۰ مارس بود
        assertEquals(JalaliDate.Date(1403, 1, 1), JalaliDate.fromGregorian(2024, 3, 20))
        assertEquals(JalaliDate.Date(1405, 7, 1), JalaliDate.fromGregorian(2026, 9, 23))
        assertEquals(JalaliDate.Date(1404, 12, 29), JalaliDate.fromGregorian(2026, 3, 20))
    }

    @Test
    fun `قالب نمایش با رقم فارسی`() {
        assertEquals("۱۴۰۵/۰۷/۰۵", JalaliDate.formatShort("2026-09-27"))
        assertEquals("۵ مهر ۱۴۰۵", JalaliDate.formatLong("2026-09-27"))
    }

    @Test
    fun `ورودی نامعتبر همان‌طور برمی‌گردد`() {
        assertNull(JalaliDate.fromIsoDay("27/09/2026"))
        assertEquals("نامعلوم", JalaliDate.formatShort("نامعلوم"))
    }
}
