package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.components.app.AppAddItemButton
import com.agon.app.ui.components.app.AppCardRow
import com.agon.app.ui.components.app.AppConfirmDialog
import com.agon.app.ui.components.app.AppDeleteRowAction
import com.agon.app.ui.components.app.AppEditRowAction
import com.agon.app.ui.components.app.AppEmojiText
import com.agon.app.ui.components.app.AppFormDialog
import com.agon.app.ui.components.app.AppFormFieldSpec
import com.agon.app.ui.components.app.AppHintText
import com.agon.app.ui.components.app.AppLocationIcon
import com.agon.app.ui.components.app.AppScaffold
import com.agon.app.ui.components.app.AppStepperPill
import com.agon.app.viewmodel.AppViewModel

/**
 * 三个二级管理页：**临期提醒阈值 / 分类管理 / 存放位置管理**。
 *
 * 2026-09-16 由 `ManageScreens.kt`(458) + `MiuixManageScreens.kt`(434) 合并为单文件双主题
 * （第三批 #3 第 6 对）。三页在两版里逐字同构（连 `contentPadding`、`spacedBy(10.dp)`、
 * 「N 条食品使用中」的文案都一样），差异集中在五处叶子：两份私有的 `*ManageScaffold`、
 * 输入弹窗的机制、添加按钮的形态、阈值行的步进器字形、禁用删除按钮的取色。
 *
 * 两份私有的 `*ManageScaffold` 这次一并删掉了 —— `AppScaffold` 从第 1 对起就是它的替代品
 * （`docs/ARCHITECTURE.md` 当时就写了「待管理页那一对合并时删除」）。MD3 侧原本给 `Scaffold`
 * 与 `TopAppBar` 都显式写了 `containerColor = background`，`AppScaffold` / `AppTopBar` 的默认
 * 行为与之逐字相同；Miuix 侧原本两个都不写，走库默认，也相同。
 *
 * 几处照抄而非统一的地方（细节在各组件的 KDoc 里）：
 * - **输入弹窗**：MD3 边打字边截断，Miuix 允许超长、点确认才截断 → [AppFormDialog] 保留两套机制。
 *   `AppFormFieldSpec.placeholder` 只在 MD3 侧生效（Miuix 的 `TextField` 用 `useLabelAsPlaceholder`）。
 * - **添加按钮**：MD3 是 `OutlinedButton` 胶囊 + 加号图标，Miuix 是库的 `TextButton`（无图标）→ [AppAddItemButton]。
 * - **步进器的减号**：两版都用 material 的 `Remove`（Miuix 的 `Remove` 是「移除/退出」形状，
 *   合并前 Miuix 版明确回退了 material 字形），只有加号分主题 → [AppStepperPill]。
 * - **卡片圆角**：两版都是 24dp（MD3 `shapes.large`、Miuix 显式 `RoundedCornerShape(24.dp)`），
 *   而 [AppCard] 的 Miuix 默认是库的 16dp，故 [AppCardRow] 传 `miuixCornerRadius = 24.dp`。
 * - **禁用删除按钮的取色**：MD3 `outlineVariant` / Miuix `dividerLine` → 收进 `appFaintColor()`。
 * - 阈值行的行内上下留白是 8dp，分类/位置行是 6dp（两版一致）→ [AppCardRow] 留 `verticalPadding` 参数。
 *
 * **两处非等价改动（刻意，与第 2/3/4/5 对同一处理）**：
 * ① 三个弹窗全部挪进 `AppScaffold` 的 content lambda —— Miuix `WindowDialog` 要求无条件调用 + `show`
 *   控制（见 `MiuixDialog.kt`）；合并前 MD3 版把弹窗写在 Scaffold 外面、Miuix 版把「编辑分类」写成
 *   `state.editing?.let { MiuixCategoryEditDialog(show = true, …) }`，两边都不合这条规矩，现在自动满足。
 *   MD3 的 `AlertDialog` 放里放外都是独立窗口、渲染无差别。
 * ② 因此 MD3 侧的删除确认弹窗由「`state.deleting?.let { … }` 才组合」变成「`AppConfirmDialog(show = …)`」，
 *   文案用 `deleting?.let { … }.orEmpty()` 计算 —— `show = false` 时 MD3 分支不组合 `AlertDialog`，
 *   与合并前等价（`AppConfirmDialog` 从第 2 对起就是这个结构）。
 */

// =====================================================
// 临期提醒阈值
// =====================================================

