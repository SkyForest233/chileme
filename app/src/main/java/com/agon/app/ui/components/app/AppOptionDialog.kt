package com.agon.app.ui.components.app

// 选项弹窗（标题 + N 条「图标 + 标题 + 说明」的可点行 + 一个取消）。
// 设置页的「选择导出格式」与「选择恢复来源」用它 —— 合并前两版各写两份，一共四份同构的行结构。
//
// 2026-09-16 第 8 对（设置页）合并时抽出。与 AppConfirmDialog 是同一族（都靠 show 控制、都必须放在
// AppScaffold 的 content lambda 里），差别是正文：那个是一段文字，这个是一列可点选项。

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.ui.components.MiuixDialog
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一条选项：图标 + 标题 + 说明 + 点击动作。
 *
 * 图标要**两个**（MD3 与 Miuix 各一个字形），与 `AppBarIconButton` 同一套做法：设置页两版选的字形本来就不同
 * （导出 JSON：MD3 `FileUpload` / Miuix `UploadCloud`；CSV：MD3 `TableChart` / Miuix `FileDownloads`；
 * 从文件导入：MD3 `FileDownload` / Miuix `FileDownloads`；本地快照：MD3 `Restore` / Miuix `Download`），
 * 合并成一个字形就是改图标 —— 只有真机看得出来。
 *
 * **刻意不做 `data class`**：属性里有 lambda，`equals` 没有意义；这里也不参与任何 `remember` key
 * （与 `AppFormFieldSpec` 的情况相反 —— 那个必须是 `data class`，否则重组时会清空用户已经打进去的字）。
 */
class AppOptionSpec(
    val md3Icon: ImageVector,
    val miuixIcon: ImageVector,
    val title: String,
    val summary: String,
    val onClick: () -> Unit,
)

/**
 * 选项弹窗。
 *
 * 两主题的结构差异与 [AppConfirmDialog] 同源：
 * - MD3：`AlertDialog` 的 `text` 槽里放一列选项行（间距 8dp），取消按钮在 `confirmButton` 槽（右下角）。
 *   选项行用 material3 的**可点 `Surface`**（`onClick` 重载自带涟漪与 button 语义）。
 * - Miuix：`MiuixDialog` 的 content 里放一列选项行（间距 10dp、整列 `padding(top = 8.dp)`），
 *   取消按钮是**整宽** `TextButton`，排在选项下面。选项行的图标不是裸图标，而是 36dp 的
 *   `primaryContainer` 圆盒 + 18dp `onPrimaryContainer` 字形；行本身用 `Surface` + `.clip().clickable`
 *   （Miuix 的 `Surface` 没有 `onClick` 重载）。
 *
 * 两处差异都是照抄合并前的两版，不统一：选项行的**内衬 14dp、圆角 16dp、底色 `surfaceContainerHigh`、
 * 图标与文字间距 12dp、标题 `OptionTitle` 档 + SemiBold、说明 `Hint` 档 + 弱化色**在两版里是一样的，
 * 所以行本身合成一份私有 [OptionRow]，只有图标呈现方式分主题。
 *
 * @param show 是否显示。规则同 [AppConfirmDialog]：**Miuix 分支必须无条件调用 `MiuixDialog` 并靠 show 控制**
 *   （库的硬约束），MD3 分支在内部 `if (show)`；因此本组件必须放在 `AppScaffold` 的 content lambda 里调用。
 * @param cancelText 合并前两版四个弹窗写的都是「取消」，留参数只为将来别的调用点，不替任何一边改文案。
 *
 * IME：本组件不含输入框，MD3 分支不需要 `DialogProperties(decorFitsSystemWindows = false)`
 * （带输入框的那类见 `SettingsScreen` 的坚果云弹窗与 `ImeHandlingTest` 第 3 条清单）。
 */
@Composable
fun AppOptionDialog(
    show: Boolean,
    title: String,
    options: List<AppOptionSpec>,
    onDismissRequest: () -> Unit,
    cancelText: String = "取消",
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixDialog(
            show = show,
            onDismissRequest = onDismissRequest,
            title = title,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                options.forEach { OptionRow(it) }
                MiuixTextButton(
                    text = cancelText,
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else if (show) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { OptionRow(it) }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) { Text(cancelText) }
            },
        )
    }
}

/**
 * 一条选项行。文字与间距两主题一致（见 [AppOptionDialog] 的 KDoc），只有图标的呈现方式分主题：
 * MD3 是裸图标 + `primary` 色，Miuix 是 36dp `primaryContainer` 圆盒里放 18dp `onPrimaryContainer` 字形。
 */
@Composable
private fun OptionRow(spec: AppOptionSpec) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            shape = RoundedCornerShape(16.dp),
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = spec.onClick),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixIcon(
                        spec.miuixIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MiuixTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    AppText(spec.title, AppTextScale.OptionTitle, fontWeight = FontWeight.SemiBold)
                    AppMutedText(spec.summary, AppTextScale.Hint)
                }
            }
        }
    } else {
        Surface(
            onClick = spec.onClick,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(spec.md3Icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    AppText(spec.title, AppTextScale.OptionTitle, fontWeight = FontWeight.SemiBold)
                    AppMutedText(spec.summary, AppTextScale.Hint)
                }
            }
        }
    }
}
