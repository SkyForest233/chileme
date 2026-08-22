package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.viewmodel.AppViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 通用二级管理页脚手架（Miuix）。 */
@Composable
private fun MiuixManageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
        content = content,
    )
}

// =====================================================
// 临期提醒阈值
// =====================================================

@Composable
fun MiuixThresholdManageScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state = rememberThresholdManageUiState(viewModel)

    MiuixManageScaffold(title = "临期提醒阈值", onBack = onBack) { padding ->
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
                Text(
                    "各分类到期前多少天视为“临期”，单个食品可在编辑页覆盖",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            items(state.categories.size, key = { state.categories[it].id }) { index ->
                val cat = state.categories[index]
                val value = state.getThreshold(cat.id)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MiuixTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(cat.emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            cat.label,
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MiuixTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = { state.onSetThreshold(cat.id, value - 1) },
                                    enabled = value > 1,
                                ) {
                                    // Miuix Remove 是「移除/退出」形状，减号与列表步进器一样回退 material
                                    Icon(Icons.Rounded.Remove, contentDescription = "减少 ${cat.label} 阈值", modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    "$value 天",
                                    style = MiuixTheme.textStyles.body2,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                )
                                IconButton(
                                    onClick = { state.onSetThreshold(cat.id, value + 1) },
                                    enabled = value < 365,
                                ) {
                                    Icon(MiuixIcons.Add, contentDescription = "增加 ${cat.label} 阈值", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================
// 分类管理
// =====================================================

@Composable
fun MiuixCategoryManageScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state = rememberCategoryManageUiState(viewModel)

    MiuixManageScaffold(title = "分类管理", onBack = onBack) { padding ->
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
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MiuixTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(cat.emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cat.label, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
                            if (inUse > 0) {
                                Text(
                                    "$inUse 条食品使用中",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        IconButton(onClick = { state.onEditingChange(cat) }) {
                            Icon(
                                MiuixIcons.Edit,
                                contentDescription = "编辑 ${cat.label}",
                                modifier = Modifier.size(18.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                        IconButton(
                            onClick = { state.onDeletingChange(cat) },
                            enabled = state.categories.size > 1,
                        ) {
                            Icon(
                                MiuixIcons.Delete,
                                contentDescription = "删除 ${cat.label}",
                                modifier = Modifier.size(18.dp),
                                tint = if (state.categories.size > 1) MiuixTheme.colorScheme.error
                                else MiuixTheme.colorScheme.dividerLine,
                            )
                        }
                    }
                }
            }
            item {
                TextButton(
                    text = "添加分类",
                    onClick = { state.onShowAddChange(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }

        MiuixCategoryEditDialog(
            show = state.showAdd,
            title = "添加分类",
            initialLabel = "",
            initialEmoji = "",
            onConfirm = { label, emoji ->
                state.onAddCategory(label, emoji)
                state.onShowAddChange(false)
            },
            onDismiss = { state.onShowAddChange(false) },
        )
        state.editing?.let { cat ->
            MiuixCategoryEditDialog(
                show = true,
                title = "编辑分类",
                initialLabel = cat.label,
                initialEmoji = cat.emoji,
                onConfirm = { label, emoji ->
                    state.onUpdateCategory(cat, label, emoji)
                    state.onEditingChange(null)
                },
                onDismiss = { state.onEditingChange(null) },
            )
        }
        OverlayDialog(
            title = "删除分类",
            summary = state.deleting?.let { cat ->
                val inUse = state.getInUseCount(cat.id)
                if (inUse > 0)
                    "有 $inUse 条食品记录正在使用「${cat.emoji} ${cat.label}」，删除后这些记录将显示为“其他”。确定删除吗？"
                else
                    "确定要删除分类「${cat.emoji} ${cat.label}」吗？"
            },
            show = state.deleting != null,
            onDismissRequest = { state.onDeletingChange(null) },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { state.onDeletingChange(null) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "删除",
                    onClick = {
                        state.deleting?.let { state.onDeleteCategory(it.id) }
                        state.onDeletingChange(null)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                )
            }
        }
    }
}

@Composable
private fun MiuixCategoryEditDialog(
    show: Boolean,
    title: String,
    initialLabel: String,
    initialEmoji: String,
    onConfirm: (label: String, emoji: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // show/initial 变化时重建输入状态（打开时清空/同步，编辑不同分类时更新）
    val labelState = remember(show, initialLabel) { TextFieldState(initialLabel) }
    val emojiState = remember(show, initialEmoji) { TextFieldState(initialEmoji) }
    OverlayDialog(
        title = title,
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                state = labelState,
                label = "分类名称",
                useLabelAsPlaceholder = true,
            )
            TextField(
                state = emojiState,
                label = "Emoji 图标",
                useLabelAsPlaceholder = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "确定",
                onClick = {
                    onConfirm(
                        labelState.text.toString().take(8),
                        emojiState.text.toString().take(4),
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = labelState.text.toString().isNotBlank(),
            )
        }
    }
}

// =====================================================
// 存放位置管理
// =====================================================

@Composable
fun MiuixLocationManageScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state = rememberLocationManageUiState(viewModel)

    MiuixManageScaffold(title = "存放位置管理", onBack = onBack) { padding ->
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
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MiuixTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            MiuixIcons.Location,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(loc, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
                            if (inUse > 0) {
                                Text(
                                    "$inUse 条食品使用中",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        IconButton(onClick = { state.onDeleteLocation(loc) }) {
                            Icon(
                                MiuixIcons.Delete,
                                contentDescription = "删除位置 $loc",
                                modifier = Modifier.size(18.dp),
                                tint = MiuixTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            item {
                TextButton(
                    text = "添加位置",
                    onClick = { state.onShowAddChange(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }

        val locState = remember(state.showAdd) { TextFieldState("") }
        OverlayDialog(
            title = "添加存放位置",
            show = state.showAdd,
            onDismissRequest = { state.onShowAddChange(false) },
        ) {
            TextField(
                state = locState,
                label = "位置名称",
                useLabelAsPlaceholder = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { state.onShowAddChange(false) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "添加",
                    onClick = {
                        state.onAddLocation(locState.text.toString().take(12))
                        state.onShowAddChange(false)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = locState.text.toString().isNotBlank(),
                )
            }
        }
    }
}
