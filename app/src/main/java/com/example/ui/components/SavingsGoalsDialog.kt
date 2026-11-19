package com.example.ui.components

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SavingsGoal
import com.example.util.FormatUtils

@Composable
fun SavingsGoalsDialog(
    goals: List<SavingsGoal>,
    currentUserName: String,
    onAddGoal: (String, Double, String, String) -> Unit,
    onDeposit: (String, Double, Boolean, String) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showCreateForm by remember { mutableStateOf(false) }
    var selectedGoalForDeposit by remember { mutableStateOf<SavingsGoal?>(null) }

    // New Goal Form State
    var newGoalTitle by remember { mutableStateOf("") }
    var newGoalTarget by remember { mutableStateOf("") }
    var newGoalDate by remember { mutableStateOf("") }

    // Deposit Form State
    var depositAmountText by remember { mutableStateOf("") }
    var logAsExpenseCheck by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Savings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Couple Wishlist & Savings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                if (!showCreateForm && selectedGoalForDeposit == null) {
                    IconButton(onClick = { showCreateForm = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New Goal", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showCreateForm) {
                    // Create New Goal Form
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("New Savings Target 🎯", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = newGoalTitle,
                                onValueChange = { newGoalTitle = it },
                                label = { Text("Goal Title (e.g., Trip to Bagan)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newGoalTarget,
                                onValueChange = { if (it.matches(Regex("""^\d*$"""))) newGoalTarget = it },
                                label = { Text("Target Amount (MMK)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newGoalDate,
                                onValueChange = { newGoalDate = it },
                                label = { Text("Target Date (Optional)") },
                                placeholder = { Text("e.g. Dec 2026") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showCreateForm = false }) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        val target = newGoalTarget.toDoubleOrNull() ?: 0.0
                                        if (newGoalTitle.isBlank() || target <= 0) {
                                            Toast.makeText(context, "Please enter title and target amount", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        onAddGoal(newGoalTitle, target, newGoalDate, "Favorite")
                                        newGoalTitle = ""
                                        newGoalTarget = ""
                                        newGoalDate = ""
                                        showCreateForm = false
                                    }
                                ) {
                                    Text("Create Goal")
                                }
                            }
                        }
                    }
                } else if (selectedGoalForDeposit != null) {
                    // Deposit Form
                    selectedGoalForDeposit?.let { goal ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Deposit to: ${goal.title} 🐷", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "Current: ${FormatUtils.formatMmk(goal.currentAmount)} / ${FormatUtils.formatMmk(goal.targetAmount)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = depositAmountText,
                                onValueChange = { if (it.matches(Regex("""^\d*$"""))) depositAmountText = it },
                                label = { Text("Deposit Amount (MMK)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = logAsExpenseCheck,
                                    onCheckedChange = { logAsExpenseCheck = it }
                                )
                                Text("Also log as shared expense", fontSize = 12.sp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { selectedGoalForDeposit = null }) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        val amount = depositAmountText.toDoubleOrNull() ?: 0.0
                                        if (amount <= 0) {
                                            Toast.makeText(context, "Enter a valid deposit amount", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        onDeposit(goal.id, amount, logAsExpenseCheck, currentUserName)
                                        depositAmountText = ""
                                        selectedGoalForDeposit = null
                                    }
                                ) {
                                    Text("Confirm Deposit")
                                }
                            }
                        }
                    }
                    }
                } else {
                    // Goals List
                    if (goals.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No couple savings goals yet.\nTap + to add your first wishlist item! 🏖️",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(goals) { goal ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (goal.isCompleted) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Text(
                                                    text = goal.title,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }

                                            IconButton(
                                                onClick = { onDeleteGoal(goal.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${FormatUtils.formatMmk(goal.currentAmount)} of ${FormatUtils.formatMmk(goal.targetAmount)}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${goal.progressPercentage.toInt()}%",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (goal.isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        LinearProgressIndicator(
                                            progress = { goal.progressPercentage / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = if (goal.isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (goal.isCompleted) "Goal Achieved! 🎉" else if (goal.targetDate.isNotBlank()) "Target: ${goal.targetDate}" else "Remaining: ${FormatUtils.formatMmk(goal.remainingAmount)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            if (!goal.isCompleted) {
                                                OutlinedButton(
                                                    onClick = { selectedGoalForDeposit = goal },
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(Icons.Default.VolunteerActivism, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Deposit", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
