package com.agon.app.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private const val THEME_ANIM_MS = 450

@Composable
private fun animatedColor(target: Color): Color {
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(THEME_ANIM_MS, easing = MotionEasing.Standard),
        label = "themeColor",
    )
    return color
}

@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme = ColorScheme(
    primary = animatedColor(target.primary),
    onPrimary = animatedColor(target.onPrimary),
    primaryContainer = animatedColor(target.primaryContainer),
    onPrimaryContainer = animatedColor(target.onPrimaryContainer),
    inversePrimary = animatedColor(target.inversePrimary),
    secondary = animatedColor(target.secondary),
    onSecondary = animatedColor(target.onSecondary),
    secondaryContainer = animatedColor(target.secondaryContainer),
    onSecondaryContainer = animatedColor(target.onSecondaryContainer),
    tertiary = animatedColor(target.tertiary),
    onTertiary = animatedColor(target.onTertiary),
    tertiaryContainer = animatedColor(target.tertiaryContainer),
    onTertiaryContainer = animatedColor(target.onTertiaryContainer),
    background = animatedColor(target.background),
    onBackground = animatedColor(target.onBackground),
    surface = animatedColor(target.surface),
    onSurface = animatedColor(target.onSurface),
    surfaceVariant = animatedColor(target.surfaceVariant),
    onSurfaceVariant = animatedColor(target.onSurfaceVariant),
    surfaceTint = animatedColor(target.surfaceTint),
    inverseSurface = animatedColor(target.inverseSurface),
    inverseOnSurface = animatedColor(target.inverseOnSurface),
    error = animatedColor(target.error),
    onError = animatedColor(target.onError),
    errorContainer = animatedColor(target.errorContainer),
    onErrorContainer = animatedColor(target.onErrorContainer),
    outline = animatedColor(target.outline),
    outlineVariant = animatedColor(target.outlineVariant),
    scrim = animatedColor(target.scrim),
    surfaceBright = animatedColor(target.surfaceBright),
    surfaceDim = animatedColor(target.surfaceDim),
    surfaceContainer = animatedColor(target.surfaceContainer),
    surfaceContainerHigh = animatedColor(target.surfaceContainerHigh),
    surfaceContainerHighest = animatedColor(target.surfaceContainerHighest),
    surfaceContainerLow = animatedColor(target.surfaceContainerLow),
    surfaceContainerLowest = animatedColor(target.surfaceContainerLowest),
)

fun ColorScheme.amoledBackground(amoled: Boolean): ColorScheme =
    if (!amoled) this
    else copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
    )

@Composable
fun rememberChilemeColorScheme(
    seedColor: Color,
    isDark: Boolean,
    isAmoled: Boolean,
    style: PaletteStyle,
    specVersion: ColorSpec.SpecVersion,
): ColorScheme {
    val context = LocalContext.current
    val seed = if (seedColor == Color.Unspecified) {
        (if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
    } else {
        seedColor
    }
    return rememberDynamicColorScheme(
        seedColor = seed,
        isDark = isDark,
        isAmoled = isAmoled,
        style = style,
        specVersion = specVersion.effectiveFor(style),
    ).amoledBackground(isAmoled)
}

@Composable
fun AgonAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    palette: AppPalette = AppPalette.MINT,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val targetScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val base = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // 动态取色也走 materialKolor 以支持 amoled 和 style 统一
            rememberChilemeColorScheme(
                seedColor = Color.Unspecified,
                isDark = darkTheme,
                isAmoled = isAmoled,
                style = paletteStyle,
                specVersion = colorSpec,
            )
        }
        else -> rememberChilemeColorScheme(
            seedColor = palette.seed,
            isDark = darkTheme,
            isAmoled = isAmoled,
            style = paletteStyle,
            specVersion = colorSpec,
        )
    }

    MaterialTheme(
        colorScheme = animateColorScheme(targetScheme),
        shapes = AppShapes,
        content = content,
    )
}

// 兼容旧调用：保留旧签名
@Composable
fun AgonAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    palette: AppPalette = AppPalette.MINT,
    content: @Composable () -> Unit,
) {
    AgonAppTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        palette = palette,
        paletteStyle = PaletteStyle.TonalSpot,
        colorSpec = ColorSpec.SpecVersion.SPEC_2025,
        isAmoled = false,
        content = content,
    )
}
