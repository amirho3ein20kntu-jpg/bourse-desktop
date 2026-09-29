package com.example.domain.alert

/**
 * تصمیم‌گیری برای هشدار ریبلنس — بدون وابستگی به اندروید، تا قابل تست باشد.
 *
 * اپ تا امروز فقط وقتی بازش می‌کردید خبر می‌داد. ممکن بود شاخص Tr امروز از
 * آستانه رد شود و شما سه روز بعد بفهمید.
 *
 * **قاعده اصلی: هشدار روی «عبور» است، نه روی «بالا بودن».** اگر هر روز که Tr
 * بالای آستانه است نوتیفیکیشن بفرستیم، کاربر ظرف یک هفته خاموشش می‌کند و
 * دیگر هیچ‌وقت خبردار نمی‌شود. پس فقط لحظه‌ی رد شدن اعلام می‌شود، و اگر وضعیت
 * ادامه پیدا کرد، یک یادآوری با فاصله‌ی زیاد.
 */
object RebalanceAlertPolicy {

    /** فاصله‌ی یادآوری وقتی Tr همچنان بالای آستانه مانده: ۷ روز. */
    const val REMINDER_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

    /** آنچه بین دو بررسی باید به یاد بماند. */
    data class AlertState(
        /** آیا در بررسی قبلی، Tr بالای آستانه بود */
        val wasAboveThreshold: Boolean = false,
        /** زمان آخرین نوتیفیکیشن ارسال‌شده؛ صفر یعنی هرگز */
        val lastAlertEpochMs: Long = 0L
    )

    enum class Reason {
        /** Tr تازه از آستانه رد شد */
        CROSSED_THRESHOLD,

        /** همچنان بالای آستانه است و یادآوری هفتگی سررسید شده */
        STILL_ABOVE_REMINDER,

        /** زیر آستانه است */
        BELOW_THRESHOLD,

        /** بالای آستانه ولی به‌تازگی اطلاع داده‌ایم */
        ALREADY_NOTIFIED,

        /** داده معتبری برای تصمیم‌گیری نبود */
        NO_DATA
    }

    data class Decision(
        val shouldNotify: Boolean,
        val reason: Reason,
        val newState: AlertState
    )

    /**
     * @param trIndex شاخص انحراف فعلی سبد.
     * @param thresholdPercent آستانه‌ای که کاربر تنظیم کرده.
     * @param hasPortfolio اگر سبد خالی باشد، هیچ هشداری معنا ندارد.
     */
    fun decide(
        trIndex: Double,
        thresholdPercent: Double,
        hasPortfolio: Boolean,
        state: AlertState,
        nowEpochMs: Long
    ): Decision {
        if (!hasPortfolio || thresholdPercent <= 0.0 || trIndex.isNaN()) {
            // وضعیت دست‌نخورده می‌ماند: یک بررسی ناموفق نباید «عبور» بعدی را بخورد
            return Decision(false, Reason.NO_DATA, state)
        }

        val isAbove = trIndex >= thresholdPercent

        if (!isAbove) {
            // برگشت به زیر آستانه، شرط لازم برای اعلام «عبور» بعدی است
            return Decision(
                shouldNotify = false,
                reason = Reason.BELOW_THRESHOLD,
                newState = state.copy(wasAboveThreshold = false)
            )
        }

        if (!state.wasAboveThreshold) {
            return Decision(
                shouldNotify = true,
                reason = Reason.CROSSED_THRESHOLD,
                newState = AlertState(wasAboveThreshold = true, lastAlertEpochMs = nowEpochMs)
            )
        }

        val sinceLast = nowEpochMs - state.lastAlertEpochMs
        val reminderDue = state.lastAlertEpochMs > 0L && sinceLast >= REMINDER_INTERVAL_MS

        return if (reminderDue) {
            Decision(
                shouldNotify = true,
                reason = Reason.STILL_ABOVE_REMINDER,
                newState = state.copy(wasAboveThreshold = true, lastAlertEpochMs = nowEpochMs)
            )
        } else {
            Decision(
                shouldNotify = false,
                reason = Reason.ALREADY_NOTIFIED,
                newState = state.copy(wasAboveThreshold = true)
            )
        }
    }

    /**
     * متن نوتیفیکیشن — اینجا ساخته می‌شود تا قابل تست باشد.
     *
     * @param lockBoundTrIndex بخشی از Tr که به خاطر قفل نگه‌داری با معامله پایین
     *   نمی‌آید. بدون این جمله، کاربری که طلا را قفل کرده «وقت ریبلنس است»
     *   می‌شنید، سیگنال‌ها را اجرا می‌کرد و هشدار باز هم هر هفته برمی‌گشت.
     * @param requiredCashText مبلغ واریز لازم، از پیش قالب‌بندی‌شده به تومان.
     */
    fun notificationText(
        trIndex: Double,
        thresholdPercent: Double,
        reason: Reason,
        lockBoundTrIndex: Double = 0.0,
        lockedSymbols: List<String> = emptyList(),
        requiredCashText: String? = null
    ): String {
        val tr = formatPercent(trIndex)
        val limit = formatPercent(thresholdPercent)
        val base = when (reason) {
            Reason.CROSSED_THRESHOLD ->
                "شاخص انحراف سبد به $tr رسید و از آستانه $limit شما گذشت. وقت بررسی ریبلنس است."
            Reason.STILL_ABOVE_REMINDER ->
                "شاخص انحراف سبد هنوز $tr است (آستانه شما: $limit). یک هفته است که بالای آستانه مانده."
            else -> return ""
        }
        return base + lockNote(lockBoundTrIndex, thresholdPercent, lockedSymbols, requiredCashText)
    }

    private fun lockNote(
        lockBoundTrIndex: Double,
        thresholdPercent: Double,
        lockedSymbols: List<String>,
        requiredCashText: String?
    ): String {
        if (lockBoundTrIndex <= 0.0 || lockedSymbols.isEmpty()) return ""
        val part = formatPercent(lockBoundTrIndex)
        val symbols = lockedSymbols.joinToString("، ")
        return if (lockBoundTrIndex >= thresholdPercent) {
            val cash = requiredCashText?.let { " واریز $it" } ?: " واریز پول نقد"
            " $part از آن به خاطر قفل نگه‌داری $symbols است و با معامله برطرف نمی‌شود؛ تنها راهش$cash است."
        } else {
            " $part از آن به خاطر قفل نگه‌داری $symbols است؛ با اجرای سیگنال‌ها شاخص زیر آستانه برمی‌گردد."
        }
    }

    private fun formatPercent(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) "${rounded.toInt()}٪" else "$rounded٪"
    }
}
