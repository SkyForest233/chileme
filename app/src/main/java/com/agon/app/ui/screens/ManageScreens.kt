package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

/** 通用二级管理页脚手架 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        content = content,
    )
}

// =====================================================
// 临期提醒阈值
// =====================================================

@Composable
fun ThresholdManageScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()

    ManageScaffold(title = "临期提醒阈值", onBack = onBack) { padding ->
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
                    style = MaterialTheme.typography.bodySmall,
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
                            style = MaterialTheme.typography.bodyLarge,
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
                                    style = MaterialTheme.typography.labelLarge,
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
fun CategoryManageScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CategoryDef?>(null) }
    var deleting by remember { mutableStateOf<CategoryDef?>(null) }

    ManageScaffold(title = "分类管理", onBack = onBack) { padding ->
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
                            Text(
                                cat.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            if (inUse > 0) {
                                Text(
                                    "$inUse 条食品使用中",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { editing = cat }) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "编辑 ${cat.label}",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
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
                                tint = if (categories.size > 1) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showAdd = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加分类")
                }
            }
        }
    }

    if (showAdd) {
        CategoryEditDialog(
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
        CategoryEditDialog(
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
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除分类") },
            text = {
                Text(
                    if (inUse > 0)
                        "有 $inUse 条食品记录正在使用「${cat.emoji} ${cat.label}」，删除后这些记录将显示为“其他”。确定删除吗？"
                    else
                        "确定要删除分类「${cat.emoji} ${cat.label}」吗？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(cat.id)
                    deleting = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}

@Composable
internal fun CategoryEditDialog(
    title: String,
    initialLabel: String,
    initialEmoji: String,
    onConfirm: (label: String, emoji: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(initialLabel) }
    var emoji by remember { mutableStateOf(initialEmoji) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(8) },
                    label = { Text("分类名称") },
                    placeholder = { Text("例如：烘焙") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(4) },
                    label = { Text("Emoji 图标") },
                    placeholder = { Text("例如：🍞") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label, emoji) },
                enabled = label.isNotBlank(),
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// =====================================================
// 存放位置管理
// =====================================================

@Composable
fun LocationManageScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    ManageScaffold(title = "存放位置管理", onBack = onBack) { padding ->
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
                            Text(
                                loc,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            if (inUse > 0) {
                                Text(
                                    "$inUse 条食品使用中",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.deleteLocation(loc) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "删除位置 $loc",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showAdd = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加位置")
                }
            }
        }
    }

    if (showAdd) {
        var locName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加存放位置") },
            text = {
                OutlinedTextField(
                    value = locName,
                    onValueChange = { locName = it.take(12) },
                    label = { Text("位置名称") },
                    placeholder = { Text("例如：车里、办公室") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addLocation(locName)
                        showAdd = false
                    },
                    enabled = locName.isNotBlank(),
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消") }
            },
        )
    }
}
