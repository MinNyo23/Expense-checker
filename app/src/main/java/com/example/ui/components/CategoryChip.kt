package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExpenseCategory

@Composable
fun CategoryChip(
    category: ExpenseCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        category.color.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }

    val contentColor = if (isSelected) {
        category.color
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("category_chip_${category.name.lowercase()}"),
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(category.color.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = category.title,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = category.title,
                fontSize = 13.sp,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
