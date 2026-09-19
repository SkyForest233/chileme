/*
 * 顶栏的五个动作按钮（09-19 #11b ② 从 AppChrome.kt 拆出）。
 *
 * 为什么单独一个文件：这五个（编辑 / 删除 / 归档历史 / 全选 / 清空）是**同一族图标按钮**，
 * 每个都是「MD3 用 Material 图标 + 某个语义色，Miuix 用 MiuixIcons 里最接近的那个 + 同一个色」的固定套路，
 * 而 Miuix 没有语义色槽位，色全部取自 `AppColors.kt` 那几个 helper。这一族会随「再加一个顶栏入口」继续长，
 * 和 `AppTopBar` 本身（标题 + 返回/关闭 + 滚动行为）不是同一件事。
 *
 * 方向是单向的：`AppTopBar`（留在 AppChrome.kt）把这里的按钮当 `actions` 槽的内容用，这里不反过来依赖顶栏。
 */
package com.agon.app.ui.components.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton

/** 顶栏「编辑」入口：MD3 `Edit` / Miuix `Edit`，都用默认内容色（详情页在用）。 */
@Composable
fun AppEditAction(onClick: () -> Unit, contentDescription: String = "编辑") {
    AppBarIconButton(onClick, contentDescription, Icons.Rounded.Edit, MiuixIcons.Edit)
}

/** 顶栏「删除这一条」入口：MD3 `Delete` / Miuix `Delete`，error 色（详情页在用）。 */
@Composable
fun AppDeleteAction(onClick: () -> Unit, contentDescription: String = "删除") {
    AppBarIconButton(onClick, contentDescription, Icons.Rounded.Delete, MiuixIcons.Delete, appErrorColor())
}

/** 顶栏「归档历史」入口：MD3 `History` / Miuix `Recent`，primary 色（列表页在用）。 */
@Composable
fun AppArchiveAction(onClick: () -> Unit, contentDescription: String = "归档历史") {
    AppBarIconButton(onClick, contentDescription, Icons.Rounded.History, MiuixIcons.Recent, appPrimaryColor())
}

/**
 * 顶栏「全选 / 取消全选」入口（列表页多选态），primary 色。
 *
 * ⚠️ **两主题的字形逻辑不同，合并前就是这样，照抄不统一**：MD3 恒用 `SelectAll` 字形、只换
 * contentDescription（「全选」/「取消全选」）；Miuix 在已全选时把字形换成 `Close`。
 * 与 [AppEditButton]（MD3 有铅笔图标、Miuix 没有）同一类刻意保留的不对称。
 */
@Composable
fun AppSelectAllAction(allSelected: Boolean, onClick: () -> Unit) {
    val description = if (allSelected) "取消全选" else "全选"
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixIconButton(onClick = onClick) {
            MiuixIcon(
                if (allSelected) MiuixIcons.Close else MiuixIcons.SelectAll,
                contentDescription = description,
                tint = appPrimaryColor(),
            )
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(Icons.Rounded.SelectAll, contentDescription = description, tint = appPrimaryColor())
        }
    }
}

/** 顶栏「清空 / 删除全部」入口：MD3 `DeleteForever` / Miuix `Delete`，error 色（归档页在用）。 */
@Composable
fun AppDestructiveAction(onClick: () -> Unit, contentDescription: String) {
    AppBarIconButton(onClick, contentDescription, Icons.Rounded.DeleteForever, MiuixIcons.Delete, appErrorColor())
}
