package com.agon.app.ui.components.app

// 信息行、分隔线、线性进度条：详情页那三块「标签-值」卡片与状态卡的进度条在用。
// 2026-09-16 由 FoodDetailScreen + MiuixFoodDetailScreen 合并时抽出。

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Recent

/** 详情行标签列宽（两版原本都是 88dp，照抄）。 */
private val DetailLabelWidth = 88.dp

/** 进度条高度（两版原本都是 8dp）。 */
private val LinearProgressHeight = 8.dp

/**
 * 「标签 - 值」信息行：标签走元信息档位 + 弱化色、固定 88dp 宽，值走正文档位 + Medium。
 * 两版原来的 `DetailRow` 私有函数逐字对等，只是取的档位/色板不同。
 */
@Composable
fun AppDetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppText(
            label,
            AppTextScale.Meta,
            modifier = Modifier.width(DetailLabelWidth),
            color = appMutedColor(),
        )
        AppText(value, AppTextScale.Body, fontWeight = FontWeight.Medium)
    }
}

/** 分隔线。调用方自己给间距（详情页是 `Modifier.padding(vertical = 10.dp)`）。 */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixHorizontalDivider(modifier)
    } else {
        HorizontalDivider(modifier)
    }
}

/**
 * 确定性线性进度条（详情页的「时间过去多少走多少」）。
 *
 * 两侧宽度处理方式不同，都按合并前的原样保留：
 * - MD3：调用处显式 `fillMaxWidth() + height(8.dp) + clip(圆角 50)`，故这三项收在组件里；
 * - Miuix：上游 `LinearProgressIndicator`（v0.9.4-rc01，`ProgressIndicator.kt:88-91`）**内部自带**
 *   `.fillMaxWidth().height(height)`，所以调用方不传宽度也是满宽 —— 抽象前后一致，不会变宽变窄。
 */
@Composable
fun AppLinearProgress(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixLinearProgressIndicator(
            modifier = modifier,
            progress = progress,
            height = LinearProgressHeight,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = color,
                backgroundColor = trackColor,
            ),
        )
    } else {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier
                .fillMaxWidth()
                .height(LinearProgressHeight)
                .clip(RoundedCornerShape(50)),
            color = color,
            trackColor = trackColor,
        )
    }
}

/**
 * 「归档中找到 N 条」这类带小图标的说明行：字形 MD3 `History` / Miuix `Recent`，16dp + 弱化色；
 * 文字走 [AppTextScale.Label]（MD3 `labelMedium` / Miuix `footnote2`）+ 弱化色，图标与文字间隔 6dp。
 * 外框间距（列表页是 `fillMaxWidth().padding(top = 8.dp).animateItem()`）由调用方的 modifier 给。
 *
 * 与顶栏的 [AppArchiveAction] 用的是同一对字形，但那是 IconButton + primary 色，这是纯图标 + 弱化色。
 */
@Composable
fun AppHistoryNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
            MiuixIcon(
                MiuixIcons.Recent,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = appMutedColor(),
            )
        } else {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = appMutedColor(),
            )
        }
        Spacer(Modifier.width(6.dp))
        AppText(text, AppTextScale.Label, color = appMutedColor())
    }
}
