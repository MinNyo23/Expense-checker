package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.ui.components.ExpenseItemCard
import com.example.ui.viewmodel.CoupleExpenseViewModel
import com.example.util.FormatUtils
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    viewModel: CoupleExpenseViewModel,
    onNavigateToMap: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val userSession by viewModel.userSession.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<ExpenseCategory?>(null) }
    var selectedPayerFilter by remember { mutableStateOf("ALL") } // "ALL", "ME", "PARTNER"
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    val filteredList = remember(expenses, searchQuery, selectedCategoryFilter, selectedPayerFilter, userSession.userName) {
        expenses.filter { exp ->
            val matchesQuery = searchQuery.isBlank() ||
                    exp.note.contains(searchQuery, ignoreCase = true) ||
                    exp.category.contains(searchQuery, ignoreCase = true) ||
                    exp.user_name.contains(searchQuery, ignoreCase = true) ||
                    exp.date.contains(searchQuery)

            val matchesCategory = selectedCategoryFilter == null ||
                    exp.category.equals(selectedCategoryFilter?.name, ignoreCase = true)

            val isMe = exp.user_name.equals(userSession.userName, ignoreCase = true)
            val matchesPayer = when (selectedPayerFilter) {
                "ME" -> isMe
                "PARTNER" -> !isMe
                else -> true
            }

            matchesQuery && matchesCategory && matchesPayer
        }
    }

    val totalFilteredSum = remember(filteredList) {
        filteredList.sumOf { it.amount }
    }
    val totalFilteredText = remember(totalFilteredSum) {
        com.example.util.FormatUtils.formatMmk(totalFilteredSum)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search notes, places, or dates...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search")
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_expense_input")
        )

        // Filters Row
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedPayerFilter == "ALL",
                onClick = { selectedPayerFilter = "ALL" },
                label = { Text("All Payers") }
            )
            FilterChip(
                selected = selectedPayerFilter == "ME",
                onClick = { selectedPayerFilter = "ME" },
                label = { Text("You") }
            )
            FilterChip(
                selected = selectedPayerFilter == "PARTNER",
                onClick = { selectedPayerFilter = "PARTNER" },
                label = { Text("Partner") }
            )

            // Category Filter Pills
            ExpenseCategory.entries.take(5).forEach { cat ->
                FilterChip(
                    selected = selectedCategoryFilter == cat,
                    onClick = {
                        selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                    },
                    label = { Text(cat.title) }
                )
            }
        }

        // Summary Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredList.size} items",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Total: $totalFilteredText",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Expense List
        if (filteredList.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No matching expenses found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = filteredList,
                    key = { idx, exp -> "hist_${exp.id}_$idx" },
                    contentType = { _, _ -> "expense" }
                ) { _, expense ->
                    ExpenseItemCard(
                        expense = expense,
                        currentUserName = userSession.userName,
                        onDeleteClick = { expenseToDelete = expense },
                        onLocationClick = { onNavigateToMap() }
                    )
                }
                item(key = "history_footer_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    // Delete Confirmation Dialog
    expenseToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("Delete Expense?") },
            text = { Text("Are you sure you want to remove '${FormatUtils.formatMmk(item.amount)} - ${item.cleanNote.ifBlank { item.category }}' from your shared budget?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteExpense(item.id)
                        expenseToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
