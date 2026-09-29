package com.example.domain.analysis

import com.example.data.model.FundTechnicalState

/**
 * لحن یک تفسیر — تا UI بتواند رنگش کند.
 *
 * رنگ اینجا فقط **تقویت‌کننده** است: متن تفسیر همیشه نوشته می‌شود، پس کسی که
 * رنگ را تشخیص نمی‌دهد چیزی از دست نمی‌دهد.
 */
enum class InterpretationTone {
    /** شرایط به نفع نماد است. */
    POSITIVE,

    /** چیزی که باید حواستان بهش باشد — نه لزوماً بد، ولی ریسک‌دار. */
    CAUTION,

    /** شرایط علیه نماد است. */
    NEGATIVE,

    /** نه این نه آن. */
    NEUTRAL
}

/** یک تفسیر کوتاه از یک عدد تکنیکال. */
data class Interpretation(
    val text: String,
    val tone: InterpretationTone
)

/**
 * اعداد تکنیکال را به فارسیِ قابل‌فهم ترجمه می‌کند.
 *
 * چرا این کلاس وجود دارد: کاربر عددِ «از SMA۲۰۰: ‎+۵۲٪‎» را می‌دید و
 * نمی‌دانست یعنی چه. خودِ عدد بدون تفسیر، تصمیم‌سازی نمی‌کند.
 *
 * **لحن عمداً توصیفی است، نه دستوری.** این تفسیرها می‌گویند «قیمت از میانگین
 * بلندمدتش خیلی فاصله گرفته»، نه «بفروش». سیگنال خرید و فروش جای دیگری در
 * برنامه ساخته می‌شود و بر پایه استراتژی ریبالانس است، نه بر پایه یک
 * اندیکاتور تنها. قاطی‌کردن این دو، به کاربر اطمینانی می‌داد که هیچ‌کدام از
 * این اعداد پشتش نیستند.
 *
 * **آستانه‌ها قراردادی‌اند، نه دقیق.** ۷۰ و ۳۰ برای RSI، و مرزهای فاصله از
 * میانگین، همان عددهای رایج تحلیل تکنیکال‌اند. اینجا به‌عنوان ثابتِ نام‌دار
 * نوشته شده‌اند تا معلوم باشد از کجا آمده‌اند و بشود عوضشان کرد — نه پخش‌شده
 * لای کد UI.
 */
object TechnicalInterpreter {

    // --- آستانه‌های فاصله از میانگین متحرک ۲۰۰ روزه (روند بلندمدت) ---

    /** بالاتر از این، فاصله از میانگین بلندمدت غیرعادی است. */
    const val SMA200_STRETCHED_PCT: Double = 50.0
    const val SMA200_STRONG_PCT: Double = 20.0
    const val SMA200_BELOW_PCT: Double = -10.0

    // --- آستانه‌های میانگین ۵۰ روزه (روند میان‌مدت، نوسانی‌تر) ---

    const val SMA50_STRETCHED_PCT: Double = 25.0
    const val SMA50_STRONG_PCT: Double = 10.0
    const val SMA50_BELOW_PCT: Double = -10.0

    // --- آستانه‌های RSI ---

    const val RSI_OVERBOUGHT: Double = 70.0
    const val RSI_BULLISH: Double = 55.0
    const val RSI_BEARISH: Double = 45.0
    const val RSI_OVERSOLD: Double = 30.0

    /** نزدیک‌تر از این درصد به حمایت یا مقاومت، یعنی «چسبیده» به آن سطح. */
    const val LEVEL_PROXIMITY_PCT: Double = 3.0

    /**
     * فاصله قیمت از میانگین متحرک ۲۰۰ روزه.
     *
     * فاصله زیاد از میانگین بلندمدت، به خودی خود سیگنال فروش نیست — ولی
     * یعنی بخش بزرگی از حرکت قیمت قبلاً اتفاق افتاده.
     */
    fun sma200(pct: Double?): Interpretation? {
        if (pct == null || !pct.isFinite()) return null
        return when {
            pct >= SMA200_STRETCHED_PCT -> Interpretation(
                "بیش از ${format(pct)}٪ بالاتر از میانگین ۲۰۰ روزه؛ فاصله زیاد است و احتمال اصلاح وجود دارد",
                InterpretationTone.CAUTION
            )
            pct >= SMA200_STRONG_PCT -> Interpretation(
                "روند بلندمدت صعودی و جاافتاده",
                InterpretationTone.POSITIVE
            )
            pct >= 0.0 -> Interpretation(
                "کمی بالای میانگین بلندمدت؛ روند مثبت ولی بدون هیجان",
                InterpretationTone.POSITIVE
            )
            pct >= SMA200_BELOW_PCT -> Interpretation(
                "کمی زیر میانگین بلندمدت؛ روند صعودی هنوز تأیید نشده",
                InterpretationTone.NEUTRAL
            )
            else -> Interpretation(
                "زیر میانگین ۲۰۰ روزه؛ روند بلندمدت نزولی است",
                InterpretationTone.NEGATIVE
            )
        }
    }

