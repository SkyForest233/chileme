package com.agon.app

// 多选批量操作栏：取消 / 移动位置 / 归档 N 项（进入多选时替换底部导航）。
//
// 跟随「悬浮 / 常驻」两种底栏形态各一套布局，MD3 与 MIUIX 两套按钮按 isMiuix 分流。
// 两条栏都必须同时避让导航栏与键盘（.navigationBarsPadding().imePadding() —— 两段式等价于旧的
// navigationBarsWithImePadding()，取 max 不叠加）；ImeHandlingTest 按**文件清单**点数并求和，
// 搬动这些浮层时必须同步改那份清单。
//
// 2026-09-16 由 MainActivity.kt 拆分而来（纯搬运：除 private→internal 外，签名与实现逐字节未改）。
// BatchActionBar 被 MainApp 调用 → internal；其余三个按钮只在本文件内使用 → 保持 private。

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 多选批量操作栏：取消 + 移动位置 + 归档 N 项（多选时替换底部导航，MD3 / MIUIX 两套按钮，跟随悬浮/非悬浮）。 */
@Composable
internal fun BatchActionBar(
    count: Int,
    isMiuix: Boolean,
    floating: Boolean,
    onCancel: () -> Unit,
    onMoveLocation: () -> Unit,
    onArchive: () -> Unit,
) {
    if (floating) {
        // 悬浮：仅按钮本身悬浮（无外层胶囊背景），「取消」文字 + 「移动位置」胶囊 + 「归档」实心胶囊独立悬浮。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp, top = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BatchCancelButton(isMiuix = isMiuix, onClick = onCancel)
                BatchMoveLocationButton(isMiuix = isMiuix, onClick = onMoveLocation)
                BatchArchiveButton(isMiuix = isMiuix, count = count, onClick = onArchive)
            }
        }
    } else {
        // 非悬浮：全宽常驻操作栏
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BatchCancelButton(isMiuix = isMiuix, onClick = onCancel, modifier = Modifier.weight(1f))
                BatchMoveLocationButton(isMiuix = isMiuix, onClick = onMoveLocation, modifier = Modifier.weight(1.3f))
                BatchArchiveButton(isMiuix = isMiuix, count = count, onClick = onArchive, modifier = Modifier.weight(1.4f))
            }
        }
    }
}

@Composable
private fun BatchCancelButton(
    isMiuix: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isMiuix) {
        // Miuix 标准文字按钮
        MiuixTextButton(
            text = "取消",
            onClick = onClick,
            modifier = modifier,
        )
    } else {
        // MD3 实心胶囊（中性色），与归档实心胶囊视觉统一
        Button(
            onClick = onClick,
            modifier = modifier.defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("取消")
        }
    }
}

@Composable
private fun BatchMoveLocationButton(
    isMiuix: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isMiuix) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            colors = MiuixButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.secondaryContainer,
                contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            MiuixIcon(
                MiuixIcons.Location,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "移动位置",
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSecondaryContainer,
            )
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("移动位置", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BatchArchiveButton(isMiuix: Boolean, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (isMiuix) {
        // Miuix 标准实心按钮（默认 16dp 圆角）
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            colors = MiuixButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.error,
                contentColor = MiuixTheme.colorScheme.onError,
            ),
        ) {
            MiuixIcon(
                Icons.Rounded.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onError,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "归档 $count 项",
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onError,
            )
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("归档 $count 项", fontWeight = FontWeight.SemiBold)
        }
    }
}
