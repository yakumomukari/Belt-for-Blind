package com.beltforblind.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BeltLightColorScheme = lightColorScheme(
    primary = BeltColors.PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = BeltColors.PurpleContainer,
    onPrimaryContainer = BeltColors.PrimaryPurpleDark,
    secondary = BeltColors.Success,
    onSecondary = Color.White,
    background = BeltColors.Background,
    onBackground = BeltColors.TextPrimary,
    surface = BeltColors.Surface,
    onSurface = BeltColors.TextPrimary,
    surfaceVariant = BeltColors.PurpleContainer,
    onSurfaceVariant = BeltColors.TextSecondary,
    outline = BeltColors.Disabled,
    outlineVariant = BeltColors.Divider,
    error = BeltColors.Error,
    onError = Color.White,
    errorContainer = BeltColors.ErrorContainer,
    onErrorContainer = BeltColors.Error,
)

private val BeltShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object BeltSpacing {
    val ExtraSmall = 8.dp
    val Small = 12.dp
    val Medium = 16.dp
    val Page = 20.dp
    val Large = 24.dp
}

@Composable
fun BeltTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BeltLightColorScheme,
        typography = BeltTypography,
        shapes = BeltShapes,
        content = content,
    )
}
