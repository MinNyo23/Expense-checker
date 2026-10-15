package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.CategorySummary
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendingBreakdownChart(
    categories: List<CategorySummary>,
    totalAmount: Double,
    modifier: Modifier = Modifier
) {
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(categories) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spending Breakdown",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "$${String.format(Locale.US, "%.2f", totalAmount)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (categories.isEmpty() || totalAmount <= 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No expenses logged yet this period 💕",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Donut Chart
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(140.dp)) {
                        val strokeWidth = 24.dp.toPx()
                        var startAngle = -90f

                        for (item in categories) {
                            val sweepAngle = (item.percentage / 100f) * 360f * animationProgress.value
                            if (sweepAngle > 0f) {
                                drawArc(
                                    color = item.category.color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle - 2f, // subtle gap
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                startAngle += sweepAngle
                            }
                        }
                    }

                    // Center label
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$${String.format(Locale.US, "%.0f", totalAmount)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Legend chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.take(6).forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(item.category.color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${item.category.title} (${String.format(Locale.US, "%.0f", item.percentage)}%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
