package com.agon.app.ui.components

// 数据损坏自救 UI：corruptKeyNames（key → 中文名，横幅与确认弹窗共用）+ DataCorruptBanner。
//
// 与仓库层的 Decoded 三态 / 按 key 粒度写守卫配套（docs/ARCHITECTURE.md §5「数据完整性守卫」）。
// 横幅只在首页出现；改动文案时注意它承诺的行为必须与 FoodRepository 的守卫实现一致，
// 否则就是「UI 说一套、仓库做一套」。CorruptGuardTest 会静态断言这部分。
//
// 2026-09-16 由 Common.kt 拆分而来（纯搬运，签名与实现未改）。

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 损坏 key 的中文名（损坏横幅与「放弃数据」确认弹窗共用）。 */
fun corruptKeyNames(corruptedKeys: Set<String>): String =
    corruptedKeys.joinToString("、") { key ->
        when (key) {
            "food_items" -> "库存"
            "archived_items" -> "归档"
            "consumption_records" -> "消耗记录"
            "history_entries" -> "录入历史"
            else -> key
        }
    }

/**
 * 数据损坏告警条：说明「哪些数据读不出来、影响是什么」，并提供处理入口。
 *
 * 出现条件：仓库层解析某个用户资产 key（库存/归档/消耗/录入历史）失败。
 * 原始串已留档到 `filesDir/corrupt/`，用户可导入备份恢复，或经 [onDiscard] 放弃这部分数据。
 *
 * 写守卫自 2026-09-15 起**按 key 粒度**降级：只有损坏的那个 key 停止写入，其余数据与功能
 * 照常可用（此前是「任一 key 损坏就整体拒绝写入」，辅助数据损坏会锁死核心功能）。
 *
 * 两套主题共用：Miuix 模式下 MaterialTheme 已由 MiuixRootTheme 桥接为 Miuix 配色。
 */
@Composable
fun DataCorruptBanner(
    corruptedKeys: Set<String>,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (corruptedKeys.isEmpty()) return
    val names = remember(corruptedKeys) { corruptKeyNames(corruptedKeys) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("⚠️", fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "$names 数据读取失败",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "这部分数据的写入已暂停，其余数据不受影响（2026-09-15 起按 key 粒度降级）。" +
                        "原始内容已留档到应用私有目录 corrupt/ 下：可导入此前的备份来恢复；" +
                        "确认不再需要时，也可以放弃这部分数据让写入恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(
                    onClick = onDiscard,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        "放弃这部分数据",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
