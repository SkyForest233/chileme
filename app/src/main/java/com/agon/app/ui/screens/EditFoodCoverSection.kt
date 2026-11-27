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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import coil3.compose.AsyncImage
import com.agon.app.data.CategoryDef
import com.agon.app.data.byId
import com.agon.app.ui.components.app.AppMutedText
import com.agon.app.ui.components.app.AppTextScale
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import java.io.File
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 编辑页「封面」区块（#10e 从 `EditFoodScreen` 抽出，2026-09-19）。
 *
 * 无状态：照片路径与自定义封面字都以 (value, onValueChange) 传进来。12 个 `rememberSaveable`
 * 表单状态一个都没搬 —— 它们的位置就是进程死亡恢复的 key，挪动会改变恢复语义。
 * 拍照 / 相册两个 launcher 留在入口（它们要写 `photoPath`、要用 `context` 与 `scope`），
 * 这里只收两个回调。封面字的三级兜底（照片 > 自定义封面 > 分类 emoji）保持原样。
 *
 * Miuix 下用原生 Surface/IconButton 和 GlassTextField；Material3 渲染分支保留原有外观。
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
    val isMiuix = LocalThemeStyle.current == ThemeStyle.MIUIX
    val previewShape: Shape = if (isMiuix) RoundedCornerShape(24.dp) else MaterialTheme.shapes.large

    Column {
        EditFoodSectionTitle("封面")
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
                            .clip(previewShape),
                    )
                    if (isMiuix) {
                        MiuixIconButton(
                            onClick = { onPhotoPathChange("") },
                            modifier = Modifier.align(Alignment.TopEnd),
                            backgroundColor = MiuixTheme.colorScheme.errorContainer,
                            cornerRadius = 24.dp,
                            minHeight = 48.dp,
                            minWidth = 48.dp,
                        ) {
                            MiuixIcon(
                                MiuixIcons.Close,
                                contentDescription = "移除封面",
                                modifier = Modifier.size(16.dp),
                                tint = MiuixTheme.colorScheme.onErrorContainer,
                            )
                        }
                    } else {
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
                }
            } else if (isMiuix) {
                MiuixSurface(
                    shape = previewShape,
                    color = MiuixTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(88.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        MiuixText(
                            coverText.ifBlank { categories.byId(category).emoji },
                            style = if (coverText.length > 2) MiuixTheme.textStyles.title3
                            else MiuixTheme.textStyles.headline1,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            } else {
                Surface(
                    shape = previewShape,
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
                EditFoodOutlinedActionButton(
                    onClick = onTakePhoto,
                    label = "拍照",
                    icon = Icons.Rounded.PhotoCamera,
                    iconSize = 16.dp,
                )
                EditFoodOutlinedActionButton(
                    onClick = onPickFromGallery,
                    label = "相册",
                    icon = Icons.Rounded.PhotoLibrary,
                    miuixIcon = MiuixIcons.Photos,
                    iconSize = 16.dp,
                )
            }
            AppMutedText("照片 > 自定义封面\n> 分类 emoji", AppTextScale.Hint)
        }
        Spacer(Modifier.height(10.dp))
        EditFoodTextField(
            value = coverText,
            onValueChange = { onCoverTextChange(it.take(4)) },
            modifier = Modifier.fillMaxWidth(),
            label = "自定义封面（可选）",
            placeholder = "输入 emoji 或短文字，如 🍓 或 牛奶",
            supportingText = "最多 4 个字符；未设置照片时显示它，都不设则显示分类 emoji",
            singleLine = true,
        )
    }
}
