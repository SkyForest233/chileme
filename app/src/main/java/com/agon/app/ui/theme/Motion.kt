package com.agon.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * MD3 motion easing tokens（m3.material.io/styles/motion）。
 * 全项目 tween 动画统一引用本文件，禁止使用默认线性缓动或散落的 CubicBezierEasing 字面量。
 *
 * 使用约定：
 * - 元素进入屏幕（fadeIn/slideIn/expand*）→ [EmphasizedDecelerate]
 * - 元素退出屏幕（fadeOut/slideOut/shrink*）→ [EmphasizedAccelerate]
 * - 状态变化（颜色/尺寸/位移，始终在屏内）→ [Standard] 或强调场景用 [Emphasized]
 */
object MotionEasing {
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}
