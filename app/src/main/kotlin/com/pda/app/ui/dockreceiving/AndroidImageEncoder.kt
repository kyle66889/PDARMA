package com.pda.app.ui.dockreceiving

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidImageEncoder @Inject constructor() : ImageEncoder {

    override suspend fun compress(file: File): CompressedImage = withContext(Dispatchers.IO) {
        // 1) bounds-only decode to read source dimensions
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val srcW = bounds.outWidth.coerceAtLeast(1)
        val srcH = bounds.outHeight.coerceAtLeast(1)

        // 2) downsample on decode
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(srcW, srcH, MAX_EDGE)
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            ?: throw IllegalStateException("无法读取照片文件")

        // 3) precise scale to MAX_EDGE longest edge
        val (targetW, targetH) = scaledSize(decoded.width, decoded.height, MAX_EDGE)
        val scaled = if (targetW != decoded.width || targetH != decoded.height) {
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        } else decoded

        // 4) JPEG encode
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()

        val bytes = out.toByteArray()
        CompressedImage(bytes = bytes, base64 = Base64.getEncoder().encodeToString(bytes))
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageEncoderModule {
    @Binds
    @Singleton
    abstract fun bindImageEncoder(impl: AndroidImageEncoder): ImageEncoder
}
