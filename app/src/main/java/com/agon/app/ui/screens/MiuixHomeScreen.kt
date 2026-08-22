package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.FoodItem
import com.agon.app.data.byId
import com.agon.app.data.cnDay
import com.agon.app.data.remainingTextAt
import com.agon.app.data.statusForAt
import com.agon.app.ui.components.DataCorruptBanner
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.FoodAvatar
import com.agon.app.ui.components.StatusBadge
import com.agon.app.ui.components.rememberStatusUi
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首页（Dashboard）的 Miuix（HyperOS）实现（v2.8 阶段二）。
 *
 * 与 [HomeScreen]（Material 3 实现）逻辑对等。结构性组件（Scaffold/TopAppBar/Card/Button/
 * Snackbar）使用 Miuix 组件；复用组件（EmptyState/FoodAvatar/StatusBadge/rememberStatusUi）
 * 来自 Common.kt，由根级桥接的 MaterialTheme 提供 Miuix 配色。
 */
@Composable
fun MiuixHomeScreen(
    viewModel: AppViewModel,
    onOpenList: (String?) -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val state = rememberHomeUiState(viewModel)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 启动自动同步完成后提示一次
    LaunchedEffect(state.autoSyncMessage) {
        state.autoSyncMessage?.let {
            snackbarHostState.showSnackbar(it)
            state.consumeAutoSyncMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = "吃了么", subtitle = state.today.cnDay()) },
        snackbarHost = {
            // 上移避免被悬浮导航栏遮挡
            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 84.dp))
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 数据损坏告警：置顶且不可忽略，此时写入已被仓库层拒绝
            if (state.corruptedKeys.isNotEmpty()) {
                item {
                    DataCorruptBanner(corruptedKeys = state.corruptedKeys)
                }
            }

            // ---- Overview stats 3 cards ----
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiuixStatCard(
                        title = "总库存",
                        count = state.total,
                        emoji = "📦",
                        containerColor = MiuixTheme.colorScheme.primaryContainer,
                        contentColor = MiuixTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenList(null) },
                    )
                    MiuixStatCard(
                        title = "临期",
                        count = state.expiring,
                        emoji = "⏳",
                        containerColor = MiuixTheme.colorScheme.tertiaryContainer,
                        contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenList("EXPIRING") },
                    )
                    MiuixStatCard(
                        title = "已过期",
                        count = state.expired,
                        emoji = "⚠️",
                        containerColor = MiuixTheme.colorScheme.errorContainer,
                        contentColor = MiuixTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenList("EXPIRED") },
                    )
                }
            }

            // ---- Clean expired shortcut button ----
            if (state.expired > 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            color = MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "有 ${state.expired} 件食品已过期",
                                    style = MiuixTheme.textStyles.title4,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    "一键清理将移入归档历史，可随时恢复",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                )
                            }
                            Button(
                                onClick = {
                                    state.cleanExpired()
                                    scope.launch { snackbarHostState.showSnackbar("已将 ${state.expired} 件过期食品移入归档") }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    color = MiuixTheme.colorScheme.error,
                                    contentColor = MiuixTheme.colorScheme.onError,
                                ),
                            ) {
                                Icon(
                                    Icons.Rounded.CleaningServices,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MiuixTheme.colorScheme.onError,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("一键清理", color = MiuixTheme.colorScheme.onError)
                            }
                        }
                    }
                }
            }

            // ---- Urgent section title ----
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "需要关注（${state.urgent.size}）",
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.urgent.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clickable { onOpenList("URGENT") }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "查看全部",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.primary,
                            )
                            Icon(
                                MiuixIcons.Forward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (state.urgent.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "✨",
                        title = "太棒了，没有临期食品！",
                        subtitle = "零食柜里的食物都很新鲜，继续保持",
                        actionLabel = "去看看全部库存",
                        onAction = { onOpenList(null) },
                    )
                }
            } else {
                items(state.urgent.take(6), key = { it.id }) { item ->
                    MiuixHomeUrgentCard(
                        item = item,
                        emoji = state.categories.byId(item.category).emoji,
                        status = item.statusForAt(state.today, state.thresholds),
                        today = state.today,
                        onClick = { onOpenItem(item.id) },
                    )
                }
            }

            // ---- View all banner card ----
            if (state.total > 0 && state.urgent.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenList(null) },
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Inventory2,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "查看全部 ${state.total} 件食品",
                                    style = MiuixTheme.textStyles.title4,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "支持按分类、位置、保质期筛选与排序",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Icon(
                                MiuixIcons.Forward,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixStatCard(
    title: String,
    count: Int,
    emoji: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            color = containerColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MiuixTheme.textStyles.footnote2, color = contentColor)
                Text(emoji, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                count.toString(),
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun MiuixHomeUrgentCard(
    item: FoodItem,
    emoji: String,
    status: com.agon.app.data.FoodStatus,
    today: java.time.LocalDate,
    onClick: () -> Unit,
) {
    val ui = rememberStatusUi(status)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FoodAvatar(item, emoji, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${item.remainingTextAt(today)} · ${item.quantity} ${item.unit}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = ui.content,
                    fontWeight = FontWeight.Medium,
                )
            }
            StatusBadge(status)
        }
    }
}
