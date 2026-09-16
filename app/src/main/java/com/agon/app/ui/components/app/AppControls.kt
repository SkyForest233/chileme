package com.agon.app.ui.components.app

// 输入与筛选控件：搜索框、筛选 chip。两主题的控件 API 完全不同（MD3 OutlinedTextField /
// Miuix InputField；chip 两版都用 material3 FilterChip，只是取色与标签字号走各自主题），
// 收在这里，屏幕层不再出现第二套调用。
//
// 2026-09-16 由 ArchiveScreen + MiuixArchiveScreen 合并时抽出。

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.MotionSpring
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.InputField as MiuixInputField
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 单行搜索框。
 *
 * 两主题差别很大，不是"换个皮"：MD3 是圆角 50 的 `OutlinedTextField` + 左侧放大镜 + placeholder；
 * Miuix 是库的 `InputField`，用 `label` 而不是 placeholder，也没有前导图标。
 * `label` 一个参数同时喂两边（MD3 当 placeholder 文案、Miuix 当 label）。
 *
 * Miuix 侧那三个空实现（`onSearch` / `expanded` / `onExpandedChange`）照抄合并前
 * `MiuixArchiveScreen` 的调用形态——那是仓库里已经编译通过、且真机验过的写法，
 * 不靠记忆补参数（`CLAUDE.md` 明令禁止臆造 Miuix 签名）。
 */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixInputField(
            query = value,
            onQueryChange = onValueChange,
            onSearch = {},
            expanded = false,
            onExpandedChange = {},
            label = label,
            modifier = modifier,
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            placeholder = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(50),
        )
    }
}

/** chip 选中态容器色的语义档：列表页三排筛选各用一种（状态/分类/位置），归档原因那排用 [Primary]。 */
enum class AppChipTone { Primary, Secondary, Tertiary }

/**
 * 筛选用胶囊 chip（「全部 / 各归档原因」这一排，以及列表页的状态 / 分类 / 位置三排）。
 *
 * 两版原来用的都是 **material3 `FilterChip`**（Miuix 侧靠 `MiuixRootTheme` 的 MaterialTheme 桥接取色），
 * 这里保持原样，只把「取哪套色板、标签用哪档字号」分流：
 * MD3 用 `MaterialTheme.colorScheme` + 默认标签样式；Miuix 用 `MiuixTheme.colorScheme` + `body2` 标签。
 *
 * @param tone 选中态容器色。**只有 [AppChipTone.Primary] 两版都额外指定了选中态文字色**
 *   （`onPrimaryContainer`）；Secondary / Tertiary 两版都只指定容器色、文字色留给库默认
 *   （material3 `filterChipColors` 的默认 `selectedLabelColor` = `onSecondaryContainer`）。
 *   不替库猜默认值 —— 与 `MiuixConfirmButton` 同一原则。
 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    tone: AppChipTone = AppChipTone.Primary,
) {
    val miuix = LocalThemeStyle.current == ThemeStyle.MIUIX
    val container = when (tone) {
        AppChipTone.Primary ->
            if (miuix) MiuixTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer
        AppChipTone.Secondary ->
            if (miuix) MiuixTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer
        AppChipTone.Tertiary ->
            if (miuix) MiuixTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            if (miuix) {
                MiuixText(label, style = MiuixTheme.textStyles.body2)
            } else {
                Text(label)
            }
        },
        shape = RoundedCornerShape(50),
        colors = if (tone == AppChipTone.Primary) {
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = container,
                selectedLabelColor = if (miuix) {
                    MiuixTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
        } else {
            FilterChipDefaults.filterChipColors(selectedContainerColor = container)
        },
    )
}

/** 筛选面板的分组小标题（「状态」/「分类」/「位置」）：Tag 档 + 弱化色，左右 20dp、上下 2dp（两版一致）。 */
@Composable
fun AppFilterSectionLabel(text: String, modifier: Modifier = Modifier) {
    AppText(
        text,
        AppTextScale.Tag,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        color = appMutedColor(),
    )
}

