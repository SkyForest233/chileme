package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agon.app.data.cn
import com.agon.app.ui.components.CheckSwitch
import com.agon.app.ui.components.app.AppMutedText
import com.agon.app.ui.components.app.AppText
import com.agon.app.ui.components.app.AppTextScale
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import com.agon.app.ui.theme.filterPanelEnter
import com.agon.app.ui.theme.filterPanelExit
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 编辑页「单独设置临期提醒」区块。
 *
 * 无状态：开关与天数字段都以 (value, onValueChange) 传入；不开启时用分类默认阈值。
 * Miuix 使用原生 Switch 与 GlassTextField；Material3 保留项目的 CheckSwitch 与 OutlinedTextField。
 */
@Composable
internal fun EditFoodThresholdSection(
    customThresholdEnabled: Boolean,
    onCustomThresholdEnabledChange: (Boolean) -> Unit,
    customThresholdText: String,
    onCustomThresholdTextChange: (String) -> Unit,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            shape = RoundedCornerShape(24.dp),
            color = MiuixTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            EditFoodThresholdCardBody(
                customThresholdEnabled,
                onCustomThresholdEnabledChange,
                customThresholdText,
                onCustomThresholdTextChange,
            )
        }
    } else {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            EditFoodThresholdCardBody(
                customThresholdEnabled,
                onCustomThresholdEnabledChange,
                customThresholdText,
                onCustomThresholdTextChange,
            )
        }
    }
}

@Composable
private fun EditFoodThresholdCardBody(
    customThresholdEnabled: Boolean,
    onCustomThresholdEnabledChange: (Boolean) -> Unit,
    customThresholdText: String,
    onCustomThresholdTextChange: (String) -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AppText(
                    "单独设置临期提醒",
                    AppTextScale.Body,
                    fontWeight = FontWeight.Medium,
                )
                AppMutedText(
                    "不开启则使用分类默认阈值（可在设置中修改）",
                    AppTextScale.Hint,
                )
            }
            if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
                MiuixSwitch(
                    checked = customThresholdEnabled,
                    onCheckedChange = onCustomThresholdEnabledChange,
                )
            } else {
                CheckSwitch(
                    checked = customThresholdEnabled,
                    onCheckedChange = onCustomThresholdEnabledChange,
                )
            }
        }
        AnimatedVisibility(
            visible = customThresholdEnabled,
            enter = filterPanelEnter(),
            exit = filterPanelExit(),
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                EditFoodTextField(
                    value = customThresholdText,
                    onValueChange = {
                        onCustomThresholdTextChange(it.filter { ch -> ch.isDigit() }.take(3))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "提前多少天算临期",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        }
    }
}

/** 只读过期日期卡。 */
@Composable
internal fun EditFoodExpirySection(
    shelfLife: Int,
    expiry: LocalDate,
) {
    val isMiuix = LocalThemeStyle.current == ThemeStyle.MIUIX
    val cardColor = if (isMiuix) MiuixTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isMiuix) MiuixTheme.colorScheme.onTertiaryContainer
    else MaterialTheme.colorScheme.onSecondaryContainer
    val expiryContent: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp)) {
            AppText(
                "预计过期日期",
                AppTextScale.Label,
                color = contentColor,
            )
            Spacer(Modifier.height(4.dp))
            AppText(
                if (shelfLife > 0) expiry.cn() else "请输入保质期",
                AppTextScale.Hero,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
    if (isMiuix) {
        MiuixSurface(
            shape = RoundedCornerShape(24.dp),
            color = cardColor,
            contentColor = contentColor,
            modifier = Modifier.fillMaxWidth(),
            content = expiryContent,
        )
    } else {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = cardColor,
            modifier = Modifier.fillMaxWidth(),
            content = expiryContent,
        )
    }
}
