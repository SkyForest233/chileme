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
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 编辑页底栏保存按钮。
 *
 * 保存校验与等 DataStore 写完后返回的逻辑留在入口；Material3 保留原按钮，Miuix 使用原生 Button。
 * `enabled` 在保存期间挡住二次点击，避免新增模式下连点生成重复记录。
 */
@Composable
internal fun EditFoodSaveBar(
    isEdit: Boolean,
    onSave: () -> Unit,
    enabled: Boolean = true,
) {
    val label = if (isEdit) "保存修改" else "添加到零食柜"
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(color = MiuixTheme.colorScheme.surface) {
            MiuixButton(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(56.dp),
                cornerRadius = 50.dp,
                colors = MiuixButtonDefaults.buttonColorsPrimary(),
            ) {
                MiuixIcon(MiuixIcons.Ok, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                MiuixText(
                    label,
                    style = MiuixTheme.textStyles.button,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    } else {
        Surface(color = MaterialTheme.colorScheme.background) {
            Button(
                onClick = onSave,
                enabled = enabled,
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
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Production date picker. Miuix 0.9.5-rc01 has no corresponding date picker, so this isolated dialog
 * remains Material3 (using the root Miuix theme bridge when opened from the Miuix editor).
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
