package com.example.domain.export

import com.example.data.model.DepositWithdrawalOrder
import com.example.data.model.LockedHoldSummary
import com.example.data.model.RebalanceAction
import com.example.data.model.SymbolRebalanceAlert
import com.example.domain.calculator.WithdrawalPlanner
import kotlin.math.roundToLong

/**
 * خروجی گرفتن از سفارش‌ها — بدون وابستگی به اندروید، تا قابل تست باشد.
 *
 * دو قالب:
 *  - **CSV** برای باز کردن در اکسل و نگه داشتن سابقه
 *  - **متن** برای فرستادن در پیام‌رسان یا یادداشت کنار دست هنگام ثبت سفارش
 *
 * همه مبالغ به **تومان** نوشته می‌شوند، چون کاربر با تومان کار می‌کند و
 * ورودی کارگزاری هم تومانی است. داده داخلی ریالی است و اینجا تبدیل می‌شود.
 */
object OrderExport {

    /**
     * اکسل ویندوزی بدون BOM، فایل UTF-8 را با کدپیج محلی باز می‌کند و متن
     * فارسی به هم می‌ریزد. این سه بایت جلوی آن را می‌گیرد.
     */
    const val UTF8_BOM = "﻿"

    private const val HEADER =
        "نماد,عمل,مبلغ (تومان),تعداد واحد,وزن فعلی %,وزن هدف %,توضیح"

    // ------------------------------------------------------------------
    // سیگنال‌های ریبلنس
    // ------------------------------------------------------------------

    fun signalsToCsv(alerts: List<SymbolRebalanceAlert>): String {
        val rows = alerts
            .filter { it.action != RebalanceAction.HOLD }
            .map { alert ->
                listOf(
                    alert.symbol,
                    alert.action.persianLabel,
                    toman(alert.actionAmount),
                    alert.estimatedUnits.toString(),
                    fmt1(alert.currentWeightPercent),
                    fmt1(alert.targetWeightPercent),
                    alert.minOrderNotice.orEmpty()
                ).joinToString(",") { escape(it) }
            }
        return buildCsv(rows)
    }

    /**
     * @param lockedHold وقتی نمادی قفل نگه‌داری دارد، فهرست سفارش‌ها فقط خرید
     *        است و بدون این خط، خواننده متن نمی‌فهمد پولِ این خریدها از کجا
     *        می‌آید. پس مبلغ نقد لازم بالای فهرست نوشته می‌شود.
     */
    fun signalsToText(
        alerts: List<SymbolRebalanceAlert>,
        title: String,
        lockedHold: LockedHoldSummary? = null
    ): String {
        val actionable = alerts.filter { it.action != RebalanceAction.HOLD }
        val lockedNote = lockedHold
            ?.takeIf { it.requiredCashRial > 0.0 }
            ?.let {
                "🔒 ${it.bindingSymbols.joinToString("، ")} قفل نگه‌داری دارند و فروخته نمی‌شوند.\n" +
                    "پول نقد لازم برای تعادل: ${toman(it.requiredCashRial)} تومان"
            }

        if (actionable.isEmpty()) {
            return if (lockedNote != null) {
                "$title\n\n$lockedNote"
            } else {
                "$title\nسفارشی برای اجرا وجود ندارد."
            }
        }

        return buildString {
            appendLine(title)
            appendLine()
            if (lockedNote != null) {
                appendLine(lockedNote)
                appendLine()
            }
            for (alert in actionable) {
                append("• ${alert.action.persianLabel} ${alert.symbol}: ")
                append("${toman(alert.actionAmount)} تومان")
                if (alert.estimatedUnits > 0L) append(" (≈ ${alert.estimatedUnits} واحد)")
                appendLine()
                alert.minOrderNotice?.let { appendLine("   ⚠ $it") }
            }
        }.trimEnd()
    }

    // ------------------------------------------------------------------
    // سفارش‌های واریز و برداشت
    // ------------------------------------------------------------------

