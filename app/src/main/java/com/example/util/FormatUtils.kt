package com.example.util

import com.example.data.model.Expense
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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

data class LoveMilestone(
    val title: String,
    val targetDays: Long,
    val daysLeft: Long,
    val isAchieved: Boolean,
    val estimatedDateFormatted: String,
    val celebrationEmoji: String = "💖"
) {
    val icon: String get() = celebrationEmoji
    val daysRemaining: Long get() = daysLeft
}

object FormatUtils {
    private val mmkFormatter = object : ThreadLocal<NumberFormat>() {
        override fun initialValue(): NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    /**
     * Formats amount as MMK (e.g., 25,000 MMK)
     */
    fun formatMmk(amount: Double): String {
        val formatter = mmkFormatter.get() ?: NumberFormat.getNumberInstance(Locale.US).apply {
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
     * Parses a "yyyy-MM-dd" date string strictly into (year, month 1..12, dayOfMonth).
     */
    private fun parseYmd(dateStr: String): Triple<Int, Int, Int>? {
        val parts = dateStr.trim().split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (year < 1900 || month !in 1..12 || day !in 1..31) return null
        val checkCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            isLenient = false
            clear()
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return try {
            // Trigger strict calendar field validation
            Triple(
                checkCal.get(Calendar.YEAR),
                checkCal.get(Calendar.MONTH) + 1,
                checkCal.get(Calendar.DAY_OF_MONTH)
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Calculates total days (Day 1 inclusive on the anniversary start date, immune to DST shifts)
     * and calendar breakdown into years, months, and days.
     */
    fun calculateLoveDays(startDateStr: String): LoveDaysInfo? {
        val nowCal = Calendar.getInstance()
        return calculateLoveDaysForDate(
            startDateStr = startDateStr,
            todayYear = nowCal.get(Calendar.YEAR),
            todayMonth = nowCal.get(Calendar.MONTH) + 1,
            todayDay = nowCal.get(Calendar.DAY_OF_MONTH)
        )
    }

    internal fun calculateLoveDaysForDate(
        startDateStr: String,
        todayYear: Int,
        todayMonth: Int,
        todayDay: Int
    ): LoveDaysInfo? {
        if (startDateStr.isBlank()) return null
        return try {
            val (startYear, startMonth, startDay) = parseYmd(startDateStr) ?: return null
            val utc = TimeZone.getTimeZone("UTC")

            val startUtc = Calendar.getInstance(utc).apply {
                clear()
                set(startYear, startMonth - 1, startDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val nowUtc = Calendar.getInstance(utc).apply {
                clear()
                set(todayYear, todayMonth - 1, todayDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffMillis = nowUtc.timeInMillis - startUtc.timeInMillis
            val elapsedDays = diffMillis / 86_400_000L
            // Love day counters treat the anniversary start date itself as Day 1
            val totalDays = if (elapsedDays >= 0L) elapsedDays + 1L else 0L

            var years = todayYear - startYear
            var months = todayMonth - startMonth
            var days = todayDay - startDay

            if (days < 0) {
                months--
                val prevMonth = (nowUtc.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                days += prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
            }
            if (months < 0) {
                years--
                months += 12
            }

            val (y, m, d) = if (elapsedDays < 0L) {
                Triple(0, 0, 0)
            } else {
                Triple(years.coerceAtLeast(0), months.coerceAtLeast(0), days.coerceAtLeast(0))
            }

            val displayFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).apply {
                timeZone = utc
            }
            val formattedDate = displayFormat.format(startUtc.time)

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
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Calculates upcoming and achieved couple love milestones (100 days, 1 year, 500 days, etc.)
     */
    fun calculateMilestones(startDateStr: String): Pair<LoveMilestone?, List<LoveMilestone>> {
        val nowCal = Calendar.getInstance()
        return calculateMilestonesForDate(
            startDateStr = startDateStr,
            todayYear = nowCal.get(Calendar.YEAR),
            todayMonth = nowCal.get(Calendar.MONTH) + 1,
            todayDay = nowCal.get(Calendar.DAY_OF_MONTH)
        )
    }

    internal fun calculateMilestonesForDate(
        startDateStr: String,
        todayYear: Int,
        todayMonth: Int,
        todayDay: Int
    ): Pair<LoveMilestone?, List<LoveMilestone>> {
        val loveDays = calculateLoveDaysForDate(startDateStr, todayYear, todayMonth, todayDay)
            ?: return null to emptyList()
        val currentDays = loveDays.totalDays
        val utc = TimeZone.getTimeZone("UTC")
        val startUtc = Calendar.getInstance(utc).apply {
            clear()
            set(loveDays.startYear, loveDays.startMonth - 1, loveDays.startDay, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val targets = listOf(
            Triple("100 Days 💕", 100L, "🎉"),
            Triple("200 Days 💌", 200L, "✨"),
            Triple("1st Anniversary 🎂", 365L, "💍"),
            Triple("500 Days 🌟", 500L, "💖"),
            Triple("2nd Anniversary 🥂", 730L, "🥂"),
            Triple("1,000 Days 👑", 1000L, "👑"),
            Triple("3rd Anniversary 🌺", 1095L, "🌺"),
            Triple("1,500 Days 🚀", 1500L, "🚀"),
            Triple("4th Anniversary 💫", 1461L, "💫"),
            Triple("5th Anniversary 🏆", 1826L, "🏆"),
            Triple("2,000 Days 💎", 2000L, "💎"),
            Triple("10th Anniversary 🏰", 3652L, "🏰")
        ).sortedBy { it.second }

        val displayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
            timeZone = utc
        }

        val milestones = targets.map { (title, days, emoji) ->
            val cal = (startUtc.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, (days - 1L).coerceAtLeast(0L).toInt())
            }
            val daysLeft = (days - currentDays).coerceAtLeast(0L)
            val isAchieved = currentDays >= days
            LoveMilestone(
                title = title,
                targetDays = days,
                daysLeft = daysLeft,
                isAchieved = isAchieved,
                estimatedDateFormatted = displayFormat.format(cal.time),
                celebrationEmoji = emoji
            )
        }

        val nextUpcoming = milestones.firstOrNull { !it.isAchieved }
        return nextUpcoming to milestones
    }

    /**
     * Generates a CSV export text string of the expenses.
     */
    fun generateCsvExport(expenses: List<Expense>, coupleCode: String): String {
        val sb = StringBuilder()
        sb.append("ID,Date,Category,Payer,SplitType,Amount_MMK,Note,Latitude,Longitude,CreatedAt\n")
        expenses.forEach { exp ->
            val cleanNote = exp.cleanNote.replace("\"", "\"\"")
            val split = exp.splitType.shortLabel
            val lat = exp.latitude?.toString() ?: ""
            val lng = exp.longitude?.toString() ?: ""
            sb.append("\"${exp.id}\",")
            sb.append("\"${exp.date}\",")
            sb.append("\"${exp.category}\",")
            sb.append("\"${exp.user_name}\",")
            sb.append("\"$split\",")
            sb.append("${exp.amount.toLong()},")
            sb.append("\"$cleanNote\",")
            sb.append("\"$lat\",")
            sb.append("\"$lng\",")
            sb.append("\"${exp.created_at ?: ""}\"\n")
        }
        return sb.toString()
    }
}
