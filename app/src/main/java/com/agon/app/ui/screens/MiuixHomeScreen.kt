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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.FoodItem
import com.agon.app.data.FoodStatus
import com.agon.app.data.byId
import com.agon.app.data.cnDay
import com.agon.app.data.daysLeft
import com.agon.app.data.remainingText
import com.agon.app.data.statusFor
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
    val items by viewModel.items.collectAsStateWithLifecycle()
    val corruptedKeys by viewModel.corruptedKeys.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 启动自动同步完成后提示一次
    val autoSyncMessage by viewModel.autoSyncMessage.collectAsStateWithLifecycle()
    LaunchedEffect(autoSyncMessage) {
        autoSyncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeAutoSyncMessage()
        }
    }

    val total = items.size
    val expiring = items.count { it.statusFor(thresholds) == FoodStatus.EXPIRING }
    val expired = items.count { it.statusFor(thresholds) == FoodStatus.EXPIRED }
    val urgent = remember(items, thresholds) {
        items.filter { it.statusFor(thresholds) != FoodStatus.SAFE }.sortedBy { it.daysLeft }
    }

    Scaffold(
        topBar = { TopAppBar(title = "吃了么", subtitle = LocalDate.now().cnDay()) },
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
            if (corruptedKeys.isNotEmpty()) {
                item(key = "corrupt-banner") { DataCorruptBanner(corruptedKeys) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        emoji = "🧺",
                        value = total,
                        label = "食品总数",
                        container = MiuixTheme.colorScheme.primaryContainer,
                        content = MiuixTheme.colorScheme.onPrimaryContainer,
                        onClick = { onOpenList(null) },
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        emoji = "⏳",
                        value = expiring,
                        label = "即将过期",
                        container = MiuixTheme.colorScheme.secondaryContainer,
                        content = MiuixTheme.colorScheme.onSecondaryContainer,
                        onClick = { onOpenList("expiring") },
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        emoji = "⚠️",
                        value = expired,
                        label = "已过期",
                        container = MiuixTheme.colorScheme.errorContainer,
                        content = MiuixTheme.colorScheme.onErrorContainer,
                        onClick = { onOpenList("expired") },
                    )
                }
            }

            item {
                FreshnessBanner(total = total, expiring = expiring, expired = expired)
            }

            if (expired > 0) {
                item {
                    Button(
                        onClick = {
                            val count = expired
                            viewModel.cleanExpired()
                            scope.launch {
                                snackbarHostState.showSnackbar("已将 $count 件过期食品移入归档")
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
                        Text("一键清理 $expired 件过期食品", fontWeight = FontWeight.SemiBold)
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
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
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

            if (urgent.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "🎉",
                        title = "一切新鲜！",
                        subtitle = "没有临期或过期的食品，继续保持吧",
                    )
                }
            } else {
                items(urgent, key = { it.id }) { item ->
                    UrgentRow(
                        item = item,
                        emoji = categories.byId(item.category).emoji,
                        status = item.statusFor(thresholds),
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
private fun UrgentRow(item: FoodItem, emoji: String, status: FoodStatus, onClick: () -> Unit) {
    val ui = rememberStatusUi(status)
    Card(onClick = onClick) {
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
                    "${item.remainingText} · ${item.quantity} ${item.unit}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = ui.content,
                )
            }
            StatusBadge(status)
        }
    }
}
