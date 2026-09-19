package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.agon.app.data.CategoryDef
import com.agon.app.data.byId
import java.io.File

/**
 * 编辑页「封面」区块（#10e 从 `EditFoodScreen` 抽出，2026-09-19）。
 *
 * 无状态：照片路径与自定义封面字都以 (value, onValueChange) 传进来。12 个 `rememberSaveable`
 * 表单状态一个都没搬 —— 它们的位置就是进程死亡恢复的 key，挪动会改变恢复语义
 * （见 devlog 2026-09-19 §11 里那条 `.value` 冷读路径）。
 *
 * 拍照 / 相册两个 launcher 留在入口（它们要写 `photoPath`、要用 `context` 与 `scope`），
 * 这里只收两个回调。封面字的三级兜底（照片 > 自定义封面 > 分类 emoji）逐字照搬，
 * 所以仍需 `categories` + `category` 两个参数。
 */
@Composable
internal fun EditFoodCoverSection(
    photoPath: String,
    onPhotoPathChange: (String) -> Unit,
    coverText: String,
    onCoverTextChange: (String) -> Unit,
    category: String,
    categories: List<CategoryDef>,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
) {
    Column {
        Text("封面", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (photoPath.isNotBlank() && File(photoPath).exists()) {
                Box {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = "封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(MaterialTheme.shapes.large),
                    )
                    // IconButton 自带 48dp 最小触摸目标（MD3 无障碍要求）
                    IconButton(
                        onClick = { onPhotoPathChange("") },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "移除封面",
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(16.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(88.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            coverText.ifBlank { categories.byId(category).emoji },
                            style = if (coverText.length > 2) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTakePhoto,
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("拍照")
                }
                OutlinedButton(
                    onClick = onPickFromGallery,
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("相册")
                }
            }
            Text(
                "照片 > 自定义封面\n> 分类 emoji",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = coverText,
            onValueChange = { onCoverTextChange(it.take(4)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("自定义封面（可选）") },
            placeholder = { Text("输入 emoji 或短文字，如 🍓 或 牛奶") },
            supportingText = { Text("最多 4 个字符；未设置照片时显示它，都不设则显示分类 emoji") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
    }
}
