package com.agon.app.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.ui.components.CheckSwitch
import com.agon.app.ui.theme.AppPalette
import com.agon.app.ui.theme.ThemeStyle

/**
 * MD3 版设置节：滚动 `Column` + `Surface` 分组卡片（外观 / 物品管理 / 备份与数据 / 关于）。
 * 合并前 `SettingsScreen` 的 Scaffold 内容，逐字搬运；只有备份节两个按钮的 onClick
 * 换成了主函数里的 [onUpload] / [onCloudRestore]（该节现由 [Md3BackupSection] 承载，参数原样传下去）。
 *
 * **#10a-2（2026-09-18）**：本函数从 `SettingsScreen.kt` 364–747 **逐字搬到本文件**，只把 `private`
 * 放宽成 `internal`（入口在另一个文件里调它）；四节里最大的「备份与数据」（原 546–716，171 行）
 * 抽成了 [Md3BackupSection]（`SettingsBackupMd3.kt`）—— 那是本次**唯一新增的组合边界**，
 * 节内 0 处 `remember`（整个 body 只有根 `Column` 的 `rememberScrollState()`）⇒ 没有状态跨边界搬家。
 */
@Composable
internal fun Md3SettingsBody(
    state: SettingsUiState,
    padding: PaddingValues,
    onUpload: () -> Unit,
    onCloudRestore: () -> Unit,
    onOpenThresholds: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ==================== 外观 ====================
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "外观",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text("深色模式", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("跟随系统", "浅色", "深色").forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = state.darkMode == index,
                            onClick = { state.setDarkMode(index) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("主题风格", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (state.themeStyleName == ThemeStyle.MIUIX.name) "MIUIX：设置页使用小米 HyperOS 组件渲染"
                    else "Material 3：默认风格",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeStyle.entries.forEachIndexed { index, style ->
                        SegmentedButton(
                            selected = state.themeStyleName == style.name,
                            onClick = { state.setThemeStyle(style.name) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeStyle.entries.size,
                            ),
                        ) { Text(style.label) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("主题配色", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (state.dynamicColor) "已开启动态取色，主题跟随壁纸；关闭后生效" else "基于 MD3 种子色生成完整主题",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    items(AppPalette.entries.toList()) { p ->
                        PaletteSwatch(
                            palette = p,
                            selected = state.paletteName == p.name && !state.dynamicColor,
                            enabled = !state.dynamicColor,
                            onClick = { state.setPalette(p.name) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "动态取色 (Material You)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                "跟随壁纸颜色，优先于上方配色方案"
                            else
                                "需要 Android 12 及以上",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CheckSwitch(
                        checked = state.dynamicColor,
                        onCheckedChange = { state.setDynamicColor(it) },
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "悬浮导航",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "关闭后底部导航改为全宽常驻底栏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CheckSwitch(
                        checked = state.floatingNav,
                        onCheckedChange = { state.setFloatingNav(it) },
                    )
                }
            }
        }

        // ==================== 物品管理（统一入口，全部二级页面） ====================
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    "物品管理",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                )
                SettingsNavRow(
                    icon = Icons.Rounded.Schedule,
                    title = "临期提醒阈值",
                    subtitle = "各分类到期前多少天提醒",
                    onClick = onOpenThresholds,
                )
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
                SettingsNavRow(
                    icon = Icons.Rounded.Category,
                    title = "分类管理",
                    subtitle = "共 ${state.categories.size} 个分类",
                    onClick = onOpenCategories,
                )
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
                SettingsNavRow(
                    icon = Icons.Rounded.Place,
                    title = "存放位置管理",
                    subtitle = "共 ${state.locations.size} 个位置预设",
                    onClick = onOpenLocations,
                )
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
                SettingsNavRow(
                    icon = Icons.Rounded.History,
                    title = "归档历史",
                    subtitle = "已归档 ${state.archived.size} 条，可恢复或彻底删除",
                    onClick = onOpenArchive,
                )
            }
        }

        Md3BackupSection(
            state = state,
            onUpload = onUpload,
            onCloudRestore = onCloudRestore,
        )

        // ==================== 关于 ====================
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "关于",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "吃了么 v1.0",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "记录家中零食库存，提醒临期食品，减少食物浪费 🌱",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}
