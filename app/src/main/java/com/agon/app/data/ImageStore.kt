package com.agon.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

private const val MAX_COVER_DIMENSION = 1200
private const val JPEG_QUALITY = 85

/**
 * 复制相册/拍照图片到私有目录 `files/covers/`，并在落盘前进行下采样与 JPEG 压缩
 * （长边限制 1200px，质量 85%，自动校正 EXIF 旋转方向）。
 * 防止相机原图（5~15MB）直接落盘导致私有存储膨胀和列表解码卡顿。
 * 返回保存后的绝对路径，失败返回 null。
 */
suspend fun copyImageToCovers(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val out = File(dir, "${UUID.randomUUID()}.jpg")

        // 1. 读取 EXIF 旋转方向
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        // 2. 解码图片尺寸（不加载像素）
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val rawWidth = boundsOptions.outWidth
        val rawHeight = boundsOptions.outHeight

        // 若尺寸解码失败，降级为直接流拷贝
        if (rawWidth <= 0 || rawHeight <= 0) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            return@withContext out.absolutePath
        }

        // 3. 计算采样率（2 的幂次方）
        var sampleSize = 1
        val maxDim = max(rawWidth, rawHeight)
        while (maxDim / (sampleSize * 2) >= MAX_COVER_DIMENSION) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }

        if (decoded == null) {
            // 解码失败兜底：直接流拷贝
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            return@withContext out.absolutePath
        }

        // 4. 构建变换矩阵（EXIF 旋转 + 剩余精细缩放）
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        val currentMax = max(decoded.width, decoded.height)
        if (currentMax > MAX_COVER_DIMENSION) {
            val scale = MAX_COVER_DIMENSION.toFloat() / currentMax.toFloat()
            matrix.postScale(scale, scale)
        }

        val finalBitmap = if (matrix.isIdentity) {
            decoded
        } else {
            val transformed = Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height, matrix, true
            )
            if (transformed != decoded) decoded.recycle()
            transformed
        }

        // 5. 压缩落盘并回收 Bitmap
        FileOutputStream(out).use { fos ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
        }
        finalBitmap.recycle()

        out.absolutePath
    } catch (e: Exception) {
        null
    }
}

/**
 * 清理孤儿封面图片：删除 covers/ 目录中不被任何库存或归档记录引用的文件。
 * 在应用启动时调用（替换/移除封面后的旧文件会在下次启动时回收）。
 * 返回删除的文件数。
 */
suspend fun cleanupOrphanCovers(context: Context, referencedPaths: Set<String>): Int =
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "covers")
            var deleted = 0
            dir.listFiles()?.forEach { file ->
                if (file.absolutePath !in referencedPaths && file.delete()) deleted++
            }
            deleted
        } catch (e: Exception) {
            0
        }
    }
