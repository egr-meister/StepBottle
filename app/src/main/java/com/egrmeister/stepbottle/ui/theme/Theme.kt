package com.egrmeister.stepbottle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Card palettes for the two independent categories. Labels and icons always accompany colour. */
@Immutable
data class CategoryColors(
    val waterContainer: Color,
    val onWaterContainer: Color,
    val waterAccent: Color,
    val activityContainer: Color,
    val onActivityContainer: Color,
    val activityAccent: Color,
)

val LocalCategoryColors = staticCompositionLocalOf {
    CategoryColors(Color.LightGray, Color.Black, Color.DarkGray, Color.LightGray, Color.Black, Color.DarkGray)
}

private val Slate = Color(0xFF2D3A45)
private val SlateMuted = Color(0xFF4F5B66)

private val scheme = lightColorScheme(
    primary = Color(0xFF3E5F7A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E5EF),
    onPrimaryContainer = Color(0xFF1E3446),
    secondary = Color(0xFF4F6F4B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7D6),
    onSecondaryContainer = Color(0xFF223620),
    tertiary = Color(0xFF6B5E4A),
    onTertiary = Color.White,
    background = Color(0xFFFAF6EE),
    onBackground = Slate,
    surface = Color(0xFFFAF6EE),
    onSurface = Slate,
    surfaceVariant = Color(0xFFEDE7DA),
    onSurfaceVariant = SlateMuted,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F1E7),
    surfaceContainer = Color(0xFFF1ECE1),
    surfaceContainerHigh = Color(0xFFEBE5D9),
    surfaceContainerHighest = Color(0xFFE5DFD2),
    outline = Color(0xFF7A8590),
    outlineVariant = Color(0xFFCFC8BA),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val categoryColors = CategoryColors(
    waterContainer = Color(0xFFDCE6EF),
    onWaterContainer = Color(0xFF1F3344),
    waterAccent = Color(0xFF4A6F8F),
    activityContainer = Color(0xFFDFE8DA),
    onActivityContainer = Color(0xFF21331F),
    activityAccent = Color(0xFF557A50),
)

private val typography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    )
}

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun StepBottleTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCategoryColors provides categoryColors) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
