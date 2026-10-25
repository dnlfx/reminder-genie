package com.dnlfx.remindergenie.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dnlfx.remindergenie.core.DueText
import com.dnlfx.remindergenie.core.pluralized
import com.dnlfx.remindergenie.ui.theme.StatusColors

/** A circular countdown ring, optionally with the item's icon in the middle. */
@Composable
fun ProgressRing(
    progress: Double,
    tint: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    size: Dp = 44.dp,
    lineWidth: Dp = 5.dp,
) {
    val animated by animateFloatAsState(progress.toFloat(), label = "progress")
    Box(modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = lineWidth.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(tint.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(tint, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.45f))
        }
    }
}

/** An icon in a tinted circle, used for templates and the editor preview. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 36.dp, filled: Boolean = false) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) tint else tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color.White else tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** A small rounded tag, used for spares, snooze and pause states. */
@Composable
fun StatusPill(text: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

/** A grouped block of rows, like a section of an iOS settings list. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    footerColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = if (footerColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else footerColor,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
            )
        }
    }
}

/** "Label ........ value" inside a [SectionCard]. */
@Composable
fun LabeledRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A label with − and + buttons, the Android stand-in for an iOS stepper. */
@Composable
fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { onValueChange((value - 1).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.semantics { contentDescription = "Decrease ${testTag ?: label}" },
        ) { Icon(Icons.Outlined.Remove, contentDescription = null) }
        FilledTonalIconButton(
            onClick = { onValueChange((value + 1).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.semantics { contentDescription = "Increase ${testTag ?: label}" },
        ) { Icon(Icons.Outlined.Add, contentDescription = null) }
    }
}

/** The headline at the top of the list: what needs attention right now. */
@Composable
fun SummaryCard(overdue: Int, dueSoon: Int, nextName: String?, nextDays: Int?, modifier: Modifier = Modifier) {
    val (icon, tint) = when {
        overdue > 0 -> Icons.Outlined.ErrorOutline to StatusColors.Red
        dueSoon > 0 -> Icons.Outlined.Schedule to StatusColors.Orange
        else -> Icons.Outlined.Check to StatusColors.Green
    }
    val headline = when {
        overdue > 0 -> "${pluralized(overdue, "item")} overdue"
        dueSoon > 0 -> "${pluralized(dueSoon, "item")} due soon"
        else -> "You're all caught up"
    }
    val detail = when {
        overdue > 0 && dueSoon > 0 -> "Plus $dueSoon due soon"
        overdue > 0 || nextName == null || nextDays == null -> null
        else -> "Next up: $nextName, ${DueText.short(nextDays).lowercase()}"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(tint),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(headline, style = MaterialTheme.typography.titleMedium)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Shown at the top of the list when notifications are off, since the app is useless without them. */
@Composable
fun NotificationsOffBanner(onTurnOn: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)) {
            Icon(Icons.Outlined.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Notifications are off", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    "You won't get reminders until you allow notifications for Reminder Genie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = onTurnOn) { Text("Turn on") }
            }
        }
    }
}

/** Shows a date as a tappable row that opens a picker. */
@Composable
fun PickerRow(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.CalendarMonth) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "$label: $value" }) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(value)
        }
    }
}
