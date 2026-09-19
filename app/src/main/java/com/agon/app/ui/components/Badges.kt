package com.agon.app.ui.components

// 胶囊小标签：StatusBadge（状态徽章）与 LocationTag（存放位置）。
//
// 两者都是「Miuix / MD3 双实现，靠 LocalThemeStyle 分流」的样板——新增同类小组件请照抄
// 这个结构（见 docs/audits/2026-09-15-code-review.md §2.1 的 App 级组件层建议）。
//
// 2026-09-16 由 Common.kt 拆分而来（纯搬运，签名与实现未改）。

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.FoodStatus
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Location

@Composable
fun StatusBadge(status: FoodStatus, modifier: Modifier = Modifier) {
    val ui = rememberStatusUi(status)
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            modifier = modifier,
            color = ui.container,
            contentColor = ui.content,
            shape = RoundedCornerShape(50),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixIcon(ui.icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                MiuixText(ui.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        Surface(
            modifier = modifier,
            color = ui.container,
            contentColor = ui.content,
            shape = RoundedCornerShape(50),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ui.icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(ui.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun LocationTag(location: String, modifier: Modifier = Modifier) {
    if (location.isBlank()) return
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = MaterialTheme.colorScheme.onSurfaceVariant
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            modifier = modifier,
            shape = RoundedCornerShape(50),
            color = bg,
            contentColor = fg,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixIcon(
                    MiuixIcons.Location,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = fg,
                )
                Spacer(Modifier.width(3.dp))
                MiuixText(location, fontSize = 11.sp, color = fg)
            }
        }
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Place,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    location,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
