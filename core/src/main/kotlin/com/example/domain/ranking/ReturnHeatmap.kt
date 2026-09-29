package com.example.domain.ranking

import kotlin.math.abs

/**
 * شدت رنگ سلول‌های جدول مقایسه بازدهی — بدون وابستگی به اندروید، تا قابل تست باشد.
 *
 * **مقیاس دوسویه است، نه ترتیبی.** بازدهی می‌تواند مثبت یا منفی باشد، پس نقطه
 * میانی روی **صفر** لنگر می‌اندازد، نه روی میانگین داده. اگر میانگین را مبنا
 * می‌گرفتیم، در ستونی که همه بازدهی‌ها مثبت‌اند ضعیف‌ترین صندوق قرمز دیده می‌شد —
 * که دروغ است.
 *
 * مقیاس درون **هر ستون** حساب می‌شود، چون مقایسه‌ی معنادار بین صندوق‌های همان
 * بازه زمانی است؛ بازدهی سالانه و یک‌ماهه اصلاً هم‌مقیاس نیستند.
 *
 * رنگ اینجا فقط **تقویت‌کننده** است: خود عدد و علامتش در هر سلول نوشته می‌شود،
 * پس کسی که کوررنگی قرمز-سبز دارد هم می‌تواند جدول را بخواند.
 */
object ReturnHeatmap {

    /** کمترین شدت قابل دیدن؛ زیر این مقدار سلول عملاً بی‌رنگ است. */
    const val MIN_ALPHA: Float = 0.10f

    /**
     * بیشترین شدت. عمداً زیر نصف است تا متن روی سلول در هر دو تم روشن و تاریک
     * خوانا بماند — پررنگ‌تر از این، عدد داخل سلول گم می‌شود.
     */
    const val MAX_ALPHA: Float = 0.55f

    /**
     * مقیاس یک ستون: بزرگ‌ترین قدرمطلق بین مقادیر موجود.
     *
     * `null` یعنی رنگ‌آمیزی معنا ندارد — یا هیچ داده‌ای نیست، یا همه صفرند.
     */
    fun scaleOf(values: List<Double?>): Double? {
        val scale = values
            .filterNotNull()
            .filter { !it.isNaN() && !it.isInfinite() }
            .maxOfOrNull { abs(it) }
            ?: return null
        return if (scale > 0.0) scale else null
    }

    /**
     * جایگاه یک مقدار روی مقیاس دوسویه: از ۱- (بدترین) تا ۱+ (بهترین)، با صفر
     * دقیقاً در وسط. `null` یعنی این سلول داده ندارد و باید «—» نشان داده شود.
     */
    fun intensity(value: Double?, scale: Double?): Float? {
        if (value == null || value.isNaN() || value.isInfinite()) return null
        if (scale == null || scale <= 0.0) return 0f
        return (value / scale).coerceIn(-1.0, 1.0).toFloat()
    }

    /**
     * شدت را به آلفای رنگ تبدیل می‌کند.
     *
     * حتی ضعیف‌ترین مقدارِ غیرصفر کمی رنگ می‌گیرد تا از سلولِ بدون داده قابل
     * تفکیک باشد؛ سلول بدون داده هیچ رنگی ندارد.
     */
    fun alphaFor(intensity: Float?): Float {
        if (intensity == null) return 0f
        val magnitude = abs(intensity)
        if (magnitude <= 0f) return 0f
        return MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * magnitude
    }

    /** true یعنی این مقدار سمت مثبت مقیاس است (سبز)، false یعنی منفی (قرمز). */
    fun isPositive(value: Double?): Boolean = (value ?: 0.0) >= 0.0

    /**
     * آلفای مقیاس **تک‌سویه**، برای امتیاز تداوم بازدهی.
     *
     * این عدد قطب ندارد: ۰ «بد» است نه «قرینهٔ خوب»، پس مقیاس دوسویهٔ
     * سبز/قرمز اینجا غلط است — امتیاز ۱۰ را قرمزِ پررنگ نشان می‌داد، انگار
     * زیان است. یک طیف تک‌رنگ از کم‌رنگ به پررنگ، همان چیزی را می‌گوید که
     * هست: بالاتر یعنی بیشتر.
     *
     * امتیاز صفر هم کمی رنگ می‌گیرد (MIN_ALPHA)، چون داده دارد و باید از
     * سلولِ بی‌داده جدا دیده شود.
     */
    fun sequentialAlpha(score: Int?): Float {
        if (score == null) return 0f
        val fraction = (score.coerceIn(0, 100)) / 100f
        return MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * fraction
    }
}
