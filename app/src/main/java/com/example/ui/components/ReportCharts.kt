package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CashBankFlow
import com.example.utils.FormatUtils
import com.example.utils.ReportUtils

val reportChartColors = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFFD81B60), Color(0xFF3949AB),
    Color(0xFF7CB342), Color(0xFFFDD835), Color(0xFF6D4C41), Color(0xFF546E7A)
)

@Composable
fun CategoryDonutChart(
    categoryTotals: List<Pair<String, Double>>,
    totalExpense: Double,
    privacyMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedCategoryIndex by remember { mutableStateOf<Int?>(null) }

    fun formatMoney(amount: Double): String =
        if (privacyMode) "₹****" else FormatUtils.formatInr(amount)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                "Category Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))

            if (totalExpense <= 0.0 || categoryTotals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No expenses in this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Donut Canvas
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 32f
                            val canvasSize = size.minDimension - strokeWidth
                            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                            val arcSize = Size(canvasSize, canvasSize)

                            var currentAngle = -90f
                            categoryTotals.forEachIndexed { index, pair ->
                                val sweepAngle = (pair.second.toFloat() / totalExpense.toFloat()) * 360f
                                val color = reportChartColors[index % reportChartColors.size]
                                val isSelected = selectedCategoryIndex == index

                                drawArc(
                                    color = if (selectedCategoryIndex != null && !isSelected) color.copy(alpha = 0.35f) else color,
                                    startAngle = currentAngle,
                                    sweepAngle = sweepAngle - 1.5f,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(
                                        width = if (isSelected) strokeWidth + 6f else strokeWidth,
                                        cap = StrokeCap.Round
                                    )
                                )
                                currentAngle += sweepAngle
                            }
                        }

                        // Center content
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (selectedCategoryIndex != null) {
                                val selected = categoryTotals.getOrNull(selectedCategoryIndex!!)
                                if (selected != null) {
                                    Text(
                                        selected.first,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Text(
                                        formatMoney(selected.second),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                Text(
                                    "Total Spend",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatMoney(totalExpense),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    // Legend Column (Top 4)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryTotals.take(5).forEachIndexed { index, pair ->
                            val color = reportChartColors[index % reportChartColors.size]
                            val percent = (pair.second / totalExpense) * 100
                            val isSelected = selectedCategoryIndex == index

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedCategoryIndex = if (selectedCategoryIndex == index) null else index
                                    }
                                    .padding(vertical = 2.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Text(
                                        pair.first,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    String.format(java.util.Locale.US, "%.0f%%", percent),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IncomeExpenseMonthlyBarChart(
    monthlyData: List<ReportUtils.MonthBarData>,
    privacyMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val maxVal = maxOf(1.0, monthlyData.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0)

    fun formatMoney(amount: Double): String =
        if (privacyMode) "₹****" else FormatUtils.formatInr(amount)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Income vs Expense (6 Months)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
                        Text("Income", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFC62828)))
                        Text("Expense", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                monthlyData.forEach { month ->
                    val incFrac = (month.income / maxVal).toFloat().coerceIn(0.04f, 1f)
                    val expFrac = (month.expense / maxVal).toFloat().coerceIn(0.04f, 1f)

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Income bar
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height((incFrac * 90).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (month.income > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant)
                            )
                            // Expense bar
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height((expFrac * 90).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (month.expense > 0) Color(0xFFC62828) else MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            month.monthLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyTrendLineChart(
    trendPoints: List<ReportUtils.TrendPoint>,
    privacyMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val maxVal = maxOf(1.0, trendPoints.maxOfOrNull { it.amount } ?: 1.0)
    val strokeColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                "Daily Expense Trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Spending pattern over the selected range",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            if (trendPoints.isEmpty() || maxVal <= 1.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No daily expense data in range", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val paddingBottom = 20f

                        val stepX = if (trendPoints.size > 1) w / (trendPoints.size - 1) else w
                        val path = Path()

                        trendPoints.forEachIndexed { index, point ->
                            val x = index * stepX
                            val y = h - paddingBottom - ((point.amount / maxVal).toFloat() * (h - paddingBottom - 10f))
                            if (index == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                            // Data point dot
                            drawCircle(
                                color = strokeColor,
                                radius = 4.5f,
                                center = Offset(x, y)
                            )
                        }

                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                        )
                    }
                }

                // First and Last Date Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        trendPoints.firstOrNull()?.label ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        trendPoints.lastOrNull()?.label ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CashBankFlowCard(
    flow: CashBankFlow,
    privacyMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    fun formatMoney(amount: Double): String =
        if (privacyMode) "₹****" else FormatUtils.formatInr(amount)

    val netCash = flow.cashInflow - flow.cashOutflow
    val netBank = flow.bankInflow - flow.bankOutflow

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                "Cash vs Bank Flow",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cash Flow Column
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFE8F5E9).copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                            Text("Cash Flow", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color(0xFF2E7D32))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("In: ${formatMoney(flow.cashInflow)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1B5E20))
                        Text("Out: ${formatMoney(flow.cashOutflow)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Net: ${formatMoney(netCash)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (netCash >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }

                // Bank Flow Column
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFE3F2FD).copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(18.dp))
                            Text("Bank Flow", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color(0xFF1565C0))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("In: ${formatMoney(flow.bankInflow)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF0D47A1))
                        Text("Out: ${formatMoney(flow.bankOutflow)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Net: ${formatMoney(netBank)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (netBank >= 0) Color(0xFF1565C0) else Color(0xFFC62828)
                        )
                    }
                }
            }
        }
    }
}
