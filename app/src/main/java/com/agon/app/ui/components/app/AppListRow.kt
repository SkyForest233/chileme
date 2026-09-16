package com.agon.app.ui.components.app

// 通用列表行：emoji + 主标题 + 副标题 + 右侧强调文本 + （删除按钮 | 文字标注）。
// 「一条记录一行」的页面共用：AppListRow = emoji + 右侧强调文本（消耗记录）；
// AppActionRow = 头像槽位 + 两个图标操作（归档行，管理页的分类/位置行同构）。
// 2026-09-16 由 ConsumptionRow + MiuixConsumptionRow 合并抽出，两版的排版参数逐项对齐。

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Refresh
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
private fun RowTitle(title: String) = AppText(title, AppTextScale.Body, fontWeight = FontWeight.Medium)

@Composable
private fun RowTrailing(trailing: String) =
    AppText(
        trailing,
        AppTextScale.Meta,
        fontWeight = FontWeight.SemiBold,
        color = appPrimaryColor(),
    )

/** 不可删行的来源标注。MD3 原来用 `labelSmall`（比副标题再小一档、也不在 AppTextScale 表里），故不复用 [AppHintText]。 */
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

/**
 * 带头像槽位的操作行：leading + 标题 + 副标题 + 右侧「主操作 + 危险操作」两个图标按钮。
 * 归档行（恢复 / 彻底删除）在用；管理页的分类行、存放位置行（编辑 / 删除）同构。
 *
 * 外壳走 [AppPaddedCard]（Miuix 侧是官方 `Card`、带库默认内衬），与合并前的 `MiuixArchiveRow`
 * 一致；**不要**换成 [AppCard]，那会让 Miuix 归档行少一层内衬（见 `AppPaddedCard` 的 KDoc）。
 *
 * `leading` 交给调用方（归档行传 `FoodAvatar`），组件层不认识领域类型。
 */
@Composable
fun AppActionRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    restoreDescription: String = "恢复",
    onDelete: (() -> Unit)? = null,
    deleteDescription: String = "彻底删除",
) {
    // 行内容两主题完全一致，只有外壳不同 —— 抽成一个 RowScope lambda 传进去，避免整段抄两遍
    val body: @Composable RowScope.() -> Unit = {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            RowHeading(title)
            AppHintText(subtitle)
        }
        if (onRestore != null) RowRestoreButton(onRestore, restoreDescription)
        if (onDelete != null) RowDeleteButton(onDelete, deleteDescription)
    }
    AppPaddedCard(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = body,
        )
    }
}

/** 行主标题（比 [AppListRow] 的标题重一档，且强制单行省略）：Heading 档位 = MD3 `titleSmall` / Miuix `subtitle`。 */
@Composable
private fun RowHeading(title: String) =
    AppText(
        title,
        AppTextScale.Heading,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

/** 主操作按钮（恢复 / 撤销归档）：primary 色，MD3 `RestartAlt` / Miuix `Refresh`，尺寸用各自默认值。 */
@Composable
private fun RowRestoreButton(onRestore: () -> Unit, contentDescription: String) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixIconButton(onClick = onRestore) {
            MiuixIcon(
                MiuixIcons.Refresh,
                contentDescription = contentDescription,
                tint = MiuixTheme.colorScheme.primary,
            )
        }
    } else {
        IconButton(onClick = onRestore) {
            Icon(
                Icons.Rounded.RestartAlt,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 分区标题 + 右侧文字链接（首页「需要处理 …… 全部食品 →」）。
 *
 * 两主题的箭头字形不同（MD3 `ArrowForward` / Miuix `Forward`），链接文字与箭头都用强调色，
 * 点击区是圆角 50 的胶囊（`clip` 在 `clickable` 之前，涟漪才是胶囊形）。
 * 标题走 [AppTextScale.SectionTitle]、链接走 [AppTextScale.Link] —— 合并前两版各自的取值。
 *
 * `modifier` 由调用方给：首页传 `fillMaxWidth().padding(top = 8.dp)`，与合并前逐字一致。
 */
@Composable
fun AppSectionHeader(
    title: String,
    linkLabel: String,
    onLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AppText(
            title,
            AppTextScale.SectionTitle,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { onLink() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(linkLabel, AppTextScale.Link, color = appPrimaryColor())
            Spacer(Modifier.width(4.dp))
            if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
                MiuixIcon(
                    MiuixIcons.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = appPrimaryColor(),
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = appPrimaryColor(),
                )
            }
        }
    }
}
