// Copyright 2026, chileme contributors
// Portions adapted from Miuix v0.9.4's Apache-2.0 example:
// Copyright 2026, compose-miuix-ui contributors
// Original example attribution: Kyant0/AndroidLiquidGlass (Apache-2.0).
// SPDX-License-Identifier: Apache-2.0

package com.agon.app

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect

/**
 * Subtle rounded-pill edge refraction for the optional liquid-glass navigation surface.
 *
 * The SDF lens follows the rounded-rectangle pattern in Miuix v0.9.4's Apache-2.0 example,
 * simplified for this pill and built only on the library's public backdrop-effect API.
 * Pixel uniforms are scaled to the backdrop's current downsampled recording size.
 */
internal fun BackdropEffectScope.liquidGlassPillLens(
    refractionHeight: Float,
    refractionAmount: Float,
) {
    if (!isRuntimeShaderSupported() || refractionHeight <= 0f || refractionAmount <= 0f) return
    if (size.width <= 0f || size.height <= 0f) return

    if (padding < refractionAmount) padding = refractionAmount

    val scale = downscaleFactor.coerceAtLeast(1).toFloat()
    runtimeShaderEffect(
        key = "ChilemeLiquidGlassPillLens",
        shaderString = LIQUID_GLASS_PILL_LENS_SHADER,
        uniformShaderName = "content",
    ) {
        setFloatUniform("size", size.width / scale, size.height / scale)
        setFloatUniform("offset", -padding / scale, -padding / scale)
        setFloatUniform("refractionHeight", refractionHeight / scale)
        setFloatUniform("refractionAmount", -refractionAmount / scale)
    }
}

private const val LIQUID_GLASS_PILL_LENS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float refractionHeight;
uniform float refractionAmount;

float sdRoundedRect(float2 point, float2 halfSize, float radius) {
    float2 corner = abs(point) - (halfSize - float2(radius));
    float outside = length(max(corner, float2(0.0))) - radius;
    float inside = min(max(corner.x, corner.y), 0.0);
    return outside + inside;
}

float2 roundedRectGradient(float2 point, float2 halfSize, float radius) {
    float2 corner = abs(point) - (halfSize - float2(radius));
    float2 outside = max(corner, float2(0.0));
    float outsideLength = length(outside);
    if (outsideLength > 0.0001) {
        return sign(point) * outside / outsideLength;
    }
    float chooseX = step(corner.y, corner.x);
    return sign(point) * float2(chooseX, 1.0 - chooseX);
}

float circleMap(float value) {
    return 1.0 - sqrt(max(0.0, 1.0 - value * value));
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centered = coord + offset - halfSize;
    float radius = min(halfSize.x, halfSize.y);
    float signedDistance = sdRoundedRect(centered, halfSize, radius);
    if (-signedDistance >= refractionHeight) {
        return content.eval(coord);
    }

    float edge = clamp(1.0 + signedDistance / refractionHeight, 0.0, 1.0);
    float displacement = circleMap(edge) * refractionAmount;
    float2 gradient = roundedRectGradient(centered, halfSize, radius);
    return content.eval(coord + displacement * gradient);
}
"""
