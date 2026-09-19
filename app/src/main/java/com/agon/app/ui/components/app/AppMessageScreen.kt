/*
 * 整屏消息页（09-19 #11b ③ 从 AppChrome.kt 拆出）。
 *
 * 为什么单独一个文件：`AppMessageScreen` 是「一个页面」而不是「页面的一块 chrome」——它自带标题栏、正文、
 * 按钮和安全区，被当作导航目标用。09-17 那轮为了消掉「空态页每屏抄一遍」把它暂住在 chrome 文件里，
 * 边界本来就该在这里。
 *
 * ⚠️ 它**没有**和 `ui/components/EmptyState.kt` 合并：前者是「带一个动作的整屏状态」，
 * 后者是「嵌在列表/网格末尾的引导块」，签名与语义都不同（判定过程见 docs/ROADMAP.md #11「明确不做」段）。
 */
package com.agon.app.ui.components.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/**
 * 「这一页没东西可显示」的兜底屏：**无顶栏**的空 Scaffold + 居中一句话 + 一个返回按钮。
 * 详情页在食品已归档/移除时用（合并前两版各写一份）。
 *
 * 文案走 [AppText] 的 Emphasis 档位（两版原来就是 MD3 `titleMedium` / Miuix `body1`）；
 * 按钮两版形态不同 —— MD3 是圆角 50 胶囊，Miuix 是库默认按钮 + 显式 `onSecondaryVariant` 文字色，
 * 照原样保留，所以这一处仍是两段分支。
 */
@Composable
fun AppMessageScreen(message: String, actionLabel: String, onAction: () -> Unit) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixScaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppText(message, AppTextScale.Emphasis)
                Spacer(Modifier.height(12.dp))
                MiuixButton(onClick = onAction) {
                    MiuixText(actionLabel, color = MiuixTheme.colorScheme.onSecondaryVariant)
                }
            }
        }
    } else {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppText(message, AppTextScale.Emphasis)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAction, shape = RoundedCornerShape(50)) { Text(actionLabel) }
            }
        }
    }
}