@Composable
fun ThresholdManageScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state = rememberThresholdManageUiState(viewModel)

    AppScaffold(title = "临期提醒阈值", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AppHintText(
                    "各分类到期前多少天视为“临期”，单个食品可在编辑页覆盖",
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            items(state.categories.size, key = { state.categories[it].id }) { index ->
                val cat = state.categories[index]
                val value = state.getThreshold(cat.id)
                AppCardRow(
                    title = cat.label,
                    verticalPadding = 8.dp,
                    modifier = Modifier.animateItem(),
                    leading = { AppEmojiText(cat.emoji, fontSize = 20.sp) },
                    trailing = {
                        AppStepperPill(
                            valueText = "$value 天",
                            onDecrement = { state.setThreshold(cat.id, value - 1) },
                            onIncrement = { state.setThreshold(cat.id, value + 1) },
                            decrementEnabled = value > 1,
                            incrementEnabled = value < 365,
                            decrementDescription = "减少 ${cat.label} 阈值",
                            incrementDescription = "增加 ${cat.label} 阈值",
                        )
                    },
                )
            }
        }
    }
}

// =====================================================
// 分类管理
// =====================================================

@Composable
fun CategoryManageScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state = rememberCategoryManageUiState(viewModel)
    val editing = state.editing
    val deleting = state.deleting

    AppScaffold(title = "分类管理", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.categories.size, key = { state.categories[it].id }) { index ->
                val cat = state.categories[index]
                val inUse = state.getInUseCount(cat.id)
                AppCardRow(
                    title = cat.label,
                    subtitle = if (inUse > 0) "$inUse 条食品使用中" else null,
                    modifier = Modifier.animateItem(),
                    leading = { AppEmojiText(cat.emoji, fontSize = 20.sp) },
                    trailing = {
                        AppEditRowAction(
                            onClick = { state.setEditing(cat) },
                            contentDescription = "编辑 ${cat.label}",
                        )
                        AppDeleteRowAction(
                            onClick = { state.setDeleting(cat) },
                            contentDescription = "删除 ${cat.label}",
                            // 至少留一个分类：只剩一个时删除按钮禁用（并换成最弱色）
                            enabled = state.categories.size > 1,
                        )
                    },
                )
            }
            item {
                AppAddItemButton(
                    text = "添加分类",
                    onClick = { state.setShowAdd(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }

        AppFormDialog(
            title = "添加分类",
            show = state.showAdd,
            fields = listOf(
                AppFormFieldSpec(label = "分类名称", maxLength = 8, placeholder = "例如：烘焙"),
                AppFormFieldSpec(label = "Emoji 图标", maxLength = 4, placeholder = "例如：🍞"),
            ),
            confirmText = "确定",
            onConfirm = { values ->
                state.addCategory(values[0], values[1])
                state.setShowAdd(false)
            },
            onDismiss = { state.setShowAdd(false) },
        )
        AppFormDialog(
            title = "编辑分类",
            show = editing != null,
            fields = listOf(
                AppFormFieldSpec(
                    label = "分类名称",
                    maxLength = 8,
                    initial = editing?.label.orEmpty(),
                    placeholder = "例如：烘焙",
                ),
                AppFormFieldSpec(
                    label = "Emoji 图标",
                    maxLength = 4,
                    initial = editing?.emoji.orEmpty(),
                    placeholder = "例如：🍞",
                ),
            ),
            confirmText = "确定",
            onConfirm = { values ->
                editing?.let { state.updateCategory(it, values[0], values[1]) }
                state.setEditing(null)
            },
            onDismiss = { state.setEditing(null) },
        )
        AppConfirmDialog(
            show = deleting != null,
            title = "删除分类",
            message = deleting?.let { cat ->
                val inUse = state.getInUseCount(cat.id)
                if (inUse > 0) {
                    "有 $inUse 条食品记录正在使用「${cat.emoji} ${cat.label}」，删除后这些记录将显示为“其他”。确定删除吗？"
                } else {
                    "确定要删除分类「${cat.emoji} ${cat.label}」吗？"
                }
            }.orEmpty(),
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                deleting?.let { state.deleteCategory(it.id) }
                state.setDeleting(null)
            },
            onDismiss = { state.setDeleting(null) },
        )
    }
}

// =====================================================
// 存放位置管理
// =====================================================

@Composable
fun LocationManageScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state = rememberLocationManageUiState(viewModel)

    AppScaffold(title = "存放位置管理", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.locations.size, key = { state.locations[it] }) { index ->
                val loc = state.locations[index]
                val inUse = state.getInUseCount(loc)
                AppCardRow(
                    title = loc,
                    subtitle = if (inUse > 0) "$inUse 条食品使用中" else null,
                    modifier = Modifier.animateItem(),
                    leading = { AppLocationIcon() },
                    trailing = {
                        AppDeleteRowAction(
                            onClick = { state.deleteLocation(loc) },
                            contentDescription = "删除位置 $loc",
                        )
                    },
                )
            }
            item {
                AppAddItemButton(
                    text = "添加位置",
                    onClick = { state.setShowAdd(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }

        AppFormDialog(
            title = "添加存放位置",
            show = state.showAdd,
            fields = listOf(
                AppFormFieldSpec(label = "位置名称", maxLength = 12, placeholder = "例如：车里、办公室"),
            ),
            confirmText = "添加",
            onConfirm = { values ->
                state.addLocation(values[0])
                state.setShowAdd(false)
            },
            onDismiss = { state.setShowAdd(false) },
        )
    }
}