/**
 * 「筛选」切换胶囊：图标 + 文案（带激活数量）+ 会转 180° 的箭头；有激活筛选时整颗换成 primaryContainer 配色。
 *
 * 两主题差异（合并前两版逐项对照过）：
 * ① 字形：MD3 `FilterList` / `ExpandMore`，Miuix `Filter` / `ExpandMore`；
 * ② 外壳各用自家库的 `Surface(onClick)`（圆角 50、未激活底色 `surfaceContainerHighest`）；
 * ③ **箭头弹簧不同**：MD3 用本仓库的 `MotionSpring.expand()`，Miuix 用库的
 *   `folmeSpring(damping = 0.95f, response = 展开 0.2f / 收起 0.3f)` —— 两套手感是原版就有的，不统一；
 * ④ 文案档位 = [AppTextScale.Action]（MD3 `labelLarge` / Miuix `body2`）；
 * ⑤ 未激活时的图标与文字色 = [appMutedColor]，激活时 = [appOnPrimaryContainerColor]。
 *
 * `AnimatedContent` 只写一份（数字变化时 150ms 淡入淡出 + 缩放），两分支只差叶子组件，
 * 所以外壳与图标各抽一个私有叶子；`transitionSpec` 两版逐字相同，照抄。
 */
@Composable
fun AppFilterToggle(expanded: Boolean, activeCount: Int, onClick: () -> Unit) {
    val active = activeCount > 0
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
            folmeSpring<Float>(damping = 0.95f, response = if (expanded) 0.2f else 0.3f)
        } else {
            MotionSpring.expand<Float>()
        },
        label = "filterArrow",
    )
    FilterToggleShell(onClick = onClick, active = active) {
        FilterToggleIcon(active = active, md3 = Icons.Rounded.FilterList, miuix = MiuixIcons.Filter)
        AnimatedContent(
            targetState = activeCount,
            transitionSpec = {
                (fadeIn(tween(150)) + androidx.compose.animation.scaleIn(tween(150)))
                    .togetherWith(fadeOut(tween(150)) + androidx.compose.animation.scaleOut(tween(150)))
            },
            label = "filterCount",
        ) { count ->
            AppText(
                if (count > 0) "筛选($count)" else "筛选",
                AppTextScale.Action,
                color = if (count > 0) appOnPrimaryContainerColor() else appMutedColor(),
            )
        }
        FilterToggleIcon(
            active = active,
            md3 = Icons.Rounded.ExpandMore,
            miuix = MiuixIcons.ExpandMore,
            rotation = rotation,
        )
    }
}

/** 胶囊外壳：圆角 50，激活时 primaryContainer、否则 surfaceContainerHighest（两主题各自取色）。 */
@Composable
private fun FilterToggleShell(
    onClick: () -> Unit,
    active: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = if (active) {
                MiuixTheme.colorScheme.primaryContainer
            } else {
                MiuixTheme.colorScheme.surfaceContainerHighest
            },
        ) {
            FilterToggleRow(content)
        }
    } else {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ) {
            FilterToggleRow(content)
        }
    }
}

@Composable
private fun FilterToggleRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** 胶囊里的 18dp 图标：`rotation` 非 null 时按角度转（展开箭头），着色随激活态切换。 */
@Composable
private fun FilterToggleIcon(
    active: Boolean,
    md3: ImageVector,
    miuix: ImageVector,
    rotation: Float? = null,
) {
    val tint = if (active) appOnPrimaryContainerColor() else appMutedColor()
    val modifier = if (rotation != null) {
        Modifier
            .size(18.dp)
            .graphicsLayer { rotationZ = rotation }
    } else {
        Modifier.size(18.dp)
    }
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixIcon(miuix, contentDescription = null, modifier = modifier, tint = tint)
    } else {
        Icon(md3, contentDescription = null, modifier = modifier, tint = tint)
    }
}
