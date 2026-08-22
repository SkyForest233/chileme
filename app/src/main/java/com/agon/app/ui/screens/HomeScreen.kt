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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onOpenList: (String?) -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val state = rememberHomeUiState(viewModel)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
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
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            // 上移避免被悬浮导航栏遮挡
            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 84.dp))
        },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("吃了么", fontWeight = FontWeight.Bold)
                        Text(
                            state.today.cnDay(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
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
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { onOpenList(null) },
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        emoji = "⏳",
                        value = state.expiring,
                        label = "即将过期",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { onOpenList("expiring") },
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        emoji = "⚠️",
                        value = state.expired,
                        label = "已过期",
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = { onOpenList("expired") },
                    )
                }
            }

            item {
                FreshnessBanner(total = state.total, expiring = state.expiring, expired = state.expired)
            }

            if (state.expired > 0) {
                item {
                    FilledTonalButton(
                        onClick = {
                            val count = state.expired
                            state.cleanExpired()
                            scope.launch {
                                snackbarHostState.showSnackbar("已将 $count 件过期食品移入归档")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
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
                        style = MaterialTheme.typography.titleMedium,
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
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "$value",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(label, style = MaterialTheme.typography.labelMedium)
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
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "今日提醒",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun UrgentRow(item: FoodItem, emoji: String, status: FoodStatus, today: LocalDate, onClick: () -> Unit) {
    val ui = rememberStatusUi(status)
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${item.remainingTextAt(today)} · ${item.quantity} ${item.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ui.content,
                )
            }
            StatusBadge(status)
        }
    }
}
