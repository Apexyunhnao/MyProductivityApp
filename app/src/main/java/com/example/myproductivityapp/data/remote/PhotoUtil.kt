package com.example.myproductivityapp.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 照片工具：本地图压缩成小尺寸 JPEG 供上传；remoteImages 字段解析（| 分隔 URL）。
 */
object PhotoUtil {

    /** remoteImages 字段解析为 URL 列表。 */
    fun remoteUrlList(remoteImages: String): List<String> =
        remoteImages.split("|").map { it.trim() }.filter { it.isNotBlank() }

    /** remoteImages 字段构建。 */
    fun joinRemoteUrls(urls: List<String>): String = urls.filter { it.isNotBlank() }.joinToString("|")

    /**
     * 压缩本地图片为 JPEG 字节。
     * 手机拍的原图常 2-5MB，这里缩到最长边 maxDim 像素、JPEG quality，输出一般 100-400KB，
     * 适合上传服务器（40G 盘 + 3M 带宽）。
     * 失败（文件不存在/解码失败）返回 null。
     */
    fun compressToJpeg(src: File, maxDim: Int = 1600, quality: Int = 78): ByteArray? {
        try {
            if (!src.exists() || src.length() == 0L) return null
            // 先读尺寸决定采样
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(src.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim && bounds.outHeight / (sample * 2) >= maxDim) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeFile(src.absolutePath, opts) ?: return null

            // 再精确缩到 maxDim 内
            val w = decoded.width
            val h = decoded.height
            val scale = maxOf(w, h).toFloat() / maxDim
            val out = if (scale > 1f) {
                Bitmap.createScaledBitmap(decoded, (w / scale).toInt().coerceAtLeast(1), (h / scale).toInt().coerceAtLeast(1), true)
            } else {
                decoded
            }
            if (out !== decoded) decoded.recycle()

            val baos = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            out.recycle()
            return baos.toByteArray()
        } catch (_: Exception) {
            return null
        }
    }
}