    /**
     * فاصله قیمت از میانگین متحرک ۵۰ روزه.
     *
     * آستانه‌ها از ۲۰۰ روزه تنگ‌تر است، چون میانگین کوتاه‌تر طبیعتاً نوسان
     * بیشتری دارد و همان ۵۰٪ اینجا معنای متفاوتی می‌دهد.
     */
    fun sma50(pct: Double?): Interpretation? {
        if (pct == null || !pct.isFinite()) return null
        return when {
            pct >= SMA50_STRETCHED_PCT -> Interpretation(
                "خیلی جلوتر از میانگین میان‌مدت؛ مستعد استراحت",
                InterpretationTone.CAUTION
            )
            pct >= SMA50_STRONG_PCT -> Interpretation(
                "شتاب میان‌مدت مثبت است",
                InterpretationTone.POSITIVE
            )
            pct >= 0.0 -> Interpretation(
                "کمی بالای میانگین میان‌مدت",
                InterpretationTone.POSITIVE
            )
            pct >= SMA50_BELOW_PCT -> Interpretation(
                "زیر میانگین میان‌مدت؛ در حال اصلاح",
                InterpretationTone.NEUTRAL
            )
            else -> Interpretation(
                "به‌وضوح زیر میانگین ۵۰ روزه؛ فشار فروش کوتاه‌مدت",
                InterpretationTone.NEGATIVE
            )
        }
    }

    /**
     * RSI چهارده‌روزه.
     *
     * نکته‌ای که معمولاً جا می‌افتد: اشباع خرید در یک روند صعودی قوی می‌تواند
     * هفته‌ها ادامه پیدا کند. «اشباع» یعنی کشش زیاد بوده، نه اینکه فردا
     * برمی‌گردد — و متن تفسیر همین را می‌گوید.
     */
    fun rsi(value: Double?): Interpretation? {
        if (value == null || !value.isFinite()) return null
        return when {
            value >= RSI_OVERBOUGHT -> Interpretation(
                "اشباع خرید؛ احتمال استراحت یا اصلاح کوتاه‌مدت",
                InterpretationTone.CAUTION
            )
            value >= RSI_BULLISH -> Interpretation(
                "قدرت دست خریدارهاست و هنوز اشباع نشده",
                InterpretationTone.POSITIVE
            )
            value >= RSI_BEARISH -> Interpretation(
                "خنثی؛ نه خریدار نه فروشنده دست بالا را دارد",
                InterpretationTone.NEUTRAL
            )
            value >= RSI_OVERSOLD -> Interpretation(
                "فشار فروش ملایم",
                InterpretationTone.NEGATIVE
            )
            else -> Interpretation(
                "اشباع فروش؛ اگر روند بلندمدت سالم باشد می‌تواند نقطه ورود باشد",
                InterpretationTone.CAUTION
            )
        }
    }

    /** فاصله تا نزدیک‌ترین حمایت. مقدار منفی یعنی حمایت پایین‌تر از قیمت است. */
    fun support(distPct: Double?): Interpretation? {
        if (distPct == null || !distPct.isFinite()) return null
        val distance = kotlin.math.abs(distPct)
        return when {
            distance <= LEVEL_PROXIMITY_PCT -> Interpretation(
                "چسبیده به حمایت؛ شکستنش می‌تواند افت را تندتر کند",
                InterpretationTone.CAUTION
            )
            distance <= 10.0 -> Interpretation(
                "حمایت نزدیک است و کف قابل‌اتکایی دارد",
                InterpretationTone.POSITIVE
            )
            else -> Interpretation(
                "تا حمایت بعدی فاصله زیادی هست",
                InterpretationTone.CAUTION
            )
        }
    }

    /** فاصله تا نزدیک‌ترین مقاومت. */
    fun resistance(distPct: Double?): Interpretation? {
        if (distPct == null || !distPct.isFinite()) return null
        val distance = kotlin.math.abs(distPct)
        return when {
            distance <= LEVEL_PROXIMITY_PCT -> Interpretation(
                "زیر مقاومت؛ عبور از آن معمولاً به حجم نیاز دارد",
                InterpretationTone.CAUTION
            )
            distance <= 10.0 -> Interpretation(
                "تا مقاومت بعدی جا دارد",
                InterpretationTone.POSITIVE
            )
            else -> Interpretation(
                "مقاومت نزدیکی در راه نیست",
                InterpretationTone.POSITIVE
            )
        }
    }

