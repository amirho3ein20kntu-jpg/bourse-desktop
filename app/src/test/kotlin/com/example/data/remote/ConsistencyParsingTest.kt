package com.example.data.remote

import com.example.data.model.FundConsistency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست خواندن پاسخ `rpc/get_fund_return_consistency`.
 *
 * نمونه‌ها عیناً همان چیزی است که از FundBase گرفته شد — نه JSON ساختگی.
 * نگاشت نام فیلدها جایی است که اشتباهش، ستون را بی‌صدا خالی می‌کند.
 */
class ConsistencyParsingTest {

    /** ردیف واقعی، کپی‌شده از پاسخ سرویس. */
    private val realRow = """
        [{
            "fund_id": "8763928c-b766-4284-83ea-13fe31af2bea",
            "category_id": "26eb984b-7ce0-4837-9e95-db0a68b83594",
            "consistency_score": 80.5,
            "rank_in_category": 1,
            "total_funds": 9,
            "sample_days": 226
        }]
    """.trimIndent()

    @Test
    fun readsEveryFieldOfTheRealResponse() {
        val parsed = FundBaseApiService.parseConsistencyRows(realRow)
        val row = parsed.getValue("8763928c-b766-4284-83ea-13fe31af2bea")
        assertEquals(80.5, row.score, 1e-9)
        assertEquals(1, row.rankInCategory)
        assertEquals(9, row.totalFunds)
        assertEquals(226, row.sampleDays)
    }

    @Test
    fun theDisplayedScoreMatchesWhatTheSiteShows() {
        // سایت برای همین صندوق «۸۱» نشان می‌دهد.
        val parsed = FundBaseApiService.parseConsistencyRows(realRow)
        assertEquals(81, parsed.getValue("8763928c-b766-4284-83ea-13fe31af2bea").displayScore)
    }

    @Test
    fun keyedByFundId() {
        val parsed = FundBaseApiService.parseConsistencyRows(realRow)
        assertEquals(setOf("8763928c-b766-4284-83ea-13fe31af2bea"), parsed.keys)
    }

    @Test
    fun readsEveryRowOfAMultiFundResponse() {
        val body = """
            [
              {"fund_id":"a","consistency_score":80.5,"rank_in_category":1,"total_funds":3,"sample_days":226},
              {"fund_id":"b","consistency_score":63.1,"rank_in_category":2,"total_funds":3,"sample_days":200},
              {"fund_id":"c","consistency_score":21.9,"rank_in_category":3,"total_funds":3,"sample_days":180}
            ]
        """.trimIndent()
        val parsed = FundBaseApiService.parseConsistencyRows(body)
        assertEquals(3, parsed.size)
        assertEquals(81, parsed.getValue("a").displayScore)
        assertEquals(63, parsed.getValue("b").displayScore)
        assertEquals(22, parsed.getValue("c").displayScore)
    }

    @Test
    fun rowWithoutAFundIdIsSkipped() {
        val body = """[{"consistency_score":50.0},{"fund_id":"a","consistency_score":50.0}]"""
        assertEquals(setOf("a"), FundBaseApiService.parseConsistencyRows(body).keys)
    }

    @Test
    fun rowWithoutAScoreIsSkipped_notScoredAsZero() {
        val body = """[{"fund_id":"a","consistency_score":null,"rank_in_category":1}]"""
        assertTrue(FundBaseApiService.parseConsistencyRows(body).isEmpty())
    }

    @Test
    fun missingOptionalFieldsAreNull_notZero() {
        val body = """[{"fund_id":"a","consistency_score":50.0}]"""
        val row = FundBaseApiService.parseConsistencyRows(body).getValue("a")
        assertNull(row.rankInCategory)
        assertNull(row.totalFunds)
        assertNull(row.sampleDays)
        assertNull(row.rankLabel)
    }

    @Test
    fun emptyArrayYieldsNothing() {
        assertTrue(FundBaseApiService.parseConsistencyRows("[]").isEmpty())
    }

    @Test
    fun malformedBodyYieldsNothing_ratherThanThrowing() {
        assertTrue(FundBaseApiService.parseConsistencyRows("not json").isEmpty())
        assertTrue(FundBaseApiService.parseConsistencyRows("").isEmpty())
        assertTrue(FundBaseApiService.parseConsistencyRows("""{"error":"boom"}""").isEmpty())
    }

    // ---------- مدل ----------

    @Test
    fun rankLabelReadsAsTheSiteWouldPhraseIt() {
        val c = FundConsistency("a", 80.5, rankInCategory = 3, totalFunds = 9)
        assertEquals("3 از 9", c.rankLabel)
    }

    @Test
    fun labelBandsFollowTheSitesOwnColouring() {
        // در نمونه واقعی: ۸۱ سبز پررنگ، ۵۰ تا ۶۳ سبز کم‌رنگ، ۴۰ و ۴۳ زرد،
        // ۲۲ و ۲۸ نارنجی.
        assertEquals("پایدار", FundConsistency("a", 80.5).label)
        assertEquals("نسبتاً پایدار", FundConsistency("a", 63.1).label)
        assertEquals("نسبتاً پایدار", FundConsistency("a", 50.1).label)
        assertEquals("متوسط", FundConsistency("a", 42.9).label)
        assertEquals("متوسط", FundConsistency("a", 30.0).label)
        assertEquals("ناپایدار", FundConsistency("a", 28.4).label)
        assertEquals("ناپایدار", FundConsistency("a", 21.9).label)
    }

    @Test
    fun shortHistoryIsFlagged_butOnlyWhenItIsKnown() {
        assertTrue(FundConsistency("a", 50.0, sampleDays = 45).isHistoryShort)
        assertTrue(!FundConsistency("a", 50.0, sampleDays = 226).isHistoryShort)
        assertTrue(!FundConsistency("a", 50.0, sampleDays = null).isHistoryShort)
    }

    @Test
    fun displayScoreStaysWithinZeroAndHundred() {
        assertEquals(0, FundConsistency("a", -12.0).displayScore)
        assertEquals(100, FundConsistency("a", 140.0).displayScore)
    }
}
