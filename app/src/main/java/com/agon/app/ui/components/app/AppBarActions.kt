/*
 * 顶栏的五个动作按钮（09-19 #11b ② 从 AppChrome.kt 拆出）。
 *
 * 为什么单独一个文件：这五个（编辑 / 删除 / 归档历史 / 全选 / 清空）是**同一族图标按钮**，
 * 每个都是「MD3 用 Material 图标 + 某个语义色，Miuix 用 MiuixIcons 里最接近的那个 + 同一个色」的固定套路，
 * 而 Miuix 没有语义色槽位，色全部取自 `AppColors.kt` 那几个 helper。这一族会随「再加一个顶栏入口」继续长，
 * 和 `AppTopBar` 本身（标题 + 返回/关闭 + 滚动行为）不是同一件事。
 *
 * 本文件还带着 `AppBarIconButton`——两主题共用的图标按钮底座，`AppTopBar` 的 `AppBarNavIcon` 也调它。
 * 它原先是 `AppChrome.kt` 的 `private fun`：09-19 #11b 把五个动作搬过来时 CI 直接编译失败，因为
 * Kotlin 的 `private` 顶层成员是**文件**级可见、同包也不行。⇒ 判据「调用点 import 不用改」查不出这种
 * 引用（它不在任何 import 里），补了守卫 `CrossFilePrivateRefTest`。底座随调用方走，故 `internal`。
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** 顶栏「编辑」入口：MD3 `Edit` / Miuix `Edit`，都用默认内容色（详情页在用）。 */
/**
 * 顶栏图标按钮的共用实现：字形由各语义入口传进来（本文件那五个 + `AppChrome.kt` 的 `AppBarNavIcon`），
 * 两主题各挑各的。
 *
 * `internal` 而不是 `private`：同包两个文件都要用它，而 Kotlin 的 `private` 顶层成员是**文件**级可见，
 * 09-19 #11b 就是把它留在 AppChrome.kt 里当 private、只在另一个文件按名字调，CI 直接编译失败。
 *
 * `tint` 为 null 时**不传**这个参数，而不是传 `Color.Unspecified` —— 合并前两版在
 * 「非危险操作」上都是整个参数不写（用各自库的默认内容色），照抄最稳。
 */
@Composable
internal fun AppBarIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    md3Icon: ImageVector,
    miuixIcon: ImageVector,
    tint: Color? = null,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixIconButton(onClick = onClick) {
            if (tint != null) {
                MiuixIcon(miuixIcon, contentDescription = contentDescription, tint = tint)
            } else {
                MiuixIcon(miuixIcon, contentDescription = contentDescription)
            }
        }
    } else {
        IconButton(onClick = onClick) {
            if (tint != null) {
                Icon(md3Icon, contentDescription = contentDescription, tint = tint)
            } else {
                Icon(md3Icon, contentDescription = contentDescription)
            }
        }
    }
}

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
