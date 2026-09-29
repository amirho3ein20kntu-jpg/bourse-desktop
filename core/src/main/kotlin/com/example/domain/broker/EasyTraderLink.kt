package com.example.domain.broker

/**
 * ساخت و خواندن لینک فرم سفارش ایزی‌تریدر (کارگزاری مفید).
 *
 * اپ اندرویدی ایزی‌تریدر یک **Trusted Web Activity** است (بسته
 * `ir.easytrader.orbis.m.twa`) — یعنی پوسته‌ای دور نسخه وب. به همین دلیل
 * باز کردن یک آدرس `https://m.easytrader.ir/...` از برنامه ما، صفحه را
 * **داخل خود اپ کارگزاری** باز می‌کند، نه در مرورگر. پس به اسکیم اختصاصی
 * یا API نیازی نیست.
 *
 * شکل آدرس، از روی دو نمونه واقعی که کاربر گرفت:
 *
 *     /order-form/IRT1AFRN0001/0/1   ← خرید
 *     /order-form/IRT1AFRN0001/1/1   ← فروش
 *
 * بخش سوم در هر دو `1` بود، پس به سمت سفارش ربطی ندارد و معنایش هنوز
 * معلوم نیست؛ همان مقدار دیده‌شده تکرار می‌شود.
 *
 * **این کلاس هیچ سفارشی ثبت نمی‌کند.** فقط فرم را باز می‌کند؛ تعداد، قیمت
 * و تأیید نهایی با کاربر است. ثبت خودکار سفارش، با یک اشتباه در نماد یا
 * مبلغ، پول واقعی از بین می‌برد.
 */
object EasyTraderLink {

    const val HOST: String = "m.easytrader.ir"

    /** بسته اپ اندرویدی؛ برای تشخیص نصب بودن و باز کردن مستقیم. */
    const val ANDROID_PACKAGE: String = "ir.easytrader.orbis.m.twa"

    private const val ORDER_FORM_SEGMENT = "order-form"

    /**
     * مقدار بخش سوم آدرس. در هر دو نمونه واقعی `1` بود.
     *
     * معنایش معلوم نیست، پس عمداً یک ثابتِ نام‌دار است و نه عددی لای کد:
     * اگر بعداً فهمیدیم چیست، یک‌جا عوض می‌شود.
     */
    private const val TRAILING_SEGMENT = "1"

    /**
     * ISIN استاندارد: دو حرف کد کشور و ده کاراکتر دیگر. نمونه ایران:
     * `IRT1AFRN0001`.
     *
     * اعتبارسنجی سخت‌گیرانه است چون خروجی این کلاس **فرم سفارش یک اوراق
     * بهادار** را باز می‌کند. یک کاراکتر اشتباه یعنی فرم اوراق دیگری باز
     * می‌شود و کاربر ممکن است چیزی بخرد که قصدش را نداشته.
     */
    private val ISIN_PATTERN = Regex("^IR[A-Z0-9]{10}$")

    enum class Side(val urlValue: String) {
        BUY("0"),
        SELL("1")
    }

    /** true یعنی این رشته شکل یک ISIN معتبر ایرانی را دارد. */
    fun isValidIsin(raw: String?): Boolean {
        val trimmed = raw?.trim()?.uppercase() ?: return false
        return ISIN_PATTERN.matches(trimmed)
    }

    /**
     * آدرس فرم سفارش. `null` یعنی ISIN معتبر نبود — و آن‌وقت **هیچ لینکی
     * ساخته نمی‌شود**، نه اینکه لینکی با کد ناقص برگردد.
     */
    fun orderFormUrl(isin: String?, side: Side): String? {
        val clean = isin?.trim()?.uppercase() ?: return null
        if (!ISIN_PATTERN.matches(clean)) return null
        return "https://$HOST/$ORDER_FORM_SEGMENT/$clean/${side.urlValue}/$TRAILING_SEGMENT"
    }

    /**
     * ISIN را از چیزی که کاربر چسبانده بیرون می‌کشد.
     *
     * هم آدرس کامل فرم سفارش را می‌پذیرد، هم خودِ ISIN را — چون کاربر ممکن
     * است هر کدام را کپی کند و رد کردن یکی‌شان فقط آزاردهنده است.
     *
     * `null` یعنی چیزی که چسبانده شده ISIN معتبری در خودش نداشت.
     */
    fun extractIsin(pasted: String?): String? {
        val text = pasted?.trim() ?: return null
        if (text.isEmpty()) return null

        // خودِ ISIN، بدون آدرس
        val asIsin = text.uppercase()
        if (ISIN_PATTERN.matches(asIsin)) return asIsin

        // داخل یک آدرس: اولین بخشی که شکل ISIN دارد
        return text
            .split('/', '?', '&', '#', ' ', '\n', '\t')
            .asSequence()
            .map { it.trim().uppercase() }
            .firstOrNull { ISIN_PATTERN.matches(it) }
    }

    /** true یعنی این آدرس به فرم سفارش ایزی‌تریدر اشاره می‌کند. */
    fun isOrderFormUrl(url: String?): Boolean {
        val text = url?.trim()?.lowercase() ?: return false
        return text.contains(HOST) && text.contains(ORDER_FORM_SEGMENT)
    }
}
