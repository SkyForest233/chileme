package com.agon.app.ui.components.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Backdrop captured from page content (never from the floating navigation surface itself). */
internal val LocalMiuixBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Runtime capability + user preference, already gated to the Miuix theme by the app shell. */
internal val LocalMiuixBlurEnabled = staticCompositionLocalOf { false }

/** Liquid-glass is a separate presentation choice for the Miuix floating navigation bar. */
internal val LocalMiuixLiquidGlassNavEnabled = staticCompositionLocalOf { false }

@Composable
internal fun rememberMiuixSurfaceBlurColors(alpha: Float): BlurColors = BlurDefaults.blurColors(
    blendColors = listOf(
        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = alpha)),
    ),
)
