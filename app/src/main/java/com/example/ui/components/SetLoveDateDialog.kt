package com.example.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.FormatUtils
import java.text.DateFormatSymbols
import java.util.Calendar

@Composable
fun SetLoveDateDialog(
    initialDateStr: String,
    onDateSaved: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentCal = Calendar.getInstance()

    // Parse existing date or default to 1 year ago today
    val initialInfo = remember(initialDateStr) {
        FormatUtils.calculateLoveDays(initialDateStr)
    }

    var selectedYear by remember {
        mutableIntStateOf(initialInfo?.startYear?.takeIf { it > 1900 } ?: (currentCal.get(Calendar.YEAR) - 1))
    }
    var selectedMonth by remember {
        mutableIntStateOf(initialInfo?.startMonth?.takeIf { it in 1..12 } ?: (currentCal.get(Calendar.MONTH) + 1))
    }
    var selectedDay by remember {
        mutableIntStateOf(initialInfo?.startDay?.takeIf { it in 1..31 } ?: currentCal.get(Calendar.DAY_OF_MONTH))
    }

    val monthNames = remember { DateFormatSymbols().months.filter { it.isNotBlank() } }

    val currentDateStr by remember {
        derivedStateOf {
            FormatUtils.formatDateString(selectedYear, selectedMonth, selectedDay)
        }
    }

    val livePreview by remember {
        derivedStateOf {
            FormatUtils.calculateLoveDays(currentDateStr)
        }
    }

    // Safe native DatePicker launcher created on demand
    val openSystemDatePicker = remember(context, selectedYear, selectedMonth, selectedDay) {
        {
            runCatching {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        selectedYear = year
                        selectedMonth = month + 1
                        selectedDay = dayOfMonth
                    },
                    selectedYear,
                    (selectedMonth - 1).coerceIn(0, 11),
                    selectedDay.coerceIn(1, 31)
                ).show()
            }
            Unit
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Set Love Anniversary",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Select Day, Month & Year 💕",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Calendar Picker Fast Button
                OutlinedButton(
                    onClick = openSystemDatePicker,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_calendar_picker_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick from Calendar Dialog")
                }

                Text(
                    text = "Or choose Date, Month & Year below:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Dropdown selectors row: Day, Month, Year
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Day Selector
                    DayDropdown(
                        selectedDay = selectedDay,
                        selectedMonth = selectedMonth,
                        selectedYear = selectedYear,
                        onDaySelected = { selectedDay = it },
                        modifier = Modifier.weight(1f)
                    )

                    // Month Selector
                    MonthDropdown(
                        selectedMonth = selectedMonth,
                        monthNames = monthNames,
                        onMonthSelected = { selectedMonth = it },
                        modifier = Modifier.weight(1.3f)
                    )

                    // Year Selector
                    YearDropdown(
                        selectedYear = selectedYear,
                        onYearSelected = { selectedYear = it },
                        modifier = Modifier.weight(1.1f)
                    )
                }

                // Live Preview Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Live Love Days Preview",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val previewTotal = livePreview?.totalDays ?: 0L
                        Text(
                            text = "$previewTotal Days Together",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val y = livePreview?.years ?: 0
                        val m = livePreview?.months ?: 0
                        val d = livePreview?.days ?: 0

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PreviewMetricChip(value = y, label = if (y == 1) "Year" else "Years")
                            Text("•", color = MaterialTheme.colorScheme.primary)
                            PreviewMetricChip(value = m, label = if (m == 1) "Month" else "Months")
                            Text("•", color = MaterialTheme.colorScheme.primary)
                            PreviewMetricChip(value = d, label = if (d == 1) "Day" else "Days")
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "From: ${livePreview?.startDateFormatted ?: currentDateStr}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDateSaved(currentDateStr)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_anniversary_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Anniversary", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PreviewMetricChip(value: Int, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$value",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayDropdown(
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val maxDays = remember(selectedMonth, selectedYear) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    Box(modifier = modifier) {
        PickerField(
            title = "Day",
            value = selectedDay.toString(),
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (day in 1..maxDays) {
                DropdownMenuItem(
                    text = { Text("$day") },
                    onClick = {
                        onDaySelected(day)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MonthDropdown(
    selectedMonth: Int,
    monthNames: List<String>,
    onMonthSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentMonthName = monthNames.getOrElse(selectedMonth - 1) { "Month $selectedMonth" }

    Box(modifier = modifier) {
        PickerField(
            title = "Month",
            value = currentMonthName.take(4),
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            monthNames.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text("${index + 1} - $name") },
                    onClick = {
                        onMonthSelected(index + 1)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun YearDropdown(
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val years = remember { (currentYear downTo currentYear - 30).toList() }

    Box(modifier = modifier) {
        PickerField(
            title = "Year",
            value = selectedYear.toString(),
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            years.forEach { year ->
                DropdownMenuItem(
                    text = { Text("$year") },
                    onClick = {
                        onYearSelected(year)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PickerField(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
