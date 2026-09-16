package com.agon.app.ui.components.app

// 通用列表行：emoji + 主标题 + 副标题 + 右侧强调文本 + （删除按钮 | 文字标注）。
// 「一条记录一行」的页面共用（消耗记录已用，归档历史/统计明细等同构）。
// 2026-09-16 由 ConsumptionRow + MiuixConsumptionRow 合并抽出，两版的排版参数逐项对齐。

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * @param trailing 右侧强调文本（如「×3 份」），用主题色。
 * @param trailingTag 没有删除入口时显示的文字标注（如「月度合计」）。
 * @param onDelete 为 null 即表示这一行不可删除——是否可删由调用方按业务规则判断
 *   （消耗记录用 `ConsumptionRecord.isDeletable()`），组件层不认识领域类型。
 */
@Composable
fun AppListRow(
    emoji: String,
    title: String,
    subtitle: String,
    trailing: String,
    modifier: Modifier = Modifier,
    trailingTag: String? = null,
    onDelete: (() -> Unit)? = null,
    deleteContentDescription: String? = null,
) {
    AppCard(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowEmoji(emoji)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                RowTitle(title)
                AppHintText(subtitle)
            }
            RowTrailing(trailing)
            Spacer(Modifier.width(4.dp))
            if (onDelete != null) {
                RowDeleteButton(onDelete, deleteContentDescription)
            } else if (trailingTag != null) {
                RowTag(trailingTag)
            }
        }
    }
}

@Composable
private fun RowEmoji(emoji: String) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixText(emoji, style = MiuixTheme.textStyles.title3)
    } else {
        Text(emoji, fontSize = 20.sp)
    }
}

@Composable
private fun RowTitle(title: String) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixText(title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
    } else {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RowTrailing(trailing: String) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixText(
            trailing,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.primary,
        )
    } else {
        Text(
            trailing,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 不可删行的来源标注。MD3 原来用 `labelSmall`（比副标题再小一档），故不复用 [AppHintText]。 */
@Composable
private fun RowTag(tag: String) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixText(
            tag,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    } else {
        Text(
            tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 删除按钮：两主题图标字形不同（MD3 `DeleteForever` / Miuix `Delete`），都用 error 色、20dp。 */
@Composable
private fun RowDeleteButton(onDelete: () -> Unit, contentDescription: String?) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixIconButton(onClick = onDelete) {
            MiuixIcon(
                MiuixIcons.Delete,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.error,
            )
        }
    } else {
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.DeleteForever,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
