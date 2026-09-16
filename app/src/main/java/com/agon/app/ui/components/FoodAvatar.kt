package com.agon.app.ui.components

// 食品头像：FoodAvatar（照片 > coverText > 分类 emoji 的优先级回落）与 EmojiAvatar。
//
// 已知待办（docs/ARCHITECTURE.md §5「图片存储」）：photoPath 存绝对路径，换机/清数据后悬空；
// 且存在性判断在组合期同步调 File.exists()，列表滚动每帧一次 syscall，应移到 VM/IO 侧或改由
// Coil 的 onError 回落 emoji。改动时请一并处理，不要只搬不动。
//
// 2026-09-16 由 Common.kt 拆分而来（纯搬运，签名与实现未改）。

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.agon.app.data.FoodItem
import java.io.File

/**
 * 食品头像优先级：照片 > 自定义 emoji/文字封面 > 分类 emoji。
 */
@Composable
fun FoodAvatar(
    item: FoodItem,
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    if (item.photoPath.isNotBlank() && File(item.photoPath).exists()) {
        AsyncImage(
            model = File(item.photoPath),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size / 3)),
        )
    } else {
        EmojiAvatar(item.coverText.ifBlank { emoji }, modifier = modifier, size = size, background = background)
    }
}

@Composable
fun EmojiAvatar(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    // 自定义文字可能是 2-3 个汉字，按长度缩小字号避免溢出
    val fontScale = when {
        emoji.length <= 2 -> 0.48f
        emoji.length == 3 -> 0.3f
        else -> 0.24f
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            emoji,
            fontSize = (size.value * fontScale).sp,
            maxLines = 1,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
