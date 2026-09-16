package com.agon.app.ui.components.app

// 卡片容器与弱化文字：两主题的圆角/色板/字号档位不同，这里做一次映射，屏幕层只写一份。
// 2026-09-16 由 ConsumptionLogScreen + MiuixConsumptionLogScreen 合并时抽出。

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 列表行/信息块的卡片底：MD3 走 `shapes.large` + `surfaceContainer`，
 * Miuix 走 16dp 圆角 + 同名色。不传 `contentColor`，与合并前两版的用法一致。
 */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = MiuixTheme.colorScheme.surfaceContainer,
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            content()
        }
    }
}

/**
 * 弱化说明文字：列表上方的提示语、行内副标题都用它
 * （MD3 `bodySmall` + `onSurfaceVariant` / Miuix `footnote2` + `onSurfaceVariantSummary`）。
 */
@Composable
fun AppHintText(text: String, modifier: Modifier = Modifier) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixText(
            text,
            modifier = modifier,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    } else {
        Text(
            text,
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
