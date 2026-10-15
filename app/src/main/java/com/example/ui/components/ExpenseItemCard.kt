package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ui.theme.PartnerAColor
import com.example.ui.theme.PartnerBColor
import java.util.Locale

@Composable
fun ExpenseItemCard(
    expense: Expense,
    currentUserName: String,
    onDeleteClick: (() -> Unit)? = null,
    onLocationClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val category = ExpenseCategory.fromString(expense.category)
    val isCurrentUser = expense.user_name.equals(currentUserName, ignoreCase = true)
    val partnerBadgeColor = if (isCurrentUser) PartnerAColor else PartnerBColor

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("expense_card_${expense.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(category.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = category.title,
                    tint = category.color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Main details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (expense.note.isNotBlank()) expense.note else category.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Author Badge
                    Surface(
                        color = partnerBadgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isCurrentUser) "You" else expense.user_name,
                            color = partnerBadgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Category name
                    Text(
                        text = "• ${category.title}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Date
                    Text(
                        text = "• ${expense.date}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                // GPS location indicator if available
                if (expense.latitude != null && expense.longitude != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickableNoRipple(onLocationClick)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location tagged",
                            tint = partnerBadgeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format(Locale.US, "GPS: %.4f, %.4f", expense.latitude, expense.longitude),
                            fontSize = 11.sp,
                            color = partnerBadgeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Price & Actions
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "$${String.format(Locale.US, "%.2f", expense.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                if (onDeleteClick != null) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_expense_${expense.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete expense",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: (() -> Unit)?): Modifier {
    return if (onClick != null) {
        this.clickable { onClick() }
    } else {
        this
    }
}
