// Copyright 2026, chileme contributors
// Adapted from compose-miuix-ui/miuix example's Vibrancy.kt (Apache-2.0).
// SPDX-License-Identifier: Apache-2.0

package com.agon.app

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

/** Adds the color saturation used by Miuix's iOS liquid-glass demo. */
internal fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}
