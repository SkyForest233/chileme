package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 编辑页底栏的保存按钮（#10e 从 `EditFoodScreen` 抽出）。
 *
 * 只搬**渲染**：`onClick` 里那段「校验名称 → 组装 FoodItem → upsert → 返回」留在入口的
 * `onSave`（它要读全部 12 个表单状态与 `existing`，抽出来会变成十几个参数）。
 * 原来用 `return@Button` 提前返回，入口改成 if/else 后行为等价（`onSave` 是独立 lambda，
 * 标签名不再是 Button）。底栏仍在入口的 `Scaffold(bottomBar = …)` 里 ⇒
 * `ImeHandlingTest` 点名的 `imePadding()` 位置没变。
 */
@Composable
internal fun EditFoodSaveBar(
    isEdit: Boolean,
    onSave: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(56.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (isEdit) "保存修改" else "添加到零食柜",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * 生产日期选择弹窗（#10e 从 `EditFoodScreen` 抽出）。
 *
 * 「毫秒 → epochDay」的换算留在弹窗内部，只把结果用 `onConfirmDay` 抛回去 ⇒
 * `Instant` / `ZoneOffset` 两个 import 跟着搬走，入口不再需要。
 * 弹窗内没有输入框，所以不在 `ImeHandlingTest` 第 3 条那份「带输入框的 MD3 弹窗」清单里。
 * `DatePicker` 三件套仍是 Material3 实验 API，而本仓没有全局 opt-in ⇒ 这里自带 `@OptIn`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditFoodDatePickerDialog(
    production: LocalDate,
    onDismiss: () -> Unit,
    onConfirmDay: (Long) -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = production.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    onConfirmDay(
                        Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toEpochDay(),
                    )
                }
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}
