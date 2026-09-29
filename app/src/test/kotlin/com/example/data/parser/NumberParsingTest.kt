package com.example.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberParsingTest {

    @Test
    fun persianDecimalSeparatorIsADecimalPoint_notDropped() {
        // «٬» جداکننده هزارگان و «٫» اعشار است؛ قبلاً ۱۲۳۴۵۶۷ خوانده می‌شد.
        assertEquals(12_345.67, ExcelAndCsvParser.parseNumber("۱۲٬۳۴۵٫۶۷"), 1e-9)
    }

    @Test
    fun scientificNotationFromXlsxCellsIsReadInFull() {
        assertEquals(12_345_678_901.0, ExcelAndCsvParser.parseNumber("1.2345678901E10"), 1e-3)
        assertEquals(12_345_678_901L, ExcelAndCsvParser.parseLong("1.2345678901E10"))
        assertEquals(0.0015, ExcelAndCsvParser.parseNumber("1.5E-3"), 1e-12)
    }

    @Test
    fun ordinaryNumbersAreUnchanged() {
        assertEquals(12_345.5, ExcelAndCsvParser.parseNumber("12,345.5"), 1e-9)
        assertEquals(1_000_000.0, ExcelAndCsvParser.parseNumber("۱,۰۰۰,۰۰۰ ریال"), 1e-9)
        assertEquals(-250.0, ExcelAndCsvParser.parseNumber("-250"), 1e-9)
    }
}
