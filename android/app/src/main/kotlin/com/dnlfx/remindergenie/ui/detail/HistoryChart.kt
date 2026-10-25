package com.dnlfx.remindergenie.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dnlfx.remindergenie.core.DueText
import com.dnlfx.remindergenie.core.HistoryStats
import com.dnlfx.remindergenie.core.pluralized
import com.dnlfx.remindergenie.ui.theme.StatusColors

/** Days each cycle actually lasted (bars) against the schedule (dashed line), latest 12 cycles. */
@Composable
fun HistoryChart(gaps: List<HistoryStats.Gap>, tint: Color, modifier: Modifier = Modifier) {
    val recent = gaps.takeLast(12)
    val target = recent.lastOrNull()?.targetDays ?: 0
    val maxValue = maxOf(recent.maxOfOrNull { it.days } ?: 0, target).coerceAtLeast(1) * 1.15f
    val guide = MaterialTheme.colorScheme.onSurfaceVariant
    val description = recent.joinToString(prefix = "Cycle lengths, oldest first: ") {
        "${DueText.monthDay(it.replacedOn)}, ${pluralized(it.days, "day")}, ${if (it.isOnTime) "on time" else "late"}"
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            "Every ${pluralized(target, "day")}",
            style = MaterialTheme.typography.labelSmall,
            color = guide,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = description }
                .testTag("history-chart"),
        ) {
            val slot = size.width / recent.size
            val barWidth = slot * 0.6f
            recent.forEachIndexed { index, gap ->
                val height = size.height * (gap.days / maxValue)
                drawRoundRect(
                    color = if (gap.isOnTime) tint else StatusColors.Red,
                    topLeft = Offset(index * slot + (slot - barWidth) / 2, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            val y = size.height * (1 - target / maxValue)
            drawLine(
                color = guide,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend(tint, "On time")
            Spacer(Modifier.width(16.dp))
            Legend(StatusColors.Red, "Late")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
