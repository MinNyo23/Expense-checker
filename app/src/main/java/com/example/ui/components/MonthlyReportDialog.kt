package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.UserSession
import com.example.data.model.Expense
import com.example.ui.viewmodel.DashboardSummary
import com.example.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonthlyReportDialog(
    summary: DashboardSummary,
    expenses: List<Expense>,
    userSession: UserSession,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentMonthName = remember {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    }

    val loveDays = remember(userSession.relationshipStartDate) {
        FormatUtils.calculateLoveDays(userSession.relationshipStartDate)
    }

    val shareableText = remember(summary, expenses, userSession, currentMonthName) {
        buildString {
            append("💕 Couple Expense Report ($currentMonthName)\n")
            append("👫 Coupled: ${userSession.userName} (Code: ${userSession.coupleCode})\n")
            if (loveDays != null) {
                append("💖 Days in Love: ${loveDays.totalDays} Days (${loveDays.years}y ${loveDays.months}m ${loveDays.days}d)\n")
            }
            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("💰 Total Spent This Month: ${FormatUtils.formatMmk(summary.monthTotal)}\n")
            append("🎯 Monthly Budget: ${FormatUtils.formatMmk(summary.monthlyBudget)} (${summary.budgetUsedPercent.toInt()}% used)\n")
            append("⚖️ Balance: ${summary.balanceSummaryText}\n")
            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("🏷️ Top Spending Categories:\n")
            summary.categories.take(4).forEach { cat ->
                append(" • ${cat.category.title}: ${FormatUtils.formatMmk(cat.totalAmount)} (${cat.percentage.toInt()}%)\n")
            }
            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("Shared with love via Couple Expense App 💕")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Monthly Spending Report", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card preview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(currentMonthName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${summary.totalCount} entries",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = FormatUtils.formatMmk(summary.monthTotal),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "Budget: ${FormatUtils.formatMmk(summary.monthlyBudget)} • Remaining: ${FormatUtils.formatMmk(summary.budgetRemaining)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "⚖️ ${summary.balanceSummaryText}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Share and Export Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareableText)
                                type = "text/plain"
                            }
                            runCatching {
                                val shareIntent = Intent.createChooser(sendIntent, "Share Monthly Report")
                                context.startActivity(shareIntent)
                            }.onFailure {
                                android.widget.Toast.makeText(context, "No sharing app found on this device", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Summary", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val csvContent = FormatUtils.generateCsvExport(expenses, userSession.coupleCode)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, csvContent)
                                putExtra(Intent.EXTRA_SUBJECT, "Couple_Expenses_${userSession.coupleCode}.csv")
                                type = "text/csv"
                            }
                            runCatching {
                                val shareIntent = Intent.createChooser(sendIntent, "Export Expenses CSV")
                                context.startActivity(shareIntent)
                            }.onFailure {
                                android.widget.Toast.makeText(context, "No export app found on this device", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export CSV", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Close")
            }
        }
    )
}
