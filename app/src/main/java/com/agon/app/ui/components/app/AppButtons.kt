package com.agon.app.ui.components.app

// 页面主行动按钮与「编辑」次级按钮。
//
// 两主题的按钮形态差异是这一对里最大的：MD3 主按钮是圆角 50 的胶囊 + secondaryContainer 配色，
// Miuix 走库的 buttonColorsPrimary()（配色与圆角都由库给）；次级按钮更悬殊 —— MD3 是带铅笔图标的
// OutlinedButton 胶囊，Miuix 原本就是**无图标**的 TextButton。这个不对称合并前就存在，照原样保留。
//
// 2026-09-16 由 FoodDetailScreen + MiuixFoodDetailScreen 合并时抽出。

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面主行动大按钮：emoji + 文案 + 可选角标（详情页的「😋 吃掉一份！ ×3」）。
 *
 * 用固定语义参数而不是 content 槽位，有两个原因：① 文案的字号/颜色两主题不同
 * （Miuix 侧必须显式传 `onPrimary`，MD3 侧靠 Button 的 contentColor 继承），
 * 开槽位就等于把 `if (isMiuix)` 推回屏幕层；② 两主题 Button 的 content 接收者类型
 * 不一定同为 `RowScope`，不传 lambda 就不用赌这件事。
 *
 * @param badge 右上角标文案（如 `×3`），null 表示不显示。
 */
@Composable
fun AppBigButton(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: String? = null,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = MiuixButtonDefaults.buttonColorsPrimary(),
        ) {
            MiuixText(emoji, fontSize = 22.sp, color = MiuixTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
            MiuixText(
                label,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onPrimary,
            )
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                MiuixText(
                    badge,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Text(badge, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 「编辑」次级按钮：MD3 = 圆角 50 的 `OutlinedButton` + 18dp 铅笔图标 + 文案；
 * Miuix = 库的 `TextButton`，**只有文案没有图标**（合并前就是这样，不是漏改）。
 */
@Composable
fun AppEditButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixTextButton(text = label, onClick = onClick, modifier = modifier)
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(50),
        ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}
