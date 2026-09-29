package com.example.data.parser

import com.example.platform.Context
import com.example.platform.Uri
import com.example.data.model.AssetCategory
import com.example.data.model.FundCategoryEntity
import com.example.data.model.PortfolioEntity
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object ExcelAndCsvParser {

    /**
     * پایین‌ترین نسبت ارزش خالص به ناخالص که هنوز باورکردنی است. کارمزد فروش
     * در بازار ایران حداکثر حدود یک درصد است؛ عددی پایین‌تر از این یعنی
     * ستون‌های تعداد یا قیمت درست خوانده نشده‌اند، نه اینکه کارمزد زیاد است.
     */
    const val MIN_NET_VALUE_RATIO = 0.95


    /**
     * Converts Persian and Arabic digits to standard ASCII digits and strips commas/spaces/currency labels.
     */
    fun sanitizeNumberString(raw: String): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val builder = StringBuilder()

        // Remove currency words and metadata (Technical Spec Section 7)
        val filtered = raw.replace("ریال", "")
            .replace("تومان", "")
            .replace("Rial", "", ignoreCase = true)
            .replace("IRR", "", ignoreCase = true)
            .replace("Toman", "", ignoreCase = true)

        for (ch in filtered) {
            when {
                ch in '0'..'9' -> builder.append(ch)
                // «٫» جداکننده اعشار فارسی است؛ قبلاً دور ریخته می‌شد و
                // «۱۲٫۵» عدد ۱۲۵ خوانده می‌شد.
                ch == '.' || ch == '/' || ch == '٫' -> builder.append('.')
                ch == '-' -> builder.append('-')
                ch in persianDigits -> builder.append(ch - '۰')
                ch in arabicDigits -> builder.append(ch - '٠')
                // Ignore commas, persian commas, spaces, non-breaking space, ZWNJ
                ch == ',' || ch == '،' || ch.isWhitespace() || ch == '\u00A0' || ch == '\u200C' -> {}
                else -> {}
            }
        }
        return builder.toString()
    }

    /**
     * Technical Spec Section 7: Parse number safely with Double/BigDecimal fallback to prevent any crash.
     */
    /**
     * عدد علمی مثل `1.2345678901E10`. برخی نرم‌افزارها سلول عددی xlsx را این‌طور
     * می‌نویسند؛ پاک‌سازی حرف E را دور می‌ریخت و ارزش ۱۲ میلیارد ریالی ۱٫۲۳
     * خوانده می‌شد.
     */
    private val SCIENTIFIC_NUMBER = Regex("""^[-+]?\d+(\.\d+)?[eE][-+]?\d+$""")

    fun parseNumber(raw: String): Double {
        val trimmed = raw.trim()
        if (SCIENTIFIC_NUMBER.matches(trimmed)) return trimmed.toDoubleOrNull() ?: 0.0
        val clean = sanitizeNumberString(raw)
        if (clean.isEmpty()) return 0.0
        return try {
            clean.toDoubleOrNull() ?: (clean.toBigDecimalOrNull()?.toDouble() ?: 0.0)
        } catch (e: Exception) {
            0.0
        }
    }

    fun parseLong(raw: String): Long {
        val trimmed = raw.trim()
        if (SCIENTIFIC_NUMBER.matches(trimmed)) return trimmed.toDoubleOrNull()?.toLong() ?: 0L
        val clean = sanitizeNumberString(raw)
        if (clean.isEmpty()) return 0L
        return try {
            clean.toLongOrNull() ?: clean.toDoubleOrNull()?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Converts Excel column letters (e.g., "A" -> 0, "F" -> 5, "AA" -> 26) to 0-indexed column integer.
     */
    fun colLettersToIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return -1
        var col = 0
        for (ch in letters) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            }
        }
        return col - 1 // A -> 0, F -> 5
    }

    /**
     * Parses raw table rows from an XLSX or CSV input stream.
     */
    fun parseRowsFromUri(context: Context, uri: Uri): List<List<String>> {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return emptyList()

        return inputStream.use { stream ->
            val bytes = stream.readBytes()
            // Check if it is a ZIP (XLSX signature: PK.. 0x50 0x4B 0x03 0x04)
            if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                parseXlsx(ByteArrayInputStream(bytes))
            } else {
                parseCsv(ByteArrayInputStream(bytes))
            }
        }
    }

    /**
     * Parses CSV or TSV stream into rows of cells.
     */
    fun parseCsv(inputStream: InputStream): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line?.trim() ?: continue
            if (trimmed.isEmpty()) continue

            // Determine delimiter: comma, semicolon, or tab
            val delimiter = when {
                trimmed.contains('\t') -> '\t'
                trimmed.contains(';') -> ';'
                else -> ','
            }

            val cells = parseCsvLine(trimmed, delimiter)
            if (cells.isNotEmpty()) {
                rows.add(cells)
            }
        }
        return rows
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var insideQuotes = false

        for (ch in line) {
            when {
                ch == '"' -> insideQuotes = !insideQuotes
                ch == delimiter && !insideQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    /**
     * پارسر XLSX با ZipInputStream و XmlPullParser (بدون کتابخانه خارجی).
     *
     * نسخه قبلی فقط دنبال `sheet1.xml` می‌گشت؛ اگر کارگزاری شیت را با نام دیگری
     * ذخیره کرده بود، فایل «خالی» تشخیص داده می‌شد. حالا همه شیت‌ها خوانده و
     * پرمحتواترینشان انتخاب می‌شود، چون شیت اول گاهی فقط سربرگ یا راهنماست.
     */
    fun parseXlsx(inputStream: InputStream): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        val sheets = mutableMapOf<String, ByteArray>()

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name.equals("xl/sharedStrings.xml", ignoreCase = true) -> {
                        sharedStrings.addAll(parseSharedStrings(zip))
                    }
                    name.startsWith("xl/worksheets/", ignoreCase = true) &&
                            name.endsWith(".xml", ignoreCase = true) -> {
                        sheets[name] = zip.readBytes()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (sheets.isEmpty()) return emptyList()

        // ترتیب طبیعی شیت‌ها: sheet1, sheet2, ... (نه ترتیب الفبایی که sheet10 را دوم می‌گذارد)
        val ordered = sheets.keys.sortedBy { key ->
            Regex("""(\d+)\.xml$""").find(key)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
        }

        var best: List<List<String>> = emptyList()
        for (key in ordered) {
            val rows = try {
                parseSheetXml(ByteArrayInputStream(sheets[key]!!), sharedStrings)
            } catch (e: Exception) {
                emptyList()
            }
            // شیتی که بیشترین سلول پرشده را دارد، شیت داده است
            if (countNonEmptyCells(rows) > countNonEmptyCells(best)) {
                best = rows
            }
        }
        return best
    }

    private fun countNonEmptyCells(rows: List<List<String>>): Int =
        rows.sumOf { row -> row.count { it.isNotBlank() } }

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val list = mutableListOf<String>()
        val parser = org.kxml2.io.KXmlParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        var inText = false
        // متن آوایی (rPh) جزو مقدار سلول نیست
        var inPhonetic = false
        val currentText = StringBuilder()

        // هر <si> یک رشته است، حتی وقتی قالب‌بندی چندتکه (<r><t>…</t></r>…) دارد.
        // قبلاً هر <t> یک رشته جدا شمرده می‌شد؛ یک سلول چندتکه ایندکس همه
        // رشته‌های بعدی را جابه‌جا می‌کرد و نماد ردیف‌ها عوض می‌شد.
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when {
                        parser.name.equals("si", ignoreCase = true) -> currentText.clear()
                        parser.name.equals("rPh", ignoreCase = true) -> inPhonetic = true
                        parser.name.equals("t", ignoreCase = true) -> inText = !inPhonetic
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when {
                        parser.name.equals("t", ignoreCase = true) -> inText = false
                        parser.name.equals("rPh", ignoreCase = true) -> inPhonetic = false
                        parser.name.equals("si", ignoreCase = true) -> list.add(currentText.toString())
                    }
                }
            }
            eventType = parser.next()
        }
        return list
    }

    private fun parseSheetXml(stream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = org.kxml2.io.KXmlParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        val currentRowMap = mutableMapOf<Int, String>()
        var currentCellRef: String? = null
        var cellType: String? = null
        val cellValue = StringBuilder()
        var insideVal = false
        var insideTextTag = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    if (name.equals("row", ignoreCase = true)) {
                        currentRowMap.clear()
                    } else if (name.equals("c", ignoreCase = true)) {
                        currentCellRef = parser.getAttributeValue(null, "r")
                        cellType = parser.getAttributeValue(null, "t")
                        cellValue.clear()
                    } else if (name.equals("v", ignoreCase = true)) {
                        insideVal = true
                    } else if (name.equals("t", ignoreCase = true)) {
                        insideTextTag = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideVal || insideTextTag) {
                        cellValue.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    if (name.equals("v", ignoreCase = true)) {
                        insideVal = false
                    } else if (name.equals("t", ignoreCase = true)) {
                        insideTextTag = false
                    } else if (name.equals("c", ignoreCase = true)) {
                        val raw = cellValue.toString().trim()
                        val resolvedText = if (cellType == "s") {
                            val idx = raw.toIntOrNull()
                            if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                        } else {
                            raw
                        }
                        // Map cell to exact 0-indexed column based on cell reference (e.g., A2 -> 0, F2 -> 5)
                        val colIndex = if (currentCellRef != null) colLettersToIndex(currentCellRef) else currentRowMap.size
                        if (colIndex >= 0) {
                            currentRowMap[colIndex] = resolvedText
                        }
                    } else if (name.equals("row", ignoreCase = true)) {
                        if (currentRowMap.isNotEmpty()) {
                            val maxCol = currentRowMap.keys.maxOrNull() ?: 0
                            // Ensure row has at least 6 columns (index 0 to 5 for A to F)
                            val width = maxOf(maxCol + 1, 6)
                            val rowList = (0 until width).map { currentRowMap[it] ?: "" }
                            rows.add(rowList)
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return rows
    }

    /**
     * Extracts portfolio items from rows, prioritizing the user's explicit specification
     * (Symbol in Column A starting at A2, Value in Column F) with dynamic fallback.
     */
    /**
     * سربرگ را برای نقش «مبنای قیمت خرید» امتیاز می‌دهد. `null` یعنی اصلاً نامزد نیست.
     *
     * چرا امتیاز و نه اولین تطابق: خروجی کارگزاری چند ستونِ هم‌خانواده دارد و
     * انتخاب اشتباه بی‌صدا عدد سود را جابه‌جا می‌کند. مشخصاً ستون «سر به سر»
     * کارمزد فروش را در خودش دارد، در حالی که ستون «ارزش فعلی» هم همان کارمزد
     * را از قبل کم کرده؛ کم کردن این دو از هم یعنی کارمزد فروش دو بار حساب شود.
     * ستون «میانگین خرید با لحاظ کارمزد» بهای تمام‌شده واقعی است و همان چیزی
     * است که ستون «سود و زیان فعلی» خود کارگزاری از آن ساخته می‌شود.
     */
    fun scoreAveragePriceHeader(rawHeader: String): Int? {
        val header = rawHeader.trim().replace(" ", "").replace("\u200C", "")
        val isCandidate = header.contains("سربهسر") ||
                header.contains("میانگینخرید") ||
                header.contains("قیمتخرید") ||
                header.equals("avgprice", ignoreCase = true)
        if (!isCandidate) return null
        // ستون‌های میانگین فروش مبنای خرید نیستند.
        if (header.contains("فروش")) return null

        var score = 0
        // بهای تمام‌شده با کارمزد، دقیقاً مبنای «سود و زیان فعلی» کارگزاری.
        if (header.contains("بالحاظکارمزد")) score += 4
        // «سر به سر» مبنای درستی است وقتی چیز بهتری نباشد.
        if (header.contains("سربهسر")) score += 1
        // نسخه تعدیل‌نشده سود نقدی مجمع را نادیده می‌گیرد.
        if (header.contains("تعدیلنشده")) score -= 3
        // دوره جاری نگه‌داری، نه میانگین مادام‌العمر.
        if (header.contains("درآخریندوره")) score += 1
        // میانگین خرید «روز» مبنای سبد نیست.
        if (header.contains("روز")) score -= 4
        return score
    }

    /**
     * بهترین ستون مبنای خرید را انتخاب می‌کند. ستونی که در هیچ ردیف داده عددی
     * ندارد نامزد نیست، وگرنه ستون‌های همیشه‌صفرِ کارگزاری برنده می‌شدند.
     */
    private fun pickAveragePriceColumn(
        headerRow: List<String>,
        dataRows: List<List<String>>
    ): Int {
        var bestCol = -1
        var bestScore = Int.MIN_VALUE
        for (j in headerRow.indices) {
            val score = scoreAveragePriceHeader(headerRow[j]) ?: continue
            if (dataRows.none { parseNumber(it.getOrNull(j) ?: "") > 0.0 }) continue
            if (score > bestScore) {
                bestScore = score
                bestCol = j
            }
        }
        return bestCol
    }

    /**
     * نرخ کارمزد فروشِ نهفته در ستون «ارزش فعلی» را از خود فایل در می‌آورد.
     *
     * کارگزاری ارزش را خالص می‌دهد (`تعداد × قیمت × (۱ − کارمزد)`)، پس نسبت
     * همین دو عدد نرخ واقعی همان نماد را می‌دهد بدون اینکه چیزی در کد فرض شود.
     * هر نسبتی خارج از بازه منطقی یعنی ستون‌ها آن چیزی نیستند که فکر می‌کنیم،
     * و در آن صورت ۱٫۰ برمی‌گردد تا رفتار قبلی حفظ شود. سقفِ ۱٫۰ هم لازم است:
     * ارزشِ بزرگ‌تر از تعداد × قیمت یعنی ستون ارزش چیز دیگری را می‌شمارد.
     */
    fun netValueRatio(valueRial: Double, quantity: Long, priceRial: Double): Double {
        if (quantity <= 0L || priceRial <= 0.0 || valueRial <= 0.0) return 1.0
        val gross = quantity.toDouble() * priceRial
        if (gross <= 0.0) return 1.0
        val ratio = valueRial / gross
        return if (ratio in MIN_NET_VALUE_RATIO..1.0) ratio else 1.0
    }

    fun extractPortfolioItems(
        rows: List<List<String>>,
        knownCategories: Map<String, AssetCategory>
    ): List<PortfolioEntity> {
        if (rows.isEmpty()) return emptyList()

        var symbolCol = -1
        var valueCol = -1
        var quantityCol = -1
        var priceCol = -1
        var averagePriceCol = -1
        var headerRowIndex = -1

        // Specification Match: Check if Column A (0) starting at cell A2 (row index 1)
        // and Column F (5) match user's brokerage pattern (A: Symbol, C: Quantity, F: Value, Q: Breakeven/Average Price)
        val hasColAColFPattern = rows.size > 1 && rows.drop(1).any { row ->
            val sym = cleanSymbolName(row.getOrNull(0) ?: "")
            val valNum = parseNumber(row.getOrNull(5) ?: "")
            sym.isNotEmpty() &&
                    !sym.contains("جمع") &&
                    !sym.contains("مجموع") &&
                    !sym.contains("مانده") &&
                    valNum > 0.0
        }

        if (hasColAColFPattern) {
            symbolCol = 0 // ستون A: نام نماد
            valueCol = 5  // ستون F: ارزش فعلی
            quantityCol = 2 // ستون C: تعداد دارایی
            headerRowIndex = 0 // سطر ۰ سربرگ است، داده از سطر ۱ (A2) شروع می‌شود

            // ستون‌های اختیاری از روی سربرگ پیدا می‌شوند، نه ایندکس ثابت.
            // قبلاً ستون Q (ایندکس ۱۶) برای «قیمت سر به سر» هاردکد شده بود؛
            // اگر کارگزاری ستونی جابه‌جا می‌کرد، عدد یک ستون دیگر خوانده می‌شد.
            val firstRow = rows.firstOrNull() ?: emptyList()
            for (j in firstRow.indices) {
                val header = firstRow[j].trim().replace(" ", "").replace("‌", "")
                if (priceCol == -1 && (
                        header.contains("آخرینقیمت") ||
                        header.contains("قیمتپایانی") ||
                        header.equals("price", ignoreCase = true)
                    )
                ) {
                    priceCol = j
                }
            }
            averagePriceCol = pickAveragePriceColumn(firstRow, rows.drop(1))

            // اگر سربرگ چیزی نگفت، به همان ستون Q برمی‌گردیم — ولی فقط وقتی
            // واقعاً وجود دارد و عددی در آن هست، نه کورکورانه.
            if (averagePriceCol == -1) {
                val hasUsableColQ = rows.drop(1).any { row ->
                    parseNumber(row.getOrNull(16) ?: "") > 0.0
                }
                if (hasUsableColQ) averagePriceCol = 16
            }
        } else {
            // General dynamic detection based on headers
            for (i in 0 until minOf(rows.size, 10)) {
                val row = rows[i]
                for (j in row.indices) {
                    val header = row[j].trim().replace(" ", "").replace("‌", "")
                    if (symbolCol == -1 && (
                            header.contains("نماد") ||
                            header.contains("دارایی") ||
                            header.equals("symbol", ignoreCase = true) ||
                            header.contains("نامسهم")
                        )
                    ) {
                        symbolCol = j
                    }
                    if (valueCol == -1 && (
                            header.contains("ارزشروز") ||
                            header.contains("ارزشفعلی") ||
                            header.contains("ارزشکل") ||
                            header.contains("خالصدارایی") ||
                            header.contains("ارزش") ||
                            header.contains("مبلغروز") ||
                            header.contains("مبلغکل") ||
                            header.equals("value", ignoreCase = true) ||
                            header.equals("marketvalue", ignoreCase = true)
                        )
                    ) {
                        valueCol = j
                    }
                    if (quantityCol == -1 && (
                            header.contains("تعداد") ||
                            header.contains("حجم") ||
                            header.contains("مانده") ||
                            header.equals("quantity", ignoreCase = true)
                        )
                    ) {
                        quantityCol = j
                    }
                    if (priceCol == -1 && (
                            header.contains("آخرینقیمت") ||
                            header.contains("قیمتپایانی") ||
                            header.equals("price", ignoreCase = true)
                        )
                    ) {
                        priceCol = j
                    }
                }

                if (symbolCol != -1 && valueCol != -1) {
                    headerRowIndex = i
                    break
                }
            }

            if (headerRowIndex != -1) {
                averagePriceCol = pickAveragePriceColumn(
                    rows[headerRowIndex],
                    rows.drop(headerRowIndex + 1)
                )
            }

            // Fallbacks for known brokerages
            if (symbolCol == -1) {
                symbolCol = if (rows.firstOrNull()?.size ?: 0 > 2) 2 else 0
            }
            if (valueCol == -1) {
                val sampleRow = rows.getOrNull(headerRowIndex + 1) ?: rows.firstOrNull() ?: emptyList()
                for (c in sampleRow.indices) {
                    if (c != symbolCol) {
                        val num = parseNumber(sampleRow[c])
                        if (num > 100_000) {
                            valueCol = c
                            break
                        }
                    }
                }
                if (valueCol == -1) valueCol = if (sampleRow.size > 5) 5 else minOf(sampleRow.size - 1, 1)
            }
        }

        val startRow = if (headerRowIndex != -1) headerRowIndex + 1 else 0
        val result = mutableListOf<PortfolioEntity>()

        for (r in startRow until rows.size) {
            val row = rows[r]
            if (row.size <= symbolCol) continue

            val rawSymbol = row.getOrNull(symbolCol)?.trim() ?: ""
            if (rawSymbol.isEmpty() ||
                rawSymbol.contains("جمع") ||
                rawSymbol.contains("مجموع") ||
                rawSymbol.contains("مانده ریالی") ||
                rawSymbol.contains("مانده کل") ||
                rawSymbol.equals("Total", ignoreCase = true)
            ) {
                continue
            }

            val cleanSymbol = cleanSymbolName(rawSymbol)
            if (cleanSymbol.isEmpty()) continue

            val rawValue = if (valueCol in row.indices) row[valueCol] else "0"
            val value = parseNumber(rawValue)
            if (value <= 0.0) continue

            val quantity = if (quantityCol != -1 && quantityCol in row.indices) parseLong(row[quantityCol]) else 0L
            val price = if (priceCol != -1 && priceCol in row.indices) parseNumber(row[priceCol]) else {
                if (quantity > 0) value / quantity else 0.0
            }

            val avgPrice = if (averagePriceCol != -1 && averagePriceCol in row.indices) {
                parseNumber(row[averagePriceCol])
            } else 0.0

            val finalAveragePrice = if (avgPrice > 0.0) avgPrice else price

            val category = knownCategories[cleanSymbol]
                ?: DefaultEtfDatabase.getCategoryForSymbol(cleanSymbol)

            result.add(
                PortfolioEntity(
                    symbol = cleanSymbol,
                    assetCategory = category,
                    currentValue = value,
                    quantity = quantity,
                    lastPrice = price,
                    averagePrice = finalAveragePrice,
                    netValueRatio = netValueRatio(value, quantity, price)
                )
            )
        }

        return result
    }

    /**
     * نتیجه خواندن فایل دسته‌بندی صندوق‌ها.
     *
     * ردیف‌هایی که نوعشان خوانا نبود جدا گزارش می‌شوند تا بی‌صدا «سهامی» فرض
     * نشوند. این جدول بر حدس‌های داخلی اپ اولویت دارد، پس یک ردیفِ ناخوانا
     * می‌توانست دستهٔ درستِ یک صندوق درآمد ثابت را بازنویسی کند و کل وزن‌های
     * هدف را جابه‌جا کند.
     */
    data class FundCategoryImport(
        val categories: List<FundCategoryEntity> = emptyList(),
        val unrecognizedSymbols: List<String> = emptyList()
    )

    /**
     * Extracts symbol -> category mappings from the category file.
     */
    fun extractFundCategories(rows: List<List<String>>): FundCategoryImport {
        if (rows.isEmpty()) return FundCategoryImport()

        var symbolCol = 0
        var categoryCol = 1
        var startRow = 0

        val firstRow = rows.firstOrNull() ?: emptyList()
        for (j in firstRow.indices) {
            val header = firstRow[j].trim()
            if (header.contains("نماد") || header.equals("symbol", ignoreCase = true)) {
                symbolCol = j
                startRow = 1
            }
            if (header.contains("نوع") || header.contains("دسته") || header.contains("گروه") || header.equals("category", ignoreCase = true)) {
                categoryCol = j
                startRow = 1
            }
        }

        val result = mutableListOf<FundCategoryEntity>()
        val unrecognized = mutableListOf<String>()

        for (r in startRow until rows.size) {
            val row = rows[r]
            val sym = row.getOrNull(symbolCol)?.trim() ?: continue
            val catRaw = row.getOrNull(categoryCol)?.trim() ?: ""
            if (sym.isEmpty()) continue

            val cleanSym = cleanSymbolName(sym)
            if (cleanSym.isEmpty()) continue

            // نوع ناخوانا رد می‌شود، نه اینکه «سهامی» فرض شود
            val category = AssetCategory.fromStringOrNull(catRaw)
            if (category == null) {
                unrecognized.add(cleanSym)
                continue
            }

            result.add(
                FundCategoryEntity(
                    symbol = cleanSym,
                    category = category,
                    fundName = "صندوق $cleanSym"
                )
            )
        }
        return FundCategoryImport(categories = result, unrecognizedSymbols = unrecognized)
    }

    private fun cleanSymbolName(raw: String): String {
        return raw.replace(Regex("""[()\[\]{}،,"]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
