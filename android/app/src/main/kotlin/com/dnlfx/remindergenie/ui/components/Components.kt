package com.dnlfx.remindergenie.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dnlfx.remindergenie.R
import com.dnlfx.remindergenie.core.DueText
import com.dnlfx.remindergenie.core.pluralized
import com.dnlfx.remindergenie.ui.theme.StatusColors
import com.dnlfx.remindergenie.ui.theme.contentColorOn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Keeps content to a comfortable reading width on tablets and in landscape, centered. */
fun Modifier.readableWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth()

/**
 * The shape of one row in a group of rows that read as a single block: large outer corners on the
 * first and last rows, small corners where rows meet.
 */
fun segmentShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 6.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

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
            drawArc(tint.copy(alpha = 0.16f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(tint, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.44f))
        }
    }
}

/** An icon in a tinted circle, used for templates and the editor preview. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, filled: Boolean = false) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) tint else tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (filled) contentColorOn(tint) else tint,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/** The app icon, drawn from the launcher icon's own layers. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(Color(0xFF5855D5), Color(0xFF9546C8)))),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/** A small rounded tag, used for spares, snooze and pause states. */
@Composable
fun StatusPill(text: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.13f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

/** A titled, rounded block of rows, like a section of a settings screen. */
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
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).semantics { heading() },
            )
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 6.dp), content = content)
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = if (footerColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else footerColor,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
            )
        }
    }
}

/** "Label ........ value" inside a [SectionCard]. */
@Composable
fun LabeledRow(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null, valueColor: Color = Color.Unspecified) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else valueColor,
        )
    }
}

/**
 * A tappable row with an icon, a title and an optional line under it, for settings and actions.
 * [trailing] sits at the end, such as a status or a switch.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint.copy(alpha = iconTint.alpha * alpha), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val color = if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else titleColor
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color.copy(alpha = color.alpha * alpha))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A row with a switch; the whole row toggles it. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A label with − and + buttons. Hold a button to keep counting, faster the longer it's held, so
 * reaching 90 days doesn't take 90 taps.
 */
@Composable
fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    // The buttons read the latest value while held, so each repeat steps from where the last one left off.
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onValueChange)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        RepeatingIconButton(
            icon = Icons.Outlined.Remove,
            description = "Decrease ${testTag ?: label}",
            enabled = value > range.first,
            onStep = { latestChange((latestValue - 1).coerceIn(range)) },
        )
        Spacer(Modifier.width(8.dp))
        RepeatingIconButton(
            icon = Icons.Outlined.Add,
            description = "Increase ${testTag ?: label}",
            enabled = value < range.last,
            onStep = { latestChange((latestValue + 1).coerceIn(range)) },
        )
    }
}

@Composable
private fun RepeatingIconButton(icon: ImageVector, description: String, enabled: Boolean, onStep: () -> Unit) {
    val latestStep by rememberUpdatedState(onStep)
    val latestEnabled by rememberUpdatedState(enabled)
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val container by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            pressed -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        },
        label = "stepper",
    )
    val content = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .background(container)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                if (!enabled) disabled()
                onClick {
                    if (latestEnabled) latestStep()
                    latestEnabled
                }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown()
                        if (!latestEnabled) return@awaitEachGesture
                        pressed = true
                        latestStep()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val repeating = launch {
                            delay(450)
                            var interval = 140L
                            while (isActive && latestEnabled) {
                                latestStep()
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                delay(interval)
                                interval = (interval * 0.85).toLong().coerceAtLeast(35L)
                            }
                        }
                        waitForUpOrCancellation()
                        repeating.cancel()
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
}

/** The headline at the top of the list: what needs attention right now, with a count per group. */
@Composable
fun SummaryCard(
    overdue: Int,
    dueSoon: Int,
    upcoming: Int,
    nextName: String?,
    nextDays: Int?,
    modifier: Modifier = Modifier,
    onShowOverdue: () -> Unit = {},
    onShowDueSoon: () -> Unit = {},
    onShowUpcoming: () -> Unit = {},
) {
    val (icon, tint, container) = when {
        overdue > 0 -> Triple(Icons.Outlined.ErrorOutline, StatusColors.Red, StatusColors.RedContainer)
        dueSoon > 0 -> Triple(Icons.Outlined.Schedule, StatusColors.Orange, StatusColors.OrangeContainer)
        else -> Triple(Icons.Outlined.TaskAlt, StatusColors.Green, StatusColors.GreenContainer)
    }
    val headline = when {
        overdue > 0 -> "${pluralized(overdue, "item")} overdue"
        dueSoon > 0 -> "${pluralized(dueSoon, "item")} due soon"
        else -> "You're all caught up"
    }
    val detail = when {
        overdue > 0 && dueSoon > 0 -> "Plus $dueSoon due soon. Swipe right on an item once it's replaced."
        overdue > 0 -> "Swipe right on an item once it's replaced."
        nextName == null || nextDays == null -> null
        else -> "Next up: $nextName, ${DueText.short(nextDays).lowercase()}"
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(tint),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(headline, style = MaterialTheme.typography.titleMedium)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            Counter(overdue, "Overdue", if (overdue > 0) StatusColors.Red else null, onShowOverdue, Modifier.weight(1f))
            Counter(dueSoon, "Due soon", if (dueSoon > 0) StatusColors.Orange else null, onShowDueSoon, Modifier.weight(1f))
            Counter(upcoming, "Upcoming", null, onShowUpcoming, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Counter(count: Int, label: String, tint: Color?, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = count > 0, onClickLabel = "Show $label items", onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$count",
            style = MaterialTheme.typography.titleLarge,
            color = tint ?: MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shown at the top of the list when notifications are off, since the app is useless without them. */
@Composable
fun NotificationsOffBanner(onTurnOn: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Notifications are off", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.height(2.dp))
                Text(
                    "You won't get reminders until you allow notifications for Reminder Genie.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onTurnOn) { Text("Turn on notifications") }
            }
        }
    }
}

/** A label with its current value, tapping it opens a picker. */
@Composable
fun PickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.CalendarMonth,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
