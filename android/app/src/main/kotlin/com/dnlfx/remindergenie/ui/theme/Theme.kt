package com.dnlfx.remindergenie.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.dnlfx.remindergenie.core.ItemColor
import com.dnlfx.remindergenie.core.ItemStatus

private val Purple = Color(0xFF5855D5)

private val LightColors = lightColorScheme(
    primary = Purple,
    secondary = Color(0xFF7A5BC9),
    tertiary = Color(0xFFB8860B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7B5FF),
    secondary = Color(0xFFCDB8FF),
    tertiary = Color(0xFFFFD65A),
)

@Composable
fun ReminderGenieTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    // Material You colors from the wallpaper on Android 12+, the icon's purple before that.
    val colors: ColorScheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Fixed colors that mean the same thing everywhere, like iOS's system colors. */
object StatusColors {
    val Red = Color(0xFFE5393A)
    val Orange = Color(0xFFF08C00)
    val Green = Color(0xFF2EAD55)
    val Gray = Color(0xFF8E8E93)
    val Indigo = Color(0xFF5856D6)
}

val ItemColor.color: Color
    get() = when (this) {
        ItemColor.BLUE -> Color(0xFF1E7BF2)
        ItemColor.TEAL -> Color(0xFF1FA6B8)
        ItemColor.GREEN -> Color(0xFF2EAD55)
        ItemColor.YELLOW -> Color(0xFFE0B000)
        ItemColor.ORANGE -> Color(0xFFF08C00)
        ItemColor.RED -> Color(0xFFE5393A)
        ItemColor.PINK -> Color(0xFFE8376B)
        ItemColor.PURPLE -> Color(0xFFA24FD6)
        ItemColor.INDIGO -> Color(0xFF5856D6)
        ItemColor.GRAY -> Color(0xFF8E8E93)
    }

val ItemStatus.tint: Color
    get() = when (this) {
        ItemStatus.OVERDUE -> StatusColors.Red
        ItemStatus.DUE_TODAY, ItemStatus.DUE_SOON -> StatusColors.Orange
        ItemStatus.OK -> StatusColors.Green
    }

/** The ring color: the item's own color while things are fine, a warning color as the due date nears. */
fun ringTint(color: ItemColor, status: ItemStatus, paused: Boolean): Color = when {
    paused -> StatusColors.Gray
    status == ItemStatus.OK -> color.color
    else -> status.tint
}
