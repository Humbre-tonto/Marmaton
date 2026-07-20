package com.marmaton.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Color tokens
val DarkBackground = Color(0xFF08080D)
val DarkSurfaceContainer = Color(0xFF181824)
val DarkPrimary = Color(0xFFA7A3FF)
val DarkPrimaryContainer = Color(0xFF2C2A52)
val DarkError = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkSuccessContainer = Color(0xFF123420)
val DarkWarnContainer = Color(0xFF3A2E10)
val DarkNeutralText = Color(0xFF9A98AE)
val DarkNeutralTextBright = Color(0xFFE4E3EE)

val LightBackground = Color(0xFFFAF9FF)
val LightSurfaceContainer = Color(0xFFF1EEFB)
val LightPrimary = Color(0xFF5B54C7)
val LightPrimaryContainer = Color(0xFFE4DFFF)
val LightError = Color(0xFFBA1A1A)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightSuccessContainer = Color(0xFFD7F2DE)
val LightWarnContainer = Color(0xFFFFE8C2)

// Custom Success/Warn colors inside theme context
class CustomColorScheme(
    val successContainer: Color,
    val warnContainer: Color
)

val LocalCustomColors = staticCompositionLocalOf {
    CustomColorScheme(
        successContainer = DarkSuccessContainer,
        warnContainer = DarkWarnContainer
    )
}

// Custom Spacing tokens
object Spacing {
    val space4: Dp = 4.dp
    val space8: Dp = 8.dp
    val space12: Dp = 12.dp
    val space16: Dp = 16.dp
    val space20: Dp = 20.dp
    val space24: Dp = 24.dp
    val space32: Dp = 32.dp
    val space40: Dp = 40.dp
}

// Custom Radii tokens
object Radii {
    val sm12 = 12.dp
    val md16 = 16.dp
    val lg20 = 20.dp
    val xl24 = 24.dp
    val pill100 = 100.dp
}

// Custom typography sizes & styles
object IonVioletTypography {
    val display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    )
    val headline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    )
    val title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
    val bodySm = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
}

val LocalSpacing = staticCompositionLocalOf { Spacing }
val LocalRadii = staticCompositionLocalOf { Radii }

val IonVioletDarkColorScheme = darkColorScheme(
    background = DarkBackground,
    surface = DarkSurfaceContainer,
    surfaceVariant = DarkSurfaceContainer,
    primary = DarkPrimary,
    primaryContainer = DarkPrimaryContainer,
    error = DarkError,
    errorContainer = DarkError,
    onErrorContainer = DarkOnErrorContainer,
    onBackground = DarkNeutralTextBright,
    onSurface = DarkNeutralTextBright,
    onSurfaceVariant = DarkNeutralText
)

val IonVioletLightColorScheme = lightColorScheme(
    background = LightBackground,
    surface = LightSurfaceContainer,
    surfaceVariant = LightSurfaceContainer,
    primary = LightPrimary,
    primaryContainer = LightPrimaryContainer,
    error = LightError,
    errorContainer = LightErrorContainer,
    onBackground = Color(0xFF1B1B23),
    onSurface = Color(0xFF1B1B23),
    onSurfaceVariant = Color(0xFF605D71)
)

@Composable
fun IonVioletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        IonVioletDarkColorScheme
    } else {
        IonVioletLightColorScheme
    }

    val customColors = if (darkTheme) {
        CustomColorScheme(DarkSuccessContainer, DarkWarnContainer)
    } else {
        CustomColorScheme(LightSuccessContainer, LightWarnContainer)
    }

    CompositionLocalProvider(
        LocalSpacing provides Spacing,
        LocalRadii provides Radii,
        LocalCustomColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
