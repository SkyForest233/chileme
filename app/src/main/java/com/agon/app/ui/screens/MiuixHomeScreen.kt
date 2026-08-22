package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.agon.app.data.FoodStatus
import com.agon.app.data.byId
import com.agon.app.data.cnDay
import com.agon.app.data.remainingTextAt
import com.agon.app.data.statusForAt
import com.agon.app.ui.components.DataCorruptBanner
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.FoodAvatar
import com.agon.app.ui.components.StatusBadge
import com.agon.app.ui.components.rememberStatusUi
import com.agon.app.ui.components.showUndoSnackbar
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
import top.yukonga.miuix.kmp.basic.SnackbarResult as MiuixSnackbarResult
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
                item(key = "corrupt-banner") { DataCorruptBanner(state.corruptedKeys) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        emoji = "🧺",
                        value = state.total,
                        label = "食品总数",
                        container = MiuixTheme.colorScheme.primaryContainer,
                        content = MiuixTheme.colorScheme.onPrimaryContainer,
                        onClick = { onOpenList(null) },
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        emoji = "⏳",
                        value = state.expiring,
                        label = "即将过期",
                        container = MiuixTheme.colorScheme.secondaryContainer,
                        content = MiuixTheme.colorScheme.onSecondaryContainer,
                        onClick = { onOpenList("expiring") },
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        emoji = "⚠️",
                        value = state.expired,
                        label = "已过期",
                        container = MiuixTheme.colorScheme.errorContainer,
                        content = MiuixTheme.colorScheme.onErrorContainer,
                        onClick = { onOpenList("expired") },
                    )
                }
            }

            item {
                FreshnessBanner(total = state.total, expiring = state.expiring, expired = state.expired)
            }

            item(key = "clean_expired_btn") {
                AnimatedVisibility(
                    visible = state.expired > 0,
                    enter = expandVertically(tween(300)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(300)) + fadeOut(tween(200)),
                ) {
                    Button(
                        onClick = {
                            val count = state.expired
                            state.cleanExpired { cleanedIds ->
                                scope.launch {
                                    val result = snackbarHostState.showUndoSnackbar("已将 $count 件过期食品移入归档")
                                    if (result == MiuixSnackbarResult.ActionPerformed) {
                                        state.restoreArchivedBatch(cleanedIds)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Icon(
                            Icons.Rounded.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MiuixTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("一键清理 ${state.expired} 件过期食品", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "需要处理",
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onOpenList(null) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "全部食品",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            MiuixIcons.Forward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (state.urgent.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "🎉",
                        title = "一切新鲜！",
                        subtitle = "没有临期或过期的食品，继续保持吧",
                    )
                }
            } else {
                items(state.urgent, key = { it.id }) { item ->
                    UrgentRow(
                        item = item,
                        emoji = state.categories.byId(item.category).emoji,
                        status = item.statusForAt(state.today, state.thresholds),
                        today = state.today,
                        onClick = { onOpenItem(item.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    emoji: String,
    value: Int,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = container, contentColor = content),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(8.dp))
            Text("$value", style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MiuixTheme.textStyles.footnote2)
        }
    }
}

@Composable
private fun FreshnessBanner(total: Int, expiring: Int, expired: Int) {
    val message = when {
        total == 0 -> "零食柜空空的，去添加第一件食品吧 ✨"
        expired > 0 -> "有 $expired 件食品已过期，记得及时清理哦"
        expiring > 0 -> "有 $expiring 件食品即将到期，优先享用它们吧"
        else -> "所有食品都很新鲜，安心享用 😋"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Inventory2,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "今日提醒",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    message,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun UrgentRow(
    item: FoodItem,
    emoji: String,
    status: FoodStatus,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = rememberStatusUi(status)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FoodAvatar(item, emoji, size = 44.dp, background = ui.container)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${item.remainingTextAt(today)} · ${item.quantity} ${item.unit}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = ui.content,
                )
            }
            StatusBadge(status)
        }
    }
}
