package dev.peterdsp.poravia.theme

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

/**
 * The Poravia palette, ported one value at a time from
 * design/tokens/poravia.tokens.json.
 *
 * Material 3's own dynamic colour is deliberately not used. A product whose
 * whole argument is that it shows exactly what its data says should not have
 * its status colours redefined by whatever wallpaper the device happens to
 * have: a "stale" chip that borrows a wallpaper's hue stops meaning stale.
 */
private object Tokens {
    val aegean950 = Color(0xFF0D1B1A)
    val aegean900 = Color(0xFF142E2C)
    val aegean700 = Color(0xFF064B47)
    val aegean600 = Color(0xFF0B6B63)
    val aegean300 = Color(0xFF52C8BC)
    val aegean100 = Color(0xFFDCEBE7)
    val aegean050 = Color(0xFFEFF7F5)
    val sun500 = Color(0xFFF2B84B)
    val sun100 = Color(0xFFFFF1C9)
    val sunInk = Color(0xFF4A2E00)
    val coral700 = Color(0xFF8F2F25)
    val coral100 = Color(0xFFFCE8E5)
    val green700 = Color(0xFF185C38)
    val green100 = Color(0xFFE3F3E9)
    val blue700 = Color(0xFF174F83)
    val blue100 = Color(0xFFE5F0FA)
    val stone700 = Color(0xFF4E615D)
    val stone200 = Color(0xFFCBD8D4)
    val canvas = Color(0xFFF6F7F2)
    val white = Color(0xFFFFFFFF)
}

/**
 * The status colours Material 3's own scheme has no slot for. They carry
 * meaning (a warning, a source that may not be republished, a confirmed
 * result), so they are part of the theme rather than literals in a screen.
 */
@Immutable
data class PoraviaStatusColors(
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

private val LightStatus = PoraviaStatusColors(
    warningSurface = Tokens.sun100,
    warningText = Tokens.sunInk,
    successSurface = Tokens.green100,
    successText = Tokens.green700,
    infoSurface = Tokens.blue100,
    infoText = Tokens.blue700,
    focus = Tokens.sun500,
    surfaceMuted = Tokens.aegean050,
    textSecondary = Tokens.stone700,
)

private val DarkStatus = PoraviaStatusColors(
    warningSurface = Color(0xFF493817),
    warningText = Color(0xFFFFE29A),
    successSurface = Color(0xFF173C29),
    successText = Color(0xFF9DDFB5),
    infoSurface = Color(0xFF19354F),
    infoText = Color(0xFFA7D2FF),
    focus = Color(0xFFF2B84B),
    surfaceMuted = Color(0xFF1C302D),
    textSecondary = Color(0xFFB8C9C5),
)

private val LightScheme = lightColorScheme(
    primary = Tokens.aegean600,
    onPrimary = Tokens.white,
    primaryContainer = Tokens.aegean100,
    onPrimaryContainer = Tokens.aegean700,
    secondary = Tokens.aegean700,
    onSecondary = Tokens.white,
    secondaryContainer = Tokens.aegean050,
    onSecondaryContainer = Tokens.aegean900,
    tertiary = Tokens.sunInk,
    onTertiary = Tokens.white,
    tertiaryContainer = Tokens.sun100,
    onTertiaryContainer = Tokens.sunInk,
    background = Tokens.canvas,
    onBackground = Tokens.aegean900,
    surface = Tokens.white,
    onSurface = Tokens.aegean900,
    surfaceVariant = Tokens.aegean050,
    onSurfaceVariant = Tokens.stone700,
    surfaceContainer = Tokens.aegean050,
    surfaceContainerHigh = Tokens.aegean100,
    surfaceContainerHighest = Tokens.aegean100,
    surfaceContainerLow = Tokens.white,
    surfaceContainerLowest = Tokens.white,
    outline = Tokens.stone700,
    outlineVariant = Tokens.stone200,
    error = Tokens.coral700,
    onError = Tokens.white,
    errorContainer = Tokens.coral100,
    onErrorContainer = Tokens.coral700,
    inverseSurface = Tokens.aegean900,
    inverseOnSurface = Tokens.aegean050,
    inversePrimary = Tokens.aegean300,
    scrim = Color(0x80000000),
)

private val DarkScheme = darkColorScheme(
    primary = Tokens.aegean300,
    onPrimary = Color(0xFF08201E),
    primaryContainer = Color(0xFF1C403C),
    onPrimaryContainer = Color(0xFFDCEBE7),
    secondary = Color(0xFF76D9CF),
    onSecondary = Color(0xFF08201E),
    secondaryContainer = Color(0xFF1C302D),
    onSecondaryContainer = Color(0xFFF4F8F6),
    tertiary = Tokens.sun500,
    onTertiary = Tokens.sunInk,
    tertiaryContainer = Color(0xFF493817),
    onTertiaryContainer = Color(0xFFFFE29A),
    background = Tokens.aegean950,
    onBackground = Color(0xFFF4F8F6),
    surface = Color(0xFF142522),
    onSurface = Color(0xFFF4F8F6),
    surfaceVariant = Color(0xFF1C302D),
    onSurfaceVariant = Color(0xFFB8C9C5),
    surfaceContainer = Color(0xFF1C302D),
    surfaceContainerHigh = Color(0xFF24403B),
    surfaceContainerHighest = Color(0xFF2B4B45),
    surfaceContainerLow = Color(0xFF142522),
    surfaceContainerLowest = Tokens.aegean950,
    outline = Color(0xFF8FA5A0),
    outlineVariant = Color(0xFF34504A),
    error = Color(0xFFFFB4AA),
    onError = Color(0xFF47231F),
    errorContainer = Color(0xFF47231F),
    onErrorContainer = Color(0xFFFFB4AA),
    inverseSurface = Color(0xFFF4F8F6),
    inverseOnSurface = Tokens.aegean900,
    inversePrimary = Tokens.aegean600,
    scrim = Color(0x99000000),
)

/**
 * Type scale from the token file. Sizes are in `sp` so the system font-size and
 * display-size settings scale them; nothing here is pinned in `dp`.
 */
private val PoraviaTypography: Typography = run {
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

private val PoraviaShapes = Shapes(
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

val LocalPoraviaStatusColors = staticCompositionLocalOf { LightStatus }

/**
 * Set by the settings screen. Screens read it instead of asking the system,
 * because the person can ask for reduced motion here even when the platform
 * setting is off.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Adds extra padding around interactive rows when the person asked for it. */
val LocalExtraTouchPadding = staticCompositionLocalOf { 0.dp }

object PoraviaTheme {
    val status: PoraviaStatusColors
        @Composable @ReadOnlyComposable get() = LocalPoraviaStatusColors.current
}

@Composable
fun PoraviaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    largerTouchTargets: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPoraviaStatusColors provides if (darkTheme) DarkStatus else LightStatus,
        LocalReduceMotion provides reduceMotion,
        LocalExtraTouchPadding provides if (largerTouchTargets) Space.x2 else 0.dp,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = PoraviaTypography,
            shapes = PoraviaShapes,
            content = content,
        )
    }
}
