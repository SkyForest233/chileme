package com.agon.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.ui.theme.ThemeStyle
import com.agon.app.viewmodel.AppViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 设置页的 Miuix（HyperOS）实现（v2.8）。
 *
 * 与 [SettingsScreen]（Material 3 实现）逻辑对等：读写同一份 AppViewModel 状态
 * （主题风格 / 深浅模式 / 动态取色 / 配色），并提供物品管理入口。
 *
 * 主题风格说明：
 * - MiuixTheme 由根级 `MiuixRootTheme`（MainActivity，MIUIX 模式下）统一提供，本页不再自行包裹。
 * - 其余尚未迁移的页面由根级桥接的 MaterialTheme 渲染（颜色跟随 Miuix），属渐进迁移的预期中间态。
 */
@Composable
fun MiuixSettingsScreen(
    viewModel: AppViewModel,
    onOpenArchive: () -> Unit,
    onOpenThresholds: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenLocations: () -> Unit,
) {
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val themeStyleName by viewModel.themeStyle.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = "设置") },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item(key = "appearance") {
                SmallTitle(text = "外观")
                Card(modifier = Modifier.padding(12.dp)) {
                    RadioButtonPreference(
                        title = ThemeStyle.MATERIAL3.label,
                        summary = "当前 MD3 风格（默认）",
                        selected = themeStyleName == ThemeStyle.MATERIAL3.name,
                        onClick = { viewModel.setThemeStyle(ThemeStyle.MATERIAL3.name) },
                    )
                    RadioButtonPreference(
                        title = ThemeStyle.MIUIX.label,
                        summary = "小米 HyperOS 风格",
                        selected = themeStyleName == ThemeStyle.MIUIX.name,
                        onClick = { viewModel.setThemeStyle(ThemeStyle.MIUIX.name) },
                    )
                    OverlayDropdownPreference(
                        title = "深色模式",
                        items = listOf("跟随系统", "浅色", "深色"),
                        selectedIndex = darkMode.coerceIn(0, 2),
                        onSelectedIndexChange = { viewModel.setDarkMode(it) },
                    )
                    SwitchPreference(
                        title = "动态取色 (Material You)",
                        summary = "需要 Android 12 及以上，优先于配色方案",
                        checked = dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) },
                    )
                }
            }

            item(key = "inventory") {
                SmallTitle(text = "物品管理")
                Card(modifier = Modifier.padding(12.dp)) {
                    ArrowPreference(
                        title = "临期提醒阈值",
                        summary = "各分类到期前多少天提醒",
                        onClick = onOpenThresholds,
                    )
                    ArrowPreference(
                        title = "分类管理",
                        summary = "共 ${categories.size} 个分类",
                        onClick = onOpenCategories,
                    )
                    ArrowPreference(
                        title = "存放位置管理",
                        summary = "共 ${locations.size} 个位置预设",
                        onClick = onOpenLocations,
                    )
                    ArrowPreference(
                        title = "归档历史",
                        summary = "已归档 ${archived.size} 条，可恢复或彻底删除",
                        onClick = onOpenArchive,
                    )
                }
            }

            item(key = "about") {
                SmallTitle(text = "关于")
                Card(modifier = Modifier.padding(12.dp)) {
                    ArrowPreference(
                        title = "吃了么 v1.0",
                        summary = "记录家中零食库存，提醒临期食品，减少食物浪费 🌱",
                    )
                }
            }
        }
    }
}
