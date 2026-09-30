package dev.peterdsp.odivrelo.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.peterdsp.odivrelo.theme.OdivreloPalette.Dark
import dev.peterdsp.odivrelo.theme.OdivreloPalette.Light

/**
 * The Odivrelo palette. Every value comes from `OdivreloPalette`, which
 * scripts/generate-design-tokens.sh writes from design/tokens/odivrelo.tokens.json,
 * the same file the web and iOS themes are generated from. This file only maps
 * those semantic roles onto Material 3's slots.
 *
 * Material 3's own dynamic colour is deliberately not used. A product whose
 * whole argument is that it shows exactly what its data says should not have
 * its status colours redefined by whatever wallpaper the device happens to
 * have: a "stale" chip that borrows a wallpaper's hue stops meaning stale.
 */
/**
 * The status colours Material 3's own scheme has no slot for. They carry
 * meaning (a warning, a source that may not be republished, a confirmed
 * result), so they are part of the theme rather than literals in a screen.
 */
@Immutable
data class OdivreloStatusColors(
    val warningSurface: Color,
    val warningText: Color,
    val successSurface: Color,
    val successText: Color,
    val infoSurface: Color,
    val infoText: Color,
    val focus: Color,
    val surfaceMuted: Color,
    val textSecondary: Color,
)

private val LightStatus = OdivreloStatusColors(
    warningSurface = Light.warningSurface,
    warningText = Light.warningText,
    successSurface = Light.successSurface,
    successText = Light.successText,
    infoSurface = Light.infoSurface,
    infoText = Light.infoText,
    focus = Light.focus,
    surfaceMuted = Light.surfaceMuted,
    textSecondary = Light.textSecondary,
)

private val DarkStatus = OdivreloStatusColors(
    warningSurface = Dark.warningSurface,
    warningText = Dark.warningText,
    successSurface = Dark.successSurface,
    successText = Dark.successText,
    infoSurface = Dark.infoSurface,
    infoText = Dark.infoText,
    focus = Dark.focus,
    surfaceMuted = Dark.surfaceMuted,
    textSecondary = Dark.textSecondary,
)

private val LightScheme = lightColorScheme(
    primary = Light.primary,
    onPrimary = Light.onPrimary,
    primaryContainer = Light.primarySoft,
    onPrimaryContainer = Light.primaryInk,
    secondary = Light.primaryPressed,
    onSecondary = Light.onPrimary,
    secondaryContainer = Light.surfaceMuted,
    onSecondaryContainer = Light.textPrimary,
    tertiary = Light.accentInk,
    onTertiary = Light.surface,
    tertiaryContainer = Light.warningSurface,
    onTertiaryContainer = Light.warningText,
    background = Light.background,
    onBackground = Light.textPrimary,
    surface = Light.surface,
    onSurface = Light.textPrimary,
    surfaceVariant = Light.surfaceMuted,
    onSurfaceVariant = Light.textSecondary,
    surfaceContainer = Light.surfaceMuted,
    surfaceContainerHigh = Light.surfaceContainerHigh,
    surfaceContainerHighest = Light.surfaceContainerHighest,
    surfaceContainerLow = Light.surface,
    surfaceContainerLowest = Light.surface,
    outline = Light.outline,
    outlineVariant = Light.border,
    error = Light.errorText,
    onError = Light.surface,
    errorContainer = Light.errorSurface,
    onErrorContainer = Light.errorText,
    inverseSurface = Light.textPrimary,
    inverseOnSurface = Light.surfaceMuted,
    inversePrimary = Dark.primary,
    scrim = Color(0x80000000),
)

