package com.agon.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix 版设置节：`LazyColumn` + 库的 Preference 组件（外观 / 物品管理 / 备份与数据 / 关于）。
 * 合并前 `MiuixSettingsScreen` 的 Scaffold 内容，逐字搬运（`innerPadding` 改名 `padding`，
 * 备份节两行的 onClick 换成 [onUpload] / [onCloudRestore]）；裸 `Text`/`Surface`/`TextButton`
 * 一律换成带 `Miuix` 别名的库控件，以免与 material3 同名符号混淆。
 *
 * **#10a-2（2026-09-18）**：本函数从 `SettingsScreen.kt` 749–938 **逐字搬到本文件**，只把 `private`
 * 放宽成 `internal`；体内 **0 处 `remember`**，组合边界与调用关系一字未变。
 */
@Composable
internal fun MiuixSettingsBody(
    state: SettingsUiState,
    padding: PaddingValues,
    onUpload: () -> Unit,
    onCloudRestore: () -> Unit,
    onOpenThresholds: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        item(key = "appearance") {
            SmallTitle(text = "外观")
            Card(modifier = Modifier.padding(12.dp)) {
                RadioButtonPreference(
                    title = ThemeStyle.MATERIAL3.label,
                    summary = "当前 MD3 风格（默认）",
                    selected = state.themeStyleName == ThemeStyle.MATERIAL3.name,
                    onClick = { state.setThemeStyle(ThemeStyle.MATERIAL3.name) },
                )
                RadioButtonPreference(
                    title = ThemeStyle.MIUIX.label,
                    summary = "小米 HyperOS 风格",
                    selected = state.themeStyleName == ThemeStyle.MIUIX.name,
                    onClick = { state.setThemeStyle(ThemeStyle.MIUIX.name) },
                )
                OverlayDropdownPreference(
                    title = "深色模式",
                    items = listOf("跟随系统", "浅色", "深色"),
                    selectedIndex = state.darkMode.coerceIn(0, 2),
                    onSelectedIndexChange = { state.setDarkMode(it) },
                )
                SwitchPreference(
                    title = "动态取色 (Material You)",
                    summary = "需要 Android 12 及以上，优先于配色方案",
                    checked = state.dynamicColor,
                    onCheckedChange = { state.setDynamicColor(it) },
                )
                SwitchPreference(
                    title = "悬浮导航",
                    summary = "关闭后底部导航改为全宽常驻底栏",
                    checked = state.floatingNav,
                    onCheckedChange = { state.setFloatingNav(it) },
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
                    summary = "共 ${state.categories.size} 个分类",
                    onClick = onOpenCategories,
                )
                ArrowPreference(
                    title = "存放位置管理",
                    summary = "共 ${state.locations.size} 个位置预设",
                    onClick = onOpenLocations,
                )
                ArrowPreference(
                    title = "归档历史",
                    summary = "已归档 ${state.archived.size} 条，可恢复或彻底删除",
                    onClick = onOpenArchive,
                )
            }
        }

        item(key = "backup") {
            SmallTitle(text = "备份与数据")
            Card(modifier = Modifier.padding(12.dp)) {
                ArrowPreference(
                    title = "导出数据",
                    summary = "支持导出为 JSON 完整备份或 Excel CSV 表格",
                    startAction = {
                        MiuixIcon(
                            MiuixIcons.UploadCloud,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    onClick = { state.setShowExportFormatDialog(true) },
                )
                ArrowPreference(
                    title = "恢复数据",
                    summary = "支持从 JSON 备份文件或本地历史快照恢复",
                    startAction = {
                        MiuixIcon(
                            MiuixIcons.FileDownloads,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    onClick = { state.setShowRestoreSourceDialog(true) },
                )
                ArrowPreference(
                    title = "坚果云同步",
                    summary = when {
                        state.nutstoreAccount.isBlank() -> "未配置，点击设置 WebDAV 账号"
                        state.credentialBroken -> "应用密码已失效，请重新填写"
                        state.plaintextFallback -> "⚠️ 系统 Keystore 不可用，密码以未加密形式保存"
                        state.lastSync.isNotBlank() -> state.lastSync
                        else -> "已配置，尚未同步"
                    },
                    startAction = {
                        MiuixIcon(
                            MiuixIcons.CloudFill,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    onClick = { state.setShowNutstoreDialog(true) },
                )
                if (state.nutstoreAccount.isNotBlank()) {
                    ArrowPreference(
                        title = "上传到云端",
                        summary = if (state.syncing) "正在上传…" else "立即手动上传当前数据",
                        startAction = {
                            MiuixIcon(
                                MiuixIcons.UploadCloud,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        enabled = !state.syncing,
                        onClick = onUpload,
                    )
                    ArrowPreference(
                        title = "从云端恢复",
                        summary = "选择备份版本（云端保留最近 $CLOUD_BACKUP_KEEP 次）",
                        startAction = {
                            MiuixIcon(
                                MiuixIcons.Download,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        enabled = !state.syncing && !state.loadingBackups,
                        onClick = onCloudRestore,
                    )
                }
                OverlayDropdownPreference(
                    title = "自动同步",
                    items = listOf("关闭", "每天", "3 天", "每周"),
                    selectedIndex = when (state.autoSyncDays) {
                        1 -> 1
                        3 -> 2
                        7 -> 3
                        else -> 0
                    },
                    onSelectedIndexChange = { idx ->
                        state.setAutoSyncDays(listOf(0, 1, 3, 7)[idx])
                    },
                )
                ArrowPreference(
                    title = "清空库存记录",
                    summary = "当前共 ${state.items.size} 条食品记录（不影响归档）",
                    titleColor = BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.error),
                    onClick = { state.setShowClearDialog(true) },
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