    fun transactionOrdersToCsv(orders: List<DepositWithdrawalOrder>): String {
        val rows = orders
            .filter { it.action != RebalanceAction.HOLD }
            .map { order ->
                listOf(
                    order.symbol,
                    order.action.persianLabel,
                    toman(order.absoluteAmount),
                    order.estimatedUnits.toString(),
                    fmt1(order.currentWeight),
                    fmt1(order.targetWeight),
                    order.minOrderNotice.orEmpty()
                ).joinToString(",") { escape(it) }
            }
        return buildCsv(rows)
    }

    // ------------------------------------------------------------------
    // برنامه برداشت
    // ------------------------------------------------------------------

    fun withdrawalPlanToCsv(plan: WithdrawalPlanner.Plan): String {
        val rows = plan.legs.map { leg ->
            val note = when {
                leg.closesPosition -> "این نماد کامل بسته می‌شود"
                leg.leavesStub -> "باقیمانده زیر کف اقتصادی ۱۰ میلیون تومان"
                else -> ""
            }
            listOf(
                leg.symbol,
                "فروش",
                toman(leg.amountRial),
                leg.estimatedUnits.toString(),
                toman(leg.valueBeforeRial),
                toman(leg.valueAfterRial),
                note
            ).joinToString(",") { escape(it) }
        }
        return buildString {
            append(UTF8_BOM)
            appendLine("نماد,عمل,مبلغ فروش (تومان),تعداد واحد,ارزش قبل (تومان),ارزش بعد (تومان),توضیح")
            rows.forEach { appendLine(it) }
        }
    }

    fun withdrawalPlanToText(plan: WithdrawalPlanner.Plan, title: String): String {
        if (plan.legs.isEmpty()) {
            return "$title\n${plan.shortfallNotice ?: "برنامه‌ای تولید نشد."}"
        }

        return buildString {
            appendLine(title)
            appendLine("مبلغ پوشش‌داده‌شده: ${toman(plan.coveredRial)} تومان")
            appendLine()
            for (leg in plan.legs) {
                append("• فروش ${leg.symbol}: ${toman(leg.amountRial)} تومان")
                if (leg.estimatedUnits > 0L) append(" (≈ ${leg.estimatedUnits} واحد)")
                appendLine()
                when {
                    leg.closesPosition -> appendLine("   ⚠ این نماد کامل بسته می‌شود")
                    leg.leavesStub -> appendLine("   ⚠ باقیمانده زیر کف اقتصادی می‌ماند")
                }
            }
            appendLine()
            appendLine("انحراف سبد پس از اجرا: ${fmt1(plan.postTrIndex)}٪")
            plan.shortfallNotice?.let { appendLine("⚠ $it") }
        }.trimEnd()
    }

    /** نام پیشنهادی فایل — تاریخ‌دار تا نسخه‌ها قاطی نشوند. */
    fun fileName(prefix: String, stamp: String): String = "$prefix-$stamp.csv"

    // ------------------------------------------------------------------

    private fun buildCsv(rows: List<String>): String = buildString {
        append(UTF8_BOM)
        appendLine(HEADER)
        rows.forEach { appendLine(it) }
    }

    /** مبلغ ریالی به عدد تومانی گردشده، بدون جداکننده تا اکسل عدد بخواند. */
    private fun toman(rial: Double): String = (rial / 10.0).roundToLong().toString()

    private fun fmt1(value: Double): String = ((value * 10.0).roundToLong() / 10.0).toString()

    /**
     * ویرگول یا نقل‌قول داخل یک سلول، ساختار CSV را می‌شکند.
     * نام صندوق‌های ایرانی گاهی ویرگول دارد («صندوق طلا، عیار»).
     */
    private fun escape(value: String): String {
        val clean = value.replace("\n", " ").trim()
        return if (clean.contains(',') || clean.contains('"')) {
            "\"" + clean.replace("\"", "\"\"") + "\""
        } else {
            clean
        }
    }
}
