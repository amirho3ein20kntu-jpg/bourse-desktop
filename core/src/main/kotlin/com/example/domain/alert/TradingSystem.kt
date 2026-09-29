package com.example.domain.alert

/**
 * سیستم معاملاتی متناظر با آستانه تحریک ریبلنس (Tr).
 *
 * تنها جایی که مرز سیستم‌ها تعریف شده. قبلاً دو تعریف متناقض وجود داشت:
 * دکمه‌های تنظیمات آستانه ۵٪ را «میان‌بسامد» و ۲۰٪ را «کم‌بسامد» می‌نامیدند،
 * ولی گیج داشبورد با شرط `<=` همان ۵٪ را «پربسامد» و ۲۰٪ را «میان‌بسامد»
 * نشان می‌داد. مرز پایینِ هر بازه متعلق به سیستم بالاتر است، مطابق دکمه‌ها.
 */
enum class TradingSystem(
    val code: String,
    val persianName: String,
    /** شروع بازه (شامل) */
    val fromPercent: Double,
    /** پایان بازه (ناشامل)؛ null یعنی بی‌سقف */
    val untilPercent: Double?
) {
    HFR("HFR", "پربسامد", 2.0, 5.0),
    MFR("MFR", "میان‌بسامد", 5.0, 20.0),
    LFR("LFR", "کم‌بسامد", 20.0, null);

    /** مثلاً «سیستم پربسامد (HFR: ۲٪ تا کمتر از ۵٪)» */
    val label: String
        get() = "سیستم $persianName ($code: $rangeText)"

    val rangeText: String
        get() = if (untilPercent == null) {
            "${persian(fromPercent)}٪ و بیشتر"
        } else {
            "${persian(fromPercent)}٪ تا کمتر از ${persian(untilPercent)}٪"
        }

    companion object {
        /** آستانه‌های زیر ۲٪ هم پربسامد حساب می‌شوند (سخت‌گیرانه‌تر از آن نیست). */
        fun forThreshold(thresholdPercent: Double): TradingSystem = when {
            thresholdPercent < MFR.fromPercent -> HFR
            thresholdPercent < LFR.fromPercent -> MFR
            else -> LFR
        }

        private fun persian(value: Double): String {
            val digits = "۰۱۲۳۴۵۶۷۸۹"
            val text = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
            return text.map { if (it.isDigit()) digits[it - '0'] else it }.joinToString("")
        }
    }
}
