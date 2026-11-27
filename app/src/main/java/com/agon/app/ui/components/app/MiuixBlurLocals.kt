package com.agon.app.ui.components.app

import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/** Page-body capture sampled by GlassTopAppBar; it excludes the top bar and all glass consumers. */
internal val LocalMiuixPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Main navigation-content capture sampled by the bottom navigation surface and Glass FAB. */
internal val LocalMiuixNavigationBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Whole app-content capture sampled by root-hosted dialogs and Glass dropdown popups. */
internal val LocalMiuixRootBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Runtime capability + user preference, already gated to the Miuix theme by the app shell. */
internal val LocalMiuixBlurEnabled = staticCompositionLocalOf { false }

/** Liquid-glass is a separate presentation choice for the Miuix floating navigation bar. */
internal val LocalMiuixLiquidGlassNavEnabled = staticCompositionLocalOf { false }
