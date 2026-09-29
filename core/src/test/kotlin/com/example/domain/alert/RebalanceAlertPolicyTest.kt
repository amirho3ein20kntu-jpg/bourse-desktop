package com.example.domain.alert

import com.example.domain.alert.RebalanceAlertPolicy.AlertState
import com.example.domain.alert.RebalanceAlertPolicy.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نوتیفیکیشنی که زیاد بیاید خاموش می‌شود و بعد از آن کاربر هیچ‌وقت خبردار
 * نمی‌شود. این تست‌ها همان تعادل را قفل می‌کنند.
 */
class RebalanceAlertPolicyTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    // ------------------------------------------------------------------
    // عبور از آستانه
    // ------------------------------------------------------------------

    @Test
    fun crossingTheThresholdNotifies() {
        val decision = RebalanceAlertPolicy.decide(
            trIndex = 6.2,
            thresholdPercent = 5.0,
            hasPortfolio = true,
            state = AlertState(wasAboveThreshold = false),
            nowEpochMs = now
        )

        assertTrue(decision.shouldNotify)
        assertEquals(Reason.CROSSED_THRESHOLD, decision.reason)
        assertTrue(decision.newState.wasAboveThreshold)
        assertEquals(now, decision.newState.lastAlertEpochMs)
    }

    @Test
    fun exactlyAtTheThresholdCounts() {
        val decision = RebalanceAlertPolicy.decide(
            5.0, 5.0, true, AlertState(wasAboveThreshold = false), now
        )

        assertTrue(decision.shouldNotify)
    }

    @Test
    fun stayingBelowNeverNotifies() {
        val decision = RebalanceAlertPolicy.decide(
            3.1, 5.0, true, AlertState(wasAboveThreshold = false), now
        )

        assertFalse(decision.shouldNotify)
        assertEquals(Reason.BELOW_THRESHOLD, decision.reason)
        assertFalse(decision.newState.wasAboveThreshold)
    }

    // ------------------------------------------------------------------
    // ضد اسپم
    // ------------------------------------------------------------------

    @Test
    fun stillAboveTheNextDayDoesNotNotifyAgain() {
        val state = AlertState(wasAboveThreshold = true, lastAlertEpochMs = now)

        val decision = RebalanceAlertPolicy.decide(
            7.0, 5.0, true, state, now + day
        )

        assertFalse("نباید هر روز نوتیفیکیشن بفرستد", decision.shouldNotify)
        assertEquals(Reason.ALREADY_NOTIFIED, decision.reason)
    }

    @Test
    fun stillAboveAfterAWeekSendsOneReminder() {
        val state = AlertState(wasAboveThreshold = true, lastAlertEpochMs = now)

        val decision = RebalanceAlertPolicy.decide(
            7.0, 5.0, true, state, now + 7 * day
        )

        assertTrue(decision.shouldNotify)
        assertEquals(Reason.STILL_ABOVE_REMINDER, decision.reason)
        assertEquals(now + 7 * day, decision.newState.lastAlertEpochMs)
    }

    @Test
    fun droppingBelowThenCrossingAgainNotifiesAgain() {
        // بالای آستانه، اطلاع داده شد
        var state = RebalanceAlertPolicy.decide(
            6.0, 5.0, true, AlertState(), now
        ).newState

        // برگشت به زیر آستانه
        state = RebalanceAlertPolicy.decide(
            2.0, 5.0, true, state, now + day
        ).newState
        assertFalse(state.wasAboveThreshold)

        // دوباره رد شد — این یک رویداد تازه است
        val decision = RebalanceAlertPolicy.decide(
            6.5, 5.0, true, state, now + 2 * day
        )

        assertTrue(decision.shouldNotify)
        assertEquals(Reason.CROSSED_THRESHOLD, decision.reason)
    }

    // ------------------------------------------------------------------
    // نبود داده
    // ------------------------------------------------------------------

    @Test
    fun emptyPortfolioNeverNotifies() {
        val decision = RebalanceAlertPolicy.decide(
            9.0, 5.0, hasPortfolio = false, state = AlertState(), nowEpochMs = now
        )

        assertFalse(decision.shouldNotify)
        assertEquals(Reason.NO_DATA, decision.reason)
    }

    @Test
    fun aFailedCheckLeavesTheStateUntouched_soTheNextCrossingIsNotSwallowed() {
        val state = AlertState(wasAboveThreshold = true, lastAlertEpochMs = now)

        val decision = RebalanceAlertPolicy.decide(
            Double.NaN, 5.0, true, state, now + day
        )

        assertFalse(decision.shouldNotify)
        assertEquals(Reason.NO_DATA, decision.reason)
        assertEquals(state, decision.newState)
    }

    @Test
    fun aThresholdOfZeroIsTreatedAsNoData() {
        val decision = RebalanceAlertPolicy.decide(
            1.0, 0.0, true, AlertState(), now
        )

        assertFalse(decision.shouldNotify)
        assertEquals(Reason.NO_DATA, decision.reason)
    }

    // ------------------------------------------------------------------
    // متن
    // ------------------------------------------------------------------

    @Test
    fun crossingTextNamesBothNumbers() {
        val text = RebalanceAlertPolicy.notificationText(6.2, 5.0, Reason.CROSSED_THRESHOLD)

        assertTrue(text.contains("6.2٪"))
        assertTrue(text.contains("5٪"))
    }

    @Test
    fun reminderTextSaysItHasBeenAWhile() {
        val text = RebalanceAlertPolicy.notificationText(7.0, 5.0, Reason.STILL_ABOVE_REMINDER)

        assertTrue(text.contains("هفته"))
    }

    @Test
    fun silentReasonsProduceNoText() {
        assertEquals("", RebalanceAlertPolicy.notificationText(1.0, 5.0, Reason.BELOW_THRESHOLD))
        assertEquals("", RebalanceAlertPolicy.notificationText(9.0, 5.0, Reason.ALREADY_NOTIFIED))
    }

    // ------------------------------------------------------------------
    // متن هشدار وقتی نمادی قفل نگه‌داری دارد
    // ------------------------------------------------------------------

    @Test
    fun textWithoutLockIsUnchanged() {
        assertEquals(
            "شاخص انحراف سبد به 5.4٪ رسید و از آستانه 5٪ شما گذشت. وقت بررسی ریبلنس است.",
            RebalanceAlertPolicy.notificationText(5.4, 5.0, Reason.CROSSED_THRESHOLD)
        )
    }

    @Test
    fun lockPartBelowThreshold_saysTheSignalsAreEnough() {
        val text = RebalanceAlertPolicy.notificationText(
            trIndex = 5.4, thresholdPercent = 5.0, reason = Reason.CROSSED_THRESHOLD,
            lockBoundTrIndex = 4.0, lockedSymbols = listOf("درخشان"), requiredCashText = "۷.۶ میلیون تومان"
        )
        assertTrue(text.endsWith("4٪ از آن به خاطر قفل نگه‌داری درخشان است؛ با اجرای سیگنال‌ها شاخص زیر آستانه برمی‌گردد."))
        assertFalse(text.contains("واریز"))
    }

    @Test
    fun lockPartAtOrAboveThreshold_pointsToTheCashNeeded() {
        val text = RebalanceAlertPolicy.notificationText(
            trIndex = 5.4, thresholdPercent = 5.0, reason = Reason.STILL_ABOVE_REMINDER,
            lockBoundTrIndex = 5.4, lockedSymbols = listOf("عیار", "درخشان"), requiredCashText = "۷.۶ میلیون تومان"
        )
        assertTrue(text.contains("5.4٪ از آن به خاطر قفل نگه‌داری عیار، درخشان است و با معامله برطرف نمی‌شود"))
        assertTrue(text.endsWith("تنها راهش واریز ۷.۶ میلیون تومان است."))
    }
}
