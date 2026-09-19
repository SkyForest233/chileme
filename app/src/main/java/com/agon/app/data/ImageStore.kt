package com.agon.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

private const val TAG = "ImageStore"
private const val MAX_COVER_DIMENSION = 1200
private const val JPEG_QUALITY = 85

/** 半成品封面文件的前缀（见 [copyImageToCovers] 的 tmp→rename）。 */
private const val COVER_TMP_PREFIX = ".tmp-"

/**
 * 把写完的临时文件**改名**成正式封面。同目录内的 `renameTo` 在 Linux 上是原子的
 * —— 正式文件名要么不存在、要么一出现就是完整的。
 * @return 正式路径；改名失败返回 null（半成品由调用侧的 finally 清掉）
 */
private fun promoteCoverTemp(tmp: File, out: File): String? =
    if (tmp.renameTo(out)) out.absolutePath else null

/**
 * 复制相册/拍照图片到私有目录 `files/covers/`，并在落盘前进行下采样与 JPEG 压缩
 * （长边限制 1200px，质量 85%，自动校正 EXIF 旋转方向）。
 * 防止相机原图（5~15MB）直接落盘导致私有存储膨胀和列表解码卡顿。
 * 返回保存后的绝对路径，失败返回 null。
 *
 * **写入是 tmp→rename 两步**（M1-2，2026-09-19）：调用方在 `rememberCoroutineScope` 里跑本函数，
 * 用户选完图立刻返回/旋转进程被回收都可能让协程在中途被取消 —— 直接写正式文件名的话，
 * 留下的是一份**截断的 JPEG**，而它 `exists()` 为真、`FoodItem.photoPath` 又指着它，
 * 于是渲染侧 `File.exists()` 判定通过、Coil 解码失败 ⇒ 一处永久坏图（比丢一张封面更难查）。
 * 现在所有字节都先写进 `covers/.tmp-<uuid>.jpg`，写完才 rename 成正式名；
 * 任何早退/异常/取消都只会留下一个**没有被任何记录引用**的半成品，下次启动 `cleanupOrphanCovers()` 自然收走。
 */
suspend fun copyImageToCovers(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    val name = UUID.randomUUID().toString()
    val dir = File(context.filesDir, "covers").apply { mkdirs() }
    val tmp = File(dir, "$COVER_TMP_PREFIX$name.jpg")
    val out = File(dir, "$name.jpg")
    try {

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

        // 若尺寸解码失败，降级为直接流拷贝（和主路径一样只写临时名，rename 之后才算存在）
        if (rawWidth <= 0 || rawHeight <= 0) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            return@withContext promoteCoverTemp(tmp, out)
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
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            return@withContext promoteCoverTemp(tmp, out)
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

        // 5. 压缩落盘并回收 Bitmap。compress 返回 false 意味着"文件已写完、内容却不是合法 JPEG"，
        // 光看 exists() 看不出来 ⇒ 必须按失败处理（M1-2：以前这条路径会把半成品一路返回给调用方）。
        val compressed = FileOutputStream(tmp).use { fos ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
        }
        finalBitmap.recycle()
        if (!compressed) {
            Log.w(TAG, "封面 JPEG 编码失败（compress 返回 false）：$uri")
            null
        } else {
            promoteCoverTemp(tmp, out)
        }
    } catch (e: Exception) {
        // 静默吞掉会让「选完封面却没反应」无从排查（2026-09-15，detekt SwallowedException 指出）。
        // 语义不变（失败返回 null，调用方据此提示），只把原因落到日志。
        Log.w(TAG, "封面图片处理失败：$uri", e)
        null
    } finally {
        // 早退 / 异常 / 协程被取消都会走到这里清掉半成品；成功路径已经 rename 走 ⇒ 这里是空操作。
        // 留着它才让"任何退出方式都不会在 covers/ 留下没写完的文件"这句话成立
        // （残留的 tmp 不带 .jpg 正式名、不被任何记录引用 ⇒ 下次启动由 cleanupOrphanCovers() 收走）。
        runCatching {
            if (tmp.exists() && !tmp.delete()) Log.w(TAG, "封面临时文件删除失败：${tmp.name}")
        }
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
            // 同理：清理失败返回 0（下次启动会再试），但要留下日志
            Log.w(TAG, "清理孤儿封面失败", e)
            0
        }
    }
