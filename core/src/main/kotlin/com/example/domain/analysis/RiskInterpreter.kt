package com.example.domain.analysis

import com.example.data.model.FundRiskStats

/**
 * سه عدد بخش «آمار ریسک» را به فارسیِ قابل‌فهم ترجمه می‌کند.
 *
 * مثل [TechnicalInterpreter] لحن **توصیفی** است نه دستوری، و آستانه‌ها
 * ثابتِ نام‌دارند تا معلوم باشد از کجا آمده‌اند.
 *
 * **آستانه‌ها عمداً عمومی‌اند و به دسته صندوق کاری ندارند.** افت ۱۵ درصدی
 * برای یک صندوق سهامی عادی است و برای یک صندوق درآمد ثابت فاجعه — ولی برای
 * تفکیک‌کردنشان باید محدوده‌های واقعی هر دسته در بازار ایران را می‌دانستم،
 * و نداشتنش یعنی هر عددی که می‌گذاشتم ساختگی بود. به‌جایش متن تفسیر خودِ
 * پدیده را توضیح می‌دهد و قضاوت دسته را به کاربر می‌سپارد.
 */
object RiskInterpreter {

    // --- آستانه‌های حداکثر افت (درصد، منفی) ---
    const val DRAWDOWN_MILD_PCT: Double = -5.0
    const val DRAWDOWN_NORMAL_PCT: Double = -15.0
    const val DRAWDOWN_HEAVY_PCT: Double = -30.0

    // --- آستانه‌های نسبت شارپ ---
    const val SHARPE_WEAK: Double = 1.0
    const val SHARPE_FAIR: Double = 2.0
    const val SHARPE_GOOD: Double = 3.0

    // --- آستانه‌های نوسان سالانه (درصد) ---
    const val VOLATILITY_LOW_PCT: Double = 15.0
    const val VOLATILITY_NORMAL_PCT: Double = 30.0
    const val VOLATILITY_HIGH_PCT: Double = 50.0

    /**
     * حداکثر افت.
     *
     * این عدد می‌گوید «اگر بدترین لحظه خریده بودید، تا کف چقدر زیر آب
     * می‌رفتید» — نه اینکه الان چقدر ضرر کرده‌اید. تفسیر همین تمایز را
     * نگه می‌دارد، چون کاربر معمولاً اولی را با دومی اشتباه می‌گیرد.
     */
    fun maxDrawdown(pct: Double?): Interpretation? {
        if (pct == null || !pct.isFinite()) return null
        val depth = -kotlin.math.abs(pct)
        return when {
            depth >= DRAWDOWN_MILD_PCT -> Interpretation(
                "افت ناچیز؛ در این بازه مسیر نسبتاً هموار بوده",
                InterpretationTone.POSITIVE
            )
            depth >= DRAWDOWN_NORMAL_PCT -> Interpretation(
                "افت متعارف؛ در بدترین دوره تا این حد پایین رفته و برگشته",
                InterpretationTone.NEUTRAL
            )
            depth >= DRAWDOWN_HEAVY_PCT -> Interpretation(
                "افت قابل‌توجه؛ اگر در سقف خریده بودید تا این حد زیر آب می‌رفتید",
                InterpretationTone.CAUTION
            )
            else -> Interpretation(
                "افت سنگین؛ نگه‌داشتنش طاقت تحمل نوسان می‌خواهد",
                InterpretationTone.NEGATIVE
            )
        }
    }

    /**
     * نسبت شارپ.
     *
     * نکته‌ای که در بازه کوتاه جا می‌افتد: شارپِ خیلی بالا روی ۹۰ روز
     * معمولاً یعنی صندوق در یک دوره صعودیِ خاص بوده، نه اینکه ذاتاً
     * کم‌ریسک و پربازده است. تفسیر همین را می‌گوید تا عدد ۴ و ۵ وعده‌ای
     * ندهد که داده پشتش نیست.
     */
    fun sharpe(value: Double?, windowDays: Int = FundRiskStats.DEFAULT_WINDOW_DAYS): Interpretation? {
        if (value == null || !value.isFinite()) return null
        return when {
            value < 0.0 -> Interpretation(
                "منفی؛ ریسکی که برداشته جواب نداده",
                InterpretationTone.NEGATIVE
            )
            value < SHARPE_WEAK -> Interpretation(
                "ضعیف؛ بازدهی متناسب با ریسکش نبوده",
                InterpretationTone.NEGATIVE
            )
            value < SHARPE_FAIR -> Interpretation(
                "قابل قبول؛ بازدهی و ریسک متوازن بوده‌اند",
                InterpretationTone.NEUTRAL
            )
            value < SHARPE_GOOD -> Interpretation(
                "خوب؛ بازدهی خوبی به ازای ریسکش داده",
                InterpretationTone.POSITIVE
            )
            else -> Interpretation(
                "بسیار بالا — ولی در بازه $windowDays روزه معمولاً یعنی یک دوره صعودی خاص، نه کیفیت همیشگی",
                InterpretationTone.CAUTION
            )
        }
    }

