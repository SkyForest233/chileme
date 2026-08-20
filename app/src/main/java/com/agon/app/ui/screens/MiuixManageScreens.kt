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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.CategoryDef
import com.agon.app.data.DEFAULT_EXPIRING_THRESHOLD
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
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
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()

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
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            items(categories.size, key = { categories[it].id }) { index ->
                val cat = categories[index]
                val value = thresholds[cat.id] ?: DEFAULT_EXPIRING_THRESHOLD
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
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
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = { viewModel.setCategoryThreshold(cat.id, value - 1) },
                                    enabled = value > 1,
                                ) {
                                    Icon(Icons.Rounded.Remove, contentDescription = "减少 ${cat.label} 阈值", modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    "$value 天",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                )
                                IconButton(
                                    onClick = { viewModel.setCategoryThreshold(cat.id, value + 1) },
                                    enabled = value < 365,
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "增加 ${cat.label} 阈值", modifier = Modifier.size(18.dp))
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
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CategoryDef?>(null) }
    var deleting by remember { mutableStateOf<CategoryDef?>(null) }

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
            items(categories.size, key = { categories[it].id }) { index ->
                val cat = categories[index]
                val inUse = items.count { it.category == cat.id }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(cat.emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cat.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            if (inUse > 0) {
                                Text(
                                    "$inUse 条食品使用中",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { editing = cat }) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "编辑 ${cat.label}",
                                modifier = Modifier.size(18.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                        IconButton(
                            onClick = { deleting = cat },
                            enabled = categories.size > 1,
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "删除 ${cat.label}",
                                modifier = Modifier.size(18.dp),
                                tint = if (categories.size > 1) MiuixTheme.colorScheme.error
                                else MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
            item {
                TextButton(
                    text = "添加分类",
                    onClick = { showAdd = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }
    }

    if (showAdd) {
        MiuixCategoryEditDialog(
            title = "添加分类",
            initialLabel = "",
            initialEmoji = "",
            onConfirm = { label, emoji ->
                viewModel.addCategory(label, emoji)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    editing?.let { cat ->
        MiuixCategoryEditDialog(
            title = "编辑分类",
            initialLabel = cat.label,
            initialEmoji = cat.emoji,
            onConfirm = { label, emoji ->
                viewModel.updateCategory(cat.copy(label = label.trim(), emoji = emoji.trim().ifBlank { cat.emoji }))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { cat ->
        val inUse = items.count { it.category == cat.id }
        OverlayDialog(
            title = "删除分类",
            summary = if (inUse > 0)
                "有 $inUse 条食品记录正在使用「${cat.emoji} ${cat.label}」，删除后这些记录将显示为“其他”。确定删除吗？"
            else
                "确定要删除分类「${cat.emoji} ${cat.label}」吗？",
            show = true,
            onDismissRequest = { deleting = null },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { deleting = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "删除",
                    onClick = {
                        viewModel.deleteCategory(cat.id)
                        deleting = null
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
    title: String,
    initialLabel: String,
    initialEmoji: String,
    onConfirm: (label: String, emoji: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val labelState = remember { TextFieldState(initialLabel) }
    val emojiState = remember { TextFieldState(initialEmoji) }
    OverlayDialog(
        title = title,
        show = true,
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
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

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
            items(locations.size, key = { locations[it] }) { index ->
                val loc = locations[index]
                val inUse = items.count { it.location == loc }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Place,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(loc, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            if (inUse > 0) {
                                Text(
                                    "$inUse 条食品使用中",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.deleteLocation(loc) }) {
                            Icon(
                                Icons.Rounded.Delete,
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
                    onClick = { showAdd = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }
    }

    if (showAdd) {
        val locState = remember { TextFieldState("") }
        OverlayDialog(
            title = "添加存放位置",
            show = true,
            onDismissRequest = { showAdd = false },
        ) {
            TextField(
                state = locState,
                label = "位置名称",
                useLabelAsPlaceholder = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { showAdd = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "添加",
                    onClick = {
                        viewModel.addLocation(locState.text.toString().take(12))
                        showAdd = false
                    },
                    modifier = Modifier.weight(1f),
                    enabled = locState.text.toString().isNotBlank(),
                )
            }
        }
    }
}