    /**
     * یک جمله جمع‌بندی از کل وضعیت تکنیکال.
     *
     * فقط دو نکته‌ی برجسته را می‌گوید، نه همه را: جمع‌بندی‌ای که هر پنج عدد را
     * تکرار کند، همان جدول است با کلمات بیشتر.
     *
     * `null` یعنی داده‌ای برای جمع‌بندی نبود.
     */
    fun summary(state: FundTechnicalState): String? {
        if (!state.hasData) return null

        val clauses = mutableListOf<String>()

        val sma200Pct = state.priceVsSma200Pct
        val rsiValue = state.rsi14

        // ۱) کشش بیش از حد از میانگین بلندمدت، مهم‌ترین نکته است
        if (sma200Pct != null && sma200Pct.isFinite() && sma200Pct >= SMA200_STRETCHED_PCT) {
            clauses += "قیمت ${format(sma200Pct)}٪ بالای میانگین ۲۰۰ روزه است"
        } else if (sma200Pct != null && sma200Pct.isFinite() && sma200Pct < SMA200_BELOW_PCT) {
            clauses += "قیمت زیر میانگین ۲۰۰ روزه است"
        }

        // ۲) RSI در دو سر طیف
        if (rsiValue != null && rsiValue.isFinite()) {
            when {
                rsiValue >= RSI_OVERBOUGHT -> clauses += "RSI در محدوده اشباع خرید است"
                rsiValue <= RSI_OVERSOLD -> clauses += "RSI در محدوده اشباع فروش است"
            }
        }

        // ۳) اگر هیچ‌کدام از دو مورد بالا نبود، روند را بگو
        if (clauses.isEmpty()) {
            val trend = trendPhrase(state)
            if (trend != null) clauses += trend
        }

        if (clauses.isEmpty()) return null

        val head = clauses.joinToString(" و ")
        val tail = conclusionFor(sma200Pct, rsiValue)
        return if (tail == null) "$head." else "$head؛ $tail"
    }

    private fun conclusionFor(sma200Pct: Double?, rsi: Double?): String? {
        val stretched = sma200Pct != null && sma200Pct.isFinite() && sma200Pct >= SMA200_STRETCHED_PCT
        val overbought = rsi != null && rsi.isFinite() && rsi >= RSI_OVERBOUGHT
        val oversold = rsi != null && rsi.isFinite() && rsi <= RSI_OVERSOLD
        val belowTrend = sma200Pct != null && sma200Pct.isFinite() && sma200Pct < SMA200_BELOW_PCT

        return when {
            stretched && overbought -> "احتمال اصلاح کوتاه‌مدت بالاست"
            stretched -> "بخش بزرگی از حرکت قیمت قبلاً اتفاق افتاده و احتمال اصلاح هست"
            belowTrend && oversold -> "افت سنگین بوده؛ برگشت نیاز به تأیید روند دارد"
            belowTrend -> "تا برگشت روند بلندمدت، خرید ریسک بیشتری دارد"
            overbought -> "ورود در این نقطه گران‌تر تمام می‌شود"
            oversold -> "اگر روند بلندمدت سالم باشد، می‌تواند نقطه ورود باشد"
            else -> null
        }
    }

    private fun trendPhrase(state: FundTechnicalState): String? {
        val monthly = state.trendMonthly.lowercase()
        val weekly = state.trendWeekly.lowercase()
        return when {
            monthly == "up" && weekly == "up" -> "روند ماهانه و هفتگی هر دو صعودی‌اند"
            monthly == "down" && weekly == "down" -> "روند ماهانه و هفتگی هر دو نزولی‌اند"
            monthly == "up" && weekly == "down" -> "روند بلندمدت صعودی است ولی هفته اخیر منفی بوده"
            monthly == "down" && weekly == "up" -> "روند بلندمدت نزولی است و هفته اخیر مثبت بوده"
            else -> null
        }
    }

    /** عدد را بدون اعشار اضافی و همیشه مثبت نشان می‌دهد (علامت در متن می‌آید). */
    private fun format(value: Double): String {
        val rounded = kotlin.math.abs(value).let {
            if (it >= 10.0) kotlin.math.round(it).toInt().toString()
            else ((kotlin.math.round(it * 10) / 10).toString())
        }
        return rounded
    }
}