    /** نوسان سالانه‌شده. */
    fun volatility(pct: Double?): Interpretation? {
        if (pct == null || !pct.isFinite()) return null
        val magnitude = kotlin.math.abs(pct)
        return when {
            magnitude < VOLATILITY_LOW_PCT -> Interpretation(
                "کم‌نوسان؛ قیمتش آرام حرکت می‌کند",
                InterpretationTone.POSITIVE
            )
            magnitude < VOLATILITY_NORMAL_PCT -> Interpretation(
                "نوسان متعارف",
                InterpretationTone.NEUTRAL
            )
            magnitude < VOLATILITY_HIGH_PCT -> Interpretation(
                "پرنوسان؛ بالا و پایین‌های بزرگ طبیعی‌اند",
                InterpretationTone.CAUTION
            )
            else -> Interpretation(
                "بسیار پرنوسان؛ برای سبدی که به آرامش نیاز دارد سنگین است",
                InterpretationTone.NEGATIVE
            )
        }
    }

    /**
     * یک جمله جمع‌بندی از وضعیت ریسک.
     *
     * فقط وقتی حرفی برای گفتن دارد جمله می‌سازد: صندوقی که هر سه عددش در
     * محدوده معمول است، جمع‌بندی نمی‌گیرد تا کارت شلوغ نشود.
     */
    fun summary(stats: FundRiskStats?): String? {
        if (stats == null || !stats.hasAnyStat) return null

        val drawdown = stats.maxDrawdownPct?.takeIf { it.isFinite() }?.let { -kotlin.math.abs(it) }
        val volatility = stats.annualVolatilityPct?.takeIf { it.isFinite() }?.let { kotlin.math.abs(it) }
        val sharpe = stats.sharpeRatio?.takeIf { it.isFinite() }

        val clauses = mutableListOf<String>()

        if (drawdown != null && drawdown < DRAWDOWN_NORMAL_PCT) {
            clauses += "در ${stats.windowDays} روز گذشته تا ${format(drawdown)}٪ افت کرده"
        }
        if (volatility != null && volatility >= VOLATILITY_HIGH_PCT) {
            clauses += "نوسان سالانه‌اش بالای ${format(VOLATILITY_HIGH_PCT)}٪ است"
        }
        if (clauses.isEmpty() && sharpe != null && sharpe >= SHARPE_GOOD) {
            clauses += "نسبت شارپ ${format(sharpe)} در این بازه بسیار بالاست"
        }

        if (clauses.isEmpty()) return null

        val tail = when {
            sharpe != null && sharpe >= SHARPE_GOOD ->
                "بازدهی‌اش این ریسک را جبران کرده، ولی بازه ۹۰ روزه برای نتیجه‌گیری کوتاه است"
            drawdown != null && drawdown < DRAWDOWN_HEAVY_PCT ->
                "قبل از خرید، مطمئن شوید چنین افتی را تحمل می‌کنید"
            else ->
                "این نوسان بخشی از ذات همین صندوق است"
        }

        return "${clauses.joinToString(" و ")}؛ $tail."
    }

    /** عدد را بدون اعشار اضافی نشان می‌دهد؛ علامت منفی حفظ می‌شود. */
    private fun format(value: Double): String {
        val magnitude = kotlin.math.abs(value)
        val body = if (magnitude >= 10.0) {
            kotlin.math.round(magnitude).toInt().toString()
        } else {
            (kotlin.math.round(magnitude * 100) / 100).toString()
        }
        return if (value < 0) "-$body" else body
    }
}
