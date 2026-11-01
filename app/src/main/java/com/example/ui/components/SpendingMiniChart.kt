package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Transaction
import com.example.utils.FormatUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailySpend(
    val dayLabel: String,
    val dateString: String,
    val amount: Double,
    val isToday: Boolean
)

@Composable
fun SpendingMiniChart(
    transactions: List<Transaction>,
    privacyMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

    val dailyData = remember(transactions) {
        val list = mutableListOf<DailySpend>()
        val cal = Calendar.getInstance()

        // Generate past 7 days (day -6 to today)
        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMillis = dayCal.timeInMillis

            val endCal = Calendar.getInstance().apply {
                timeInMillis = startMillis
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val endMillis = endCal.timeInMillis

            val spend = transactions
                .filter { !it.isIncome && it.category != "Transfer" && it.category != "Adjustment" && it.category != "Udhaar" && it.category != "Investment" && it.timestamp in startMillis..endMillis }
                .sumOf { it.amount }

            val label = if (i == 0) "Today" else dayFormat.format(Date(startMillis))
            val dateStr = dateFormat.format(Date(startMillis))
            list.add(DailySpend(label, dateStr, spend, isToday = (i == 0)))
        }
        list
    }

    val total7Days = dailyData.sumOf { it.amount }
    val avgPerDay = total7Days / 7.0
    val maxSpend = maxOf(1.0, dailyData.maxOfOrNull { it.amount } ?: 1.0)

    var selectedDay by remember { mutableStateOf<DailySpend?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spending_mini_chart_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Last 7 Days Spending",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (privacyMode) "Total: ₹****" else "Total: ${FormatUtils.formatInr(total7Days)} • Avg: ${FormatUtils.formatInr(avgPerDay)}/day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (selectedDay != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            if (privacyMode) "${selectedDay!!.dayLabel}: ₹****"
                            else "${selectedDay!!.dayLabel} (${selectedDay!!.dateString}): ${FormatUtils.formatInr(selectedDay!!.amount)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Bars Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                dailyData.forEach { item ->
                    val fraction = (item.amount / maxSpend).toFloat().coerceIn(0.05f, 1f)
                    val barHeight = (fraction * 75).dp
                    val isSelected = selectedDay == item

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedDay = if (selectedDay == item) null else item
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Amount above bar if non-zero
                        if (item.amount > 0) {
                            Text(
                                if (privacyMode) "₹*" else "₹${if (item.amount >= 1000) "${(item.amount / 1000).toInt()}k" else item.amount.toInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (item.isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (item.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        } else {
                            Spacer(Modifier.height(16.dp))
                        }

                        // The bar pill
                        Box(
                            modifier = Modifier
                                .width(if (item.isToday) 20.dp else 16.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.tertiary
                                        item.isToday -> MaterialTheme.colorScheme.primary
                                        item.amount > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                        )

                        Spacer(Modifier.height(8.dp))

                        // Day label below bar
                        Text(
                            item.dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (item.isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (item.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
