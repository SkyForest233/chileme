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
import com.materialkolor.rememberDynamicColorScheme

/**
 * 品牌 shape scale（对应 MD3 shape tokens）：比 MD3 默认更圆润，
 * 与全局胶囊/大圆角风格一致。组件应优先引用 MaterialTheme.shapes 而非魔法数字。
 *
 * extraSmall 8dp / small 12dp / medium 16dp / large 24dp / extraLarge 28dp
 */
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

/**
 * 对整套 ColorScheme 的每个角色做 tween 渐变，
 * 切换配色方案 / 深浅模式 / 动态取色时颜色平滑过渡而非瞬切。
 */
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

@Composable
fun AgonAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    palette: AppPalette = AppPalette.MINT,
    content: @Composable () -> Unit,
) {
    val targetScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> rememberDynamicColorScheme(
            seedColor = palette.seed,
            isDark = darkTheme,
            style = PaletteStyle.TonalSpot,
        )
    }

    MaterialTheme(
        colorScheme = animateColorScheme(targetScheme),
        shapes = AppShapes,
        content = content,
    )
}
