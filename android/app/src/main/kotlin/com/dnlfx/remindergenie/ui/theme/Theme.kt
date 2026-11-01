package com.dnlfx.remindergenie.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dnlfx.remindergenie.core.ItemColor
import com.dnlfx.remindergenie.core.ItemStatus
import com.dnlfx.remindergenie.data.ThemeMode

// The brand palette, built around the icon's indigo with its gold sparkle as the accent.

private val LightColors = lightColorScheme(
    primary = Color(0xFF5451C8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2DFFF),
    onPrimaryContainer = Color(0xFF3A37AD),
    inversePrimary = Color(0xFFC3C0FF),
    secondary = Color(0xFF5D5C72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E0F9),
    onSecondaryContainer = Color(0xFF454559),
    tertiary = Color(0xFF775A0B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDF9E),
    onTertiaryContainer = Color(0xFF5B4300),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceTint = Color(0xFF5451C8),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF3EFF7),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC8C5D0),
    surfaceBright = Color(0xFFFCF8FF),
    surfaceDim = Color(0xFFDCD8E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2FC),
    surfaceContainer = Color(0xFFF0ECF6),
    surfaceContainerHigh = Color(0xFFEAE7F0),
    surfaceContainerHighest = Color(0xFFE4E1EB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC3C0FF),
    onPrimary = Color(0xFF24218F),
    primaryContainer = Color(0xFF3B38AE),
    onPrimaryContainer = Color(0xFFE2DFFF),
    inversePrimary = Color(0xFF5451C8),
    secondary = Color(0xFFC6C4DD),
    onSecondary = Color(0xFF2F2F42),
    secondaryContainer = Color(0xFF454559),
    onSecondaryContainer = Color(0xFFE2E0F9),
    tertiary = Color(0xFFE8C26C),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFDF9E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    surfaceTint = Color(0xFFC3C0FF),
    inverseSurface = Color(0xFFE4E1E9),
    inverseOnSurface = Color(0xFF303036),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF47464F),
    surfaceBright = Color(0xFF39383F),
    surfaceDim = Color(0xFF131318),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF35343A),
)

/** Slightly heavier titles than the Material defaults, for a crisper hierarchy. */
private val AppTypography = Typography().let { base ->
    base.copy(
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Colors that mean the same thing everywhere: overdue, due soon, on track. Each has a strong tone
 * for text, icons and rings (at least 4.5:1 on the app's surfaces) and a soft container tone.
 */
@Immutable
data class StatusPalette(
    val red: Color,
    val orange: Color,
    val green: Color,
    val indigo: Color,
    val gray: Color,
    val redContainer: Color,
    val orangeContainer: Color,
    val greenContainer: Color,
)

private val LightStatus = StatusPalette(
    red = Color(0xFFC4282D),
    orange = Color(0xFFA84F00),
    green = Color(0xFF1F7A3D),
    indigo = Color(0xFF5452C8),
    gray = Color(0xFF66656F),
    redContainer = Color(0xFFFFE2DF),
    orangeContainer = Color(0xFFFFE6CC),
    greenContainer = Color(0xFFD5F5DD),
)

private val DarkStatus = StatusPalette(
    red = Color(0xFFFF8A84),
    orange = Color(0xFFFFB35C),
    green = Color(0xFF6FD893),
    indigo = Color(0xFFB9B7FF),
    gray = Color(0xFFA3A2AD),
    redContainer = Color(0xFF4A1A1A),
    orangeContainer = Color(0xFF45290A),
    greenContainer = Color(0xFF123822),
)

@Immutable
private data class AppPalette(val isDark: Boolean, val status: StatusPalette)

private val LocalAppPalette = staticCompositionLocalOf { AppPalette(isDark = false, status = LightStatus) }

@Composable
fun ReminderGenieTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    // The brand palette by default; the wallpaper's Material You colors on Android 12+ if chosen.
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalAppPalette provides AppPalette(dark, if (dark) DarkStatus else LightStatus)) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** The status colors for the current light or dark theme. */
object StatusColors {
    val Red: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.status.red
    val Orange: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.status.orange
    val Green: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.status.green
    val Gray: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.status.gray
    val Indigo: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.status.indigo
    val RedContainer: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.status.redContainer
    val OrangeContainer: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.status.orangeContainer
    val GreenContainer: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.status.greenContainer
}

/** An item's tag color, deeper in light mode and softer in dark mode so it reads on either. */
val ItemColor.color: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalAppPalette.current.isDark) darkTone else lightTone

private val ItemColor.lightTone: Color
    get() = when (this) {
        ItemColor.BLUE -> Color(0xFF1F6FE0)
        ItemColor.TEAL -> Color(0xFF0E8A9A)
        ItemColor.GREEN -> Color(0xFF2A9A4E)
        ItemColor.YELLOW -> Color(0xFFB88A00)
        ItemColor.ORANGE -> Color(0xFFD86F00)
        ItemColor.RED -> Color(0xFFD7303A)
        ItemColor.PINK -> Color(0xFFD02A68)
        ItemColor.PURPLE -> Color(0xFF9442C8)
        ItemColor.INDIGO -> Color(0xFF5452C8)
        ItemColor.GRAY -> Color(0xFF7A7983)
    }

private val ItemColor.darkTone: Color
    get() = when (this) {
        ItemColor.BLUE -> Color(0xFF79AEFF)
        ItemColor.TEAL -> Color(0xFF5CD0DE)
        ItemColor.GREEN -> Color(0xFF6FD893)
        ItemColor.YELLOW -> Color(0xFFF2CC4E)
        ItemColor.ORANGE -> Color(0xFFFFAE57)
        ItemColor.RED -> Color(0xFFFF8A84)
        ItemColor.PINK -> Color(0xFFFF8AB4)
        ItemColor.PURPLE -> Color(0xFFD49BFF)
        ItemColor.INDIGO -> Color(0xFFB9B7FF)
        ItemColor.GRAY -> Color(0xFFB0AFBA)
    }

/** Readable text and icon color on top of [background]: white on deep colors, near-black on light ones. */
fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.18f) Color(0xFF16161B) else Color.White

val ItemStatus.tint: Color
    @Composable @ReadOnlyComposable
    get() = when (this) {
        ItemStatus.OVERDUE -> StatusColors.Red
        ItemStatus.DUE_TODAY, ItemStatus.DUE_SOON -> StatusColors.Orange
        ItemStatus.OK -> StatusColors.Green
    }

/** The ring color: the item's own color while things are fine, a warning color as the due date nears. */
@Composable
@ReadOnlyComposable
fun ringTint(color: ItemColor, status: ItemStatus, paused: Boolean): Color = when {
    paused -> StatusColors.Gray
    status == ItemStatus.OK -> color.color
    else -> status.tint
}
