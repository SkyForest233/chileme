package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.agon.app.ui.components.app.AppConfirmDialog
import com.agon.app.ui.components.app.AppPaddedCard
import com.agon.app.ui.components.app.AppScaffold
import com.agon.app.ui.components.app.AppSectionHeader
import com.agon.app.ui.components.app.AppSnackbarPlacement
import com.agon.app.ui.components.app.AppStatCard
import com.agon.app.ui.components.app.AppStatTone
import com.agon.app.ui.components.app.AppText
import com.agon.app.ui.components.app.AppTextScale
import com.agon.app.ui.components.app.AppWideButton
import com.agon.app.ui.components.app.appMutedColor
import com.agon.app.ui.components.app.appOnPrimaryContainerColor
import com.agon.app.ui.components.app.appPrimaryContainerColor
import com.agon.app.ui.components.app.rememberAppSnackbarHostState
import com.agon.app.ui.components.corruptKeyNames
import com.agon.app.ui.components.rememberStatusUi
import com.agon.app.viewmodel.AppViewModel
import com.agon.app.viewmodel.UiEvent
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 首页（Dashboard）：三张统计卡 + 今日提醒 + 一键清理 + 「需要处理」列表。
 *
 * 2026-09-16 由 `HomeScreen`(423) + `MiuixHomeScreen`(401) 合并为单文件双主题（第三批 #3 第 4 对）。
 * 两版**逐段同构**——连 `LazyColumn` 的 contentPadding、`AnimatedVisibility` 的 tween 时长、
 * 「件数口径」的注释都一样——差异全在外壳与叶子上，逐项落位到 `ui/components/app/`：
 *
 * - **顶栏**：一个 `subtitle` 参数喂两边。Miuix 直接吃（上游 v0.9.4-rc01 `TopAppBar.kt:100` 已核对
 *   `subtitle: String = ""`，传空串与不传等价）；MD3 侧「带副标题」即折叠式 `LargeTopAppBar` +
 *   `nestedScroll` + `fillMaxSize`（合并前 MD3 首页就是这么写的，Miuix 首页的 Scaffold 没有 modifier，
 *   所以也不替它加）。Miuix 同样有 `largeTitle` + `scrollBehavior` 可做折叠顶栏，但合并前没用，不替它开。
 * - **撤销条**：`AppSnackbarPlacement.FloatingNav`——两版都抬 84dp 避开悬浮导航栏，与二级页的
 *   `SystemBars`（MD3 侧 `navigationBarsPadding() + 24dp`）不是一回事。
 * - **叶子**：统计卡 [AppStatCard]、分区标题 [AppSectionHeader]、整宽按钮 [AppWideButton]、
 *   提醒条与紧急行的外壳 [AppPaddedCard]、放弃损坏数据弹窗 [AppConfirmDialog]。
 *
 * 弹窗写在 [AppScaffold] 的 content lambda 里，且是「无条件调用 + `show` 控制」——合并前的
 * `MiuixHomeScreen` 写的是 `if (show) { MiuixDialog(show = true) }`，与 `MiuixDialog.kt` KDoc 里的
 * 库约束相反；走 [AppConfirmDialog] 后这条自动满足（MD3 侧弹窗从 Scaffold 外挪到里面，渲染无差别）。
 */
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onOpenList: (String?) -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val state = rememberHomeUiState(viewModel)
    val snackbar = rememberAppSnackbarHostState()
    val scope = rememberCoroutineScope()
    var showDiscardCorruptDialog by remember { mutableStateOf(false) }

    // 启动自动同步完成后提示一次（#4a：改收 Channel）。
    // key 用 snackbar 而不是事件值：切换主题会换一个新的宿主容器，旧协程必须停掉。
    // 这条队列只在首页组合期间排空 —— 用户停在别的 Tab 时事件在队列里等着，
    // 回到首页才弹（与旧的可空 StateFlow 行为一致；详见 viewmodel/UiEvent.kt 的类注释）。
    LaunchedEffect(snackbar) {
        viewModel.homeUiEvents.collect { event ->
            if (event is UiEvent.Notice) {
                snackbar.showMessage(event.message)
            }
        }
    }

    AppScaffold(
        title = "吃了么",
        subtitle = state.today.cnDay(),
        snackbar = snackbar,
        snackbarPlacement = AppSnackbarPlacement.FloatingNav,
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
                item(key = "corrupt-banner") {
                    DataCorruptBanner(
                        corruptedKeys = state.corruptedKeys,
                        onDiscard = { showDiscardCorruptDialog = true },
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppStatCard(
                        emoji = "🧺",
                        value = state.total,
                        label = "食品总数",
                        tone = AppStatTone.Primary,
                        onClick = { onOpenList(null) },
                        modifier = Modifier.weight(1f),
                    )
                    AppStatCard(
                        emoji = "⏳",
                        value = state.expiring,
                        label = "即将过期",
                        tone = AppStatTone.Secondary,
                        onClick = { onOpenList("expiring") },
                        modifier = Modifier.weight(1f),
                    )
                    AppStatCard(
                        emoji = "⚠️",
                        value = state.expired,
                        label = "已过期",
                        tone = AppStatTone.Error,
                        onClick = { onOpenList("expired") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                FreshnessBanner(
                    total = state.total,
                    expiringQuantity = state.expiringQuantity,
                    expiredQuantity = state.expiredQuantity,
                )
            }

            item(key = "clean_expired_btn") {
                AnimatedVisibility(
                    visible = state.expired > 0,
                    enter = expandVertically(tween(300)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(300)) + fadeOut(tween(200)),
                ) {
                    AppWideButton(
                        icon = Icons.Rounded.CleaningServices,
                        label = "一键清理 ${state.expiredQuantity} 件过期食品",
                        onClick = {
                            // 按「件数」而非记录条数：一条记录可能有多件，按钮会把整条记录
                            // 连同它的数量一起归档，显示件数才与操作效果一致（2026-09-15）。
                            val count = state.expiredQuantity
                            state.cleanExpired { cleanedIds ->
                                scope.launch {
                                    if (snackbar.showUndoSnackbar("已将 $count 件过期食品移入归档")) {
                                        state.restoreArchivedBatch(cleanedIds)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                AppSectionHeader(
                    title = "需要处理",
                    linkLabel = "全部食品",
                    onLink = { onOpenList(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
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

        // ---- 损坏数据：放弃确认（二次确认后清空该部分数据，让写入恢复）----
        AppConfirmDialog(
            show = showDiscardCorruptDialog,
            title = "放弃损坏的数据？",
            message = "将清空：${corruptKeyNames(state.corruptedKeys)}。\n\n" +
                "清除后这部分数据不再显示，相关写入恢复正常。原始内容仍留档在应用私有目录 " +
                "corrupt/ 下（普通界面看不到）。若想恢复这部分数据，请改用「导入此前的备份」。",
            confirmText = "放弃数据",
            onConfirm = {
                showDiscardCorruptDialog = false
                state.discardCorruptData()
                scope.launch { snackbar.showMessage("已放弃损坏数据，相关功能恢复正常") }
            },
            onDismiss = { showDiscardCorruptDialog = false },
            destructive = true,
            // 合并前只有首页这个 Miuix 弹窗在摘要与按钮行之间留了 8dp，归档/详情页的两个弹窗没有
            contentTopPadding = 8.dp,
        )
    }
}

/**
 * 「今日提醒」条：圆形图标 + 小标题 + 一句话结论。
 *
 * 文案口径是**件数**（2026-09-15）：`expiringQuantity` / `expiredQuantity` 传进来的是 `quantity`
 * 求和，不是记录条数，与「一键清理」按钮上的数字同源。
 * 外壳走 [AppPaddedCard]（合并前 MD3 = `Surface(shapes.large, surfaceContainer)`、Miuix = 官方 `Card`，
 * 正好就是它的两个分支）；小标题用 [AppTextScale.Label] + 弱化色，结论用 [AppTextScale.Meta]。
 */
@Composable
private fun FreshnessBanner(total: Int, expiringQuantity: Int, expiredQuantity: Int) {
    val message = when {
        total == 0 -> "零食柜空空的，去添加第一件食品吧 ✨"
        expiredQuantity > 0 -> "有 $expiredQuantity 件食品已过期，记得及时清理哦"
        expiringQuantity > 0 -> "有 $expiringQuantity 件食品即将到期，优先享用它们吧"
        else -> "所有食品都很新鲜，安心享用 😋"
    }
    AppPaddedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(appPrimaryContainerColor()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Inventory2,
                    contentDescription = null,
                    tint = appOnPrimaryContainerColor(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                AppText("今日提醒", AppTextScale.Label, color = appMutedColor())
                AppText(message, AppTextScale.Meta, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * 「需要处理」列表的一行：头像 + 名称 + 「剩余天数 · 数量 单位」+ 状态徽章，整行可点开详情。
 *
 * 外壳走 [AppPaddedCard] 的可点形态（合并前 MD3 = `Card(onClick, shapes.large, surfaceContainer,
 * elevation 0)`、Miuix = 官方 `Card(onClick)`）。名称用 [AppTextScale.ItemTitle]（MD3 `titleSmall` /
 * Miuix `body2`，与归档行标题的 Heading 档在 Miuix 侧差一档 —— 原版如此，见 AppTextScale 的说明）；
 * 副标题用 Hint 档但颜色是**状态语义色** `ui.content`，不是 [AppHintText] 的弱化色，故直接用 [AppText]。
 * 名称不强制单行（列表页的行才单行省略），与合并前一致。
 */
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
    AppPaddedCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FoodAvatar(item, emoji, size = 44.dp, background = ui.container)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AppText(item.name, AppTextScale.ItemTitle, fontWeight = FontWeight.SemiBold)
                AppText(
                    "${item.remainingTextAt(today)} · ${item.quantity} ${item.unit}",
                    AppTextScale.Hint,
                    color = ui.content,
                )
            }
            StatusBadge(status)
        }
    }
}
