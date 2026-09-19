package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agon.app.data.cn
import com.agon.app.ui.components.CheckSwitch
import com.agon.app.ui.theme.filterPanelEnter
import com.agon.app.ui.theme.filterPanelExit
import java.time.LocalDate

/**
 * 编辑页「单独设置临期提醒」区块（#10e 从 `EditFoodScreen` 抽出）。
 *
 * 无状态：开关与天数字段都以 (value, onValueChange) 传入；不开启时用分类默认阈值
 * （那句说明文字照搬）。展开动画与输入过滤（只留数字、最多 3 位）逐字照搬。
 */
@Composable
internal fun EditFoodThresholdSection(
    customThresholdEnabled: Boolean,
    onCustomThresholdEnabledChange: (Boolean) -> Unit,
    customThresholdText: String,
    onCustomThresholdTextChange: (String) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "单独设置临期提醒",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "不开启则使用分类默认阈值（可在设置中修改）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CheckSwitch(
                    checked = customThresholdEnabled,
                    onCheckedChange = onCustomThresholdEnabledChange,
                )
            }
            AnimatedVisibility(
                visible = customThresholdEnabled,
                enter = filterPanelEnter(),
                exit = filterPanelExit(),
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customThresholdText,
                        onValueChange = {
                            onCustomThresholdTextChange(it.filter { ch -> ch.isDigit() }.take(3))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("提前多少天算临期") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }
        }
    }
}

/**
 * 编辑页「预计过期日期」区块（#10e 从 `EditFoodScreen` 抽出）。
 *
 * 只读：保质期与生产日期都在入口算好（`production` / `shelfLife` / `expiry`），
 * 这里只负责显示，所以「请输入保质期」那句兜底判断也跟着搬了过来。
 */
@Composable
internal fun EditFoodExpirySection(
    shelfLife: Int,
    expiry: LocalDate,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "预计过期日期",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (shelfLife > 0) expiry.cn() else "请输入保质期",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
