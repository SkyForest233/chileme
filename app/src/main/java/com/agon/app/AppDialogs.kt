package com.agon.app

// App 级弹窗。目前只有一个：批量「移动存放位置」（MD3 AlertDialog / Miuix WindowDialog 双实现）。
//
// ⚠️ MD3 分支必须保留 properties = DialogProperties(decorFitsSystemWindows = false) + stickyImePadding()：
// MD3 弹窗是独立浮动窗口，不关这个开关 IME inset 传不进内容，键盘会盖住「确定移动」按钮
// （2026-09-16 修，用户真机复测通过）。Miuix 分支不需要 —— WindowDialog 的 DialogContent 由库自理
// IME，**不要**给它传 defaultWindowInsetsPadding = false。ImeHandlingTest 第 3 条按文件清单守卫这一点，
// 弹窗再搬家时同步改清单。
//
// 参数偏多（8 个）是**忠实搬运**的结果：这块原本是 MainApp 里的内联代码，直接引用了 viewModel /
// selectedIds / isMiuix / scope / 两个 SnackbarHostState。把它收窄成 onConfirm(target) 回调（VM 与
// Snackbar 逻辑回到调用方）留到第三批的 App 级组件层一起做，避免同一轮里既改结构又改行为。
//
// 2026-09-16 由 MainActivity.kt（拆分中途在 MainApp.kt）搬出：除「被捕获的 var showMoveLocationDialog
// 换成 show + onDismiss 两个参数」这 8 行代码（6 处赋值 + show 实参 + if 条件）与 1 行段注释外，
// 弹窗内部实现逐字节未改。

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import com.agon.app.ui.components.MiuixDialog
import com.agon.app.ui.components.app.stickyImePadding
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun BatchMoveLocationDialog(
    viewModel: AppViewModel,
    selectedIds: Set<String>,
    isMiuix: Boolean,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    miuixSnackbarHostState: MiuixSnackbarHostState,
    show: Boolean,
    onDismiss: () -> Unit,
) {
    // ---- 批量修改存放位置：MD3 AlertDialog / Miuix WindowDialog 双实现 ----
    if (show) {
        val locations by viewModel.locations.collectAsStateWithLifecycle()
        var selectedLocation by remember { mutableStateOf(locations.firstOrNull() ?: "零食柜") }
        var customLocation by remember { mutableStateOf("") }

        if (isMiuix) {
            MiuixDialog(
                title = "批量修改存放位置",
                summary = "已选 ${selectedIds.size} 件食品，请选择目标位置：",
                show = show,
                onDismissRequest = { onDismiss() },
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(locations) { loc ->
                            FilterChip(
                                selected = selectedLocation == loc && customLocation.isBlank(),
                                onClick = {
                                    selectedLocation = loc
                                    customLocation = ""
                                },
                                label = { Text(loc, style = MiuixTheme.textStyles.body2) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MiuixTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MiuixTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customLocation,
                        onValueChange = { customLocation = it },
                        label = { Text("或输入新位置（如：书房）") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MiuixTextButton(
                            text = "取消",
                            onClick = { onDismiss() },
                            modifier = Modifier.weight(1f),
                        )
                        // 弹窗动作一律用库的 TextButton（它本身就是填充胶囊，不是无底文字按钮），
                        // 主要动作传 textButtonColorsPrimary()。原先这里是 Button + buttonColorsPrimary()
                        // 再手写 Text(color = onPrimary, fontWeight = SemiBold)：颜色虽然对，但要自己补文字色
                        // 与字重、拿不到 MiuixTheme.textStyles.button 与 disabled 角色，也不是上游
                        // DialogSection.kt 里 7 个弹窗的统一写法。
                        MiuixTextButton(
                            text = "确定移动",
                            onClick = {
                                val target = customLocation.trim().ifBlank { selectedLocation.trim() }
                                val count = selectedIds.size
                                if (target.isNotBlank()) {
                                    viewModel.updateLocationBatch(selectedIds, target)
                                    viewModel.clearSelection()
                                    onDismiss()
                                    scope.launch {
                                        miuixSnackbarHostState.showSnackbar("已将 $count 件食品移动到「$target」")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { onDismiss() },
                // 键盘避让（2026-09-16 补，与 SettingsScreen 坚果云弹窗 / ManageScreens 两处一致）：
                // MD3 弹窗是独立浮动窗口，默认 DialogProperties（decorFitsSystemWindows = true）不会把
                // IME inset 透给内容 —— 下面「或输入新位置」这个输入框弹出键盘时，「确定移动」按钮会被盖住。
                // 关掉 decorFits 拿到 inset，再由粘性避让把弹窗整体上移到键盘之上。
                // 用 stickyImePadding() 而非 imePadding()：2026-09-17 真机复测发现焦点在输入框之间切换时
                // IME inset 会瞬时归零、居中弹窗跟着坠一下（见 ui/components/app/AppIme.kt）。
                // Miuix 分支不需要：WindowDialog 的 DialogContent 由库自理 IME（见 MiuixDialog 的 KDoc）。
                properties = DialogProperties(decorFitsSystemWindows = false),
                modifier = stickyImePadding(),
                title = { Text("批量修改存放位置") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "已选 ${selectedIds.size} 件食品，请选择目标位置：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(locations) { loc ->
                                FilterChip(
                                    selected = selectedLocation == loc && customLocation.isBlank(),
                                    onClick = {
                                        selectedLocation = loc
                                        customLocation = ""
                                    },
                                    label = { Text(loc) },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                )
                            }
                        }
                        OutlinedTextField(
                            value = customLocation,
                            onValueChange = { customLocation = it },
                            label = { Text("或输入新位置（如：书房）") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = customLocation.trim().ifBlank { selectedLocation.trim() }
                            val count = selectedIds.size
                            if (target.isNotBlank()) {
                                viewModel.updateLocationBatch(selectedIds, target)
                                viewModel.clearSelection()
                                onDismiss()
                                scope.launch {
                                    snackbarHostState.showSnackbar("已将 $count 件食品移动到「$target」")
                                }
                            }
                        },
                    ) {
                        Text("确定移动", fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onDismiss() }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}
