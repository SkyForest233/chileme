package com.agon.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import java.time.LocalDate

/**
 * 当前日期（随生命周期刷新）。UI 中一切「剩余天数 / 状态 / 新鲜度」判定都应读它，
 * 而不是直接 `LocalDate.now()`。
 *
 * - MainActivity 在每个 `ON_RESUME` 用最新 `LocalDate` 提供它（见 MainActivity.setContent）。
 * - 默认值 `LocalDate.now()` 保证未提供时（如单独 @Preview）行为正确。
 * - 用 `compositionLocalOf`：只让读了它的 composable 在日期变化时重组，不波及整棵子树。
 *
 * 配合 `FoodModels.kt` 的 `statusForAt / daysLeftAt / remainingTextAt / elapsedRatioAt / freshnessAt`
 * 使用（这些纯函数可注入日期，单测可传固定日期覆盖跨零点场景）。
 */
val LocalToday = compositionLocalOf { LocalDate.now() }
