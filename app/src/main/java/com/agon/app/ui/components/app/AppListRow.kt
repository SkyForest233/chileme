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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Location
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

/**
 * 不可删行的来源标注（「月度合计」）。走 [AppTextScale.Tag] + 弱化色 —— 第 5 对发现列表页筛选面板的
 * 分组小标题是同一映射（MD3 `labelSmall` / Miuix `footnote2`，都配弱化色），于是把这一档提进档位表，
 * 这里改成委托；取值与委托前逐字相同。
 */
@Composable
private fun RowTag(tag: String) = AppText(tag, AppTextScale.Tag, color = appMutedColor())

/**
 * 卡片里的一行：**前导槽**（emoji 或图标）+ 标题 + 可选副标题 + **尾部槽**（步进器、操作按钮）。
 * 管理页三行（阈值 / 分类 / 位置）合并前逐字同构，只有前导与尾部不同。
 *
 * 与 [AppListRow] 的分工：那个是「emoji + 标题 + 副标题 + 行尾强调文本」的**固定组合**（消耗记录行在用），
 * 这里两侧都是槽位，因为管理页的行尾是控件而不是文本。外壳走 [AppCard] 且
 * **Miuix 侧圆角传 24dp** —— 合并前两版都是 24dp（MD3 `shapes.large`、Miuix 显式 `RoundedCornerShape(24.dp)`），
 * 若走 [AppCard] 的默认 16dp 就是只有真机看得出来的视觉改动。
 *
 * @param verticalPadding 行内上下留白。合并前阈值行 8dp、分类/位置行 6dp，**两版一致**，故留参数不统一。
 * @param modifier 只作用于卡片外壳，`fillMaxWidth()` 由内部补，所以调用方的 `animateItem()` 会排在它前面
 *   （与 [AppActionRow] 同一情况，第 2 对已真机验证过）。
 * @param subtitle 传 null 就不渲染（分类/位置行只在「有食品在用」时才显示这一行）。
 *   标题走 [AppTextScale.Body] + Medium、副标题走 [AppHintText]，都是两版原来的取值。
 *   合并前阈值行是把 `weight(1f)` 直接写在标题 `Text` 上、没有 `Column` 包裹，这里统一成 `Column`
 *   （无副标题时渲染等价：宽度分配与垂直居中都一样）—— 结构统一，取值不动。
 */
@Composable
fun AppCardRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    verticalPadding: Dp = 6.dp,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    AppCard(modifier = modifier.fillMaxWidth(), miuixCornerRadius = 24.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                AppText(title, AppTextScale.Body, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    AppHintText(subtitle)
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

/**
 * 位置行的前导图标：MD3 `Place` / Miuix `Location`，18dp + 弱化色。
 * 装饰性图标，两版都不给 contentDescription（旁边那行文字已经念出位置名）。
 */
@Composable
fun AppLocationIcon(modifier: Modifier = Modifier) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixIcon(
            MiuixIcons.Location,
            contentDescription = null,
            modifier = modifier.size(18.dp),
            tint = appMutedColor(),
        )
    } else {
        Icon(
            Icons.Rounded.Place,
            contentDescription = null,
            modifier = modifier.size(18.dp),
            tint = appMutedColor(),
        )
    }
}

/** 行内「编辑」图标按钮：两版都是各自的 `Edit` 字形、18dp、主色。 */
@Composable
fun AppEditRowAction(onClick: () -> Unit, contentDescription: String?) {
    RowIconButton(
        onClick = onClick,
        enabled = true,
        contentDescription = contentDescription,
        md3 = Icons.Rounded.Edit,
        miuix = MiuixIcons.Edit,
        tint = appPrimaryColor(),
    )
}

/**
 * 行内「删除」图标按钮：两版都是各自的 `Delete` 字形、18dp。
 * **可点时危险色、禁用时最弱色**（MD3 `outlineVariant` / Miuix `dividerLine`）——
 * 分类只剩一个时删除按钮禁用，两版都是这个规则，故收进组件而不是让屏幕传色。
 */
@Composable
fun AppDeleteRowAction(
    onClick: () -> Unit,
    contentDescription: String?,
    enabled: Boolean = true,
) {
    RowIconButton(
        onClick = onClick,
        enabled = enabled,
        contentDescription = contentDescription,
        md3 = Icons.Rounded.Delete,
        miuix = MiuixIcons.Delete,
        tint = if (enabled) appErrorColor() else appFaintColor(),
    )
}

/**
 * 行内 18dp 图标按钮的公共实现。
 *
 * **不接 `modifier` 参数**：合并前两版都没往这两个 `IconButton` 上传 modifier，
 * 而 Miuix 的 `IconButton` 是否有该形参本轮没有按 pinned source 核过 —— 按 `CLAUDE.md`
 * 「不得凭记忆臆造 Miuix 签名」，不需要的参数就不加（要加时先核上游）。
 * 触摸目标保持库默认（48dp），只有里面的字形是 18dp，与 `docs/DESIGN_SPEC.md` §6 一致。
 */
@Composable
private fun RowIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String?,
    md3: ImageVector,
    miuix: ImageVector,
    tint: Color,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixIconButton(onClick = onClick, enabled = enabled) {
            MiuixIcon(miuix, contentDescription = contentDescription, modifier = Modifier.size(18.dp), tint = tint)
        }
    } else {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(md3, contentDescription = contentDescription, modifier = Modifier.size(18.dp), tint = tint)
        }
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
 *
 * @param tone 外壳底色档：归档页用默认的 [AppCardTone.Container]；列表页「归档中找到 N 条」那些行
 *   合并前 MD3 用的是 `surfaceContainerLow`（低一档），故传 [AppCardTone.ContainerLow]。只影响 MD3 侧。
 */
@Composable
fun AppActionRow(
    title: String,
    subtitle: String,
    tone: AppCardTone = AppCardTone.Container,
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
    AppPaddedCard(modifier.fillMaxWidth(), tone = tone) {
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
 * 标题走 [AppTextScale.SectionTitle]、链接走 [AppTextScale.Action] —— 合并前两版各自的取值。
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
            AppText(linkLabel, AppTextScale.Action, color = appPrimaryColor())
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