private val DarkScheme = darkColorScheme(
    primary = Dark.primary,
    onPrimary = Dark.onPrimary,
    primaryContainer = Dark.primarySoft,
    onPrimaryContainer = Dark.primaryInk,
    secondary = Dark.primaryPressed,
    onSecondary = Dark.onPrimary,
    secondaryContainer = Dark.surfaceMuted,
    onSecondaryContainer = Dark.textPrimary,
    tertiary = Dark.accent,
    onTertiary = Dark.accentInk,
    tertiaryContainer = Dark.warningSurface,
    onTertiaryContainer = Dark.warningText,
    background = Dark.background,
    onBackground = Dark.textPrimary,
    surface = Dark.surface,
    onSurface = Dark.textPrimary,
    surfaceVariant = Dark.surfaceMuted,
    onSurfaceVariant = Dark.textSecondary,
    surfaceContainer = Dark.surfaceMuted,
    surfaceContainerHigh = Dark.surfaceContainerHigh,
    surfaceContainerHighest = Dark.surfaceContainerHighest,
    surfaceContainerLow = Dark.surface,
    surfaceContainerLowest = Dark.background,
    outline = Dark.outline,
    outlineVariant = Dark.border,
    error = Dark.errorText,
    onError = Dark.errorSurface,
    errorContainer = Dark.errorSurface,
    onErrorContainer = Dark.errorText,
    inverseSurface = Dark.textPrimary,
    inverseOnSurface = Light.textPrimary,
    inversePrimary = Light.primary,
    scrim = Color(0x99000000),
)

/**
 * Type scale from the token file. Sizes are in `sp` so the system font-size and
 * display-size settings scale them; nothing here is pinned in `dp`.
 */
private val OdivreloTypography: Typography = run {
    val sans = FontFamily.SansSerif
    Typography(
        displaySmall = TextStyle(
            fontFamily = sans,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
        ),
        headlineMedium = TextStyle(
            fontFamily = sans,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
        ),
        headlineSmall = TextStyle(
            fontFamily = sans,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleLarge = TextStyle(
            fontFamily = sans,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = TextStyle(
            fontFamily = sans,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleSmall = TextStyle(
            fontFamily = sans,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        bodyLarge = TextStyle(
            fontFamily = sans,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
        ),
        bodyMedium = TextStyle(
            fontFamily = sans,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
        ),
        bodySmall = TextStyle(
            fontFamily = sans,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
        ),
        labelLarge = TextStyle(
            fontFamily = sans,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        labelMedium = TextStyle(
            fontFamily = sans,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        labelSmall = TextStyle(
            fontFamily = sans,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

private val OdivreloShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Spacing scale from the token file, so no screen invents a number. */
object Space {
    val x1: Dp = 4.dp
    val x2: Dp = 8.dp
    val x3: Dp = 12.dp
    val x4: Dp = 16.dp
    val x5: Dp = 20.dp
    val x6: Dp = 24.dp
    val x8: Dp = 32.dp
    val x10: Dp = 40.dp
    val x12: Dp = 48.dp

    /**
     * The smallest interactive size anywhere in the product. The token file says
     * 44dp because the web has a different floor; Android's accessibility
     * requirement is 48dp and the larger of the two wins.
     */
    val touchTarget: Dp = 48.dp

    /** A comfortable measure for a column of running text on a wide window. */
    val readingWidth: Dp = 560.dp
}

val LocalOdivreloStatusColors = staticCompositionLocalOf { LightStatus }

/**
 * Set by the settings screen. Screens read it instead of asking the system,
 * because the person can ask for reduced motion here even when the platform
 * setting is off.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Adds extra padding around interactive rows when the person asked for it. */
val LocalExtraTouchPadding = staticCompositionLocalOf { 0.dp }

object OdivreloTheme {
    val status: OdivreloStatusColors
        @Composable @ReadOnlyComposable get() = LocalOdivreloStatusColors.current
}

@Composable
fun OdivreloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    largerTouchTargets: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalOdivreloStatusColors provides if (darkTheme) DarkStatus else LightStatus,
        LocalReduceMotion provides reduceMotion,
        LocalExtraTouchPadding provides if (largerTouchTargets) Space.x2 else 0.dp,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = OdivreloTypography,
            shapes = OdivreloShapes,
            content = content,
        )
    }
}
