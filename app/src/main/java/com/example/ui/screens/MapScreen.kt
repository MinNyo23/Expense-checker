package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.ui.components.LeafletMapWebView
import com.example.ui.theme.PartnerAColor
import com.example.ui.theme.PartnerBColor
import com.example.ui.viewmodel.CoupleExpenseViewModel
import java.util.Locale

@Composable
fun MapScreen(
    viewModel: CoupleExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val expenses by viewModel.expenses.collectAsState()
    val userSession by viewModel.userSession.collectAsState()

    var filterUser by remember { mutableStateOf<String?>("ALL") } // "ALL", "ME", "PARTNER"
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }

    val filteredExpenses = remember(expenses, filterUser, userSession.userName) {
        val withCoords = expenses.filter { it.latitude != null && it.longitude != null }
        when (filterUser) {
            "ME" -> withCoords.filter { it.user_name.equals(userSession.userName, ignoreCase = true) }
            "PARTNER" -> withCoords.filter { !it.user_name.equals(userSession.userName, ignoreCase = true) }
            else -> withCoords
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Leaflet.js interactive map in WebView
        LeafletMapWebView(
            expenses = filteredExpenses,
            currentUserName = userSession.userName,
            onMarkerClick = { exp ->
                selectedExpense = exp
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Floating Header with Partner Legend and Filter
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Shared Expense Map",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Count badge
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${filteredExpenses.size} pinned",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Legend & Filter Row
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color Legend
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(PartnerAColor)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "You", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(PartnerBColor)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Partner", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Filters
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = filterUser == "ALL",
                            onClick = { filterUser = "ALL" },
                            label = { Text("All", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = filterUser == "ME",
                            onClick = { filterUser = "ME" },
                            label = { Text("You", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = filterUser == "PARTNER",
                            onClick = { filterUser = "PARTNER" },
                            label = { Text("Partner", fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Bottom Selected Expense Card
        AnimatedVisibility(
            visible = selectedExpense != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            val exp = selectedExpense
            if (exp != null) {
                val category = ExpenseCategory.fromString(exp.category)
                val isMe = exp.user_name.equals(userSession.userName, ignoreCase = true)
                val color = if (isMe) PartnerAColor else PartnerBColor

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("map_selected_expense_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(category.color.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = null,
                                        tint = category.color,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = exp.note.ifBlank { category.title },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${category.title} • ${exp.date}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { selectedExpense = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = color.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isMe) "Paid by You" else "Paid by ${exp.user_name}",
                                    color = color,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Text(
                                text = "$${String.format(Locale.US, "%.2f", exp.amount)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
