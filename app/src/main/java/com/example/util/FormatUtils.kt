package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class LoveDaysInfo(
    val totalDays: Long,
    val years: Int,
    val months: Int,
    val days: Int,
    val startDateFormatted: String,
    val startYear: Int = 0,
    val startMonth: Int = 1,
    val startDay: Int = 1,
    val formattedBreakdown: String = ""
)

object FormatUtils {
    /**
     * Formats amount as MMK (e.g., 25,000 MMK)
     */
    fun formatMmk(amount: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
        return "${formatter.format(amount)} MMK"
    }

    /**
     * Formats date from year, month (1-12), and day into "yyyy-MM-dd"
     */
    fun formatDateString(year: Int, month: Int, day: Int): String {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }

    /**
     * Calculates total days and breakdown into years, months, and days
     */
    fun calculateLoveDays(startDateStr: String): LoveDaysInfo? {
        if (startDateStr.isBlank()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startDate = sdf.parse(startDateStr) ?: return null
            val now = Date()

            val startCal = Calendar.getInstance().apply {
                time = startDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val nowCal = Calendar.getInstance().apply {
                time = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffMillis = nowCal.timeInMillis - startCal.timeInMillis
            val totalDays = (diffMillis / (1000L * 60 * 60 * 24)).coerceAtLeast(0)

            val startYear = startCal.get(Calendar.YEAR)
            val startMonth = startCal.get(Calendar.MONTH) + 1
            val startDay = startCal.get(Calendar.DAY_OF_MONTH)

            var years = nowCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)
            var months = nowCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH)
            var days = nowCal.get(Calendar.DAY_OF_MONTH) - startCal.get(Calendar.DAY_OF_MONTH)

            if (days < 0) {
                months--
                val prevMonth = (nowCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                days += prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
            }
            if (months < 0) {
                years--
                months += 12
            }

            val y = years.coerceAtLeast(0)
            val m = months.coerceAtLeast(0)
            val d = days.coerceAtLeast(0)

            val displayFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            val formattedDate = displayFormat.format(startDate)

            val breakdownParts = mutableListOf<String>()
            breakdownParts.add("$y ${if (y == 1) "Year" else "Years"}")
            breakdownParts.add("$m ${if (m == 1) "Month" else "Months"}")
            breakdownParts.add("$d ${if (d == 1) "Day" else "Days"}")
            val breakdownStr = breakdownParts.joinToString(", ")

            LoveDaysInfo(
                totalDays = totalDays,
                years = y,
                months = m,
                days = d,
                startDateFormatted = formattedDate,
                startYear = startYear,
                startMonth = startMonth,
                startDay = startDay,
                formattedBreakdown = breakdownStr
            )
        } catch (e: Exception) {
            null
        }
    }
}
