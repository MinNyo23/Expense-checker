package com.example.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageStorageHelper {
    private const val TAG = "ImageStorageHelper"

    /**
     * Copies a content URI from Android Photo Picker into the app's internal files directory.
     * Returns the absolute path of the local file, or null on failure.
     * This avoids URI permission expirations across app reboots.
     */
    fun saveImageToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val receiptsDir = File(context.filesDir, "receipt_photos").apply {
                if (!exists()) mkdirs()
            }
            val fileName = "receipt_${UUID.randomUUID()}.jpg"
            val targetFile = File(receiptsDir, fileName)

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
                outputStream = FileOutputStream(targetFile)
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
                targetFile.absolutePath
            } finally {
                inputStream?.close()
                outputStream?.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image locally", e)
            null
        }
    }

    /**
     * Copies avatar photo into internal storage for permanent local caching.
     */
    fun saveAvatarToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val avatarsDir = File(context.filesDir, "avatar_photos").apply {
                if (!exists()) mkdirs()
            }
            val fileName = "avatar_${UUID.randomUUID()}.jpg"
            val targetFile = File(avatarsDir, fileName)

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
                outputStream = FileOutputStream(targetFile)
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
                targetFile.absolutePath
            } finally {
                inputStream?.close()
                outputStream?.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save avatar locally", e)
            null
        }
    }

    /**
     * Compresses the selected image to a compact base64 thumbnail (approx 6-10KB)
     * so it can seamlessly sync across Supabase database between both partners.
     */
    fun encodeImageToBase64Thumbnail(context: Context, sourceUri: Uri, maxSize: Int = 180): String? {
        return encodeUriToBase64Internal(context, sourceUri, maxSize = maxSize, quality = 80)
    }

    /**
     * Copies memo photo into internal storage for permanent local caching.
     */
    fun saveMemoImageToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val memoDir = File(context.filesDir, "memo_photos").apply {
                if (!exists()) mkdirs()
            }
            val fileName = "memo_${UUID.randomUUID()}.jpg"
            val targetFile = File(memoDir, fileName)

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
                outputStream = FileOutputStream(targetFile)
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
                targetFile.absolutePath
            } finally {
                inputStream?.close()
                outputStream?.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save memo photo locally", e)
            null
        }
    }

    /**
     * Compresses the selected memo image to a crisp yet compact base64 JPEG (approx 35-50KB)
     * so both partners can sync full love photos in real-time over Supabase PostgREST and WebSockets.
     */
    fun encodeMemoImageToBase64(context: Context, sourceUri: Uri, maxSize: Int = 640): String? {
        return encodeUriToBase64Internal(context, sourceUri, maxSize = maxSize, quality = 75)
    }

    private fun encodeUriToBase64Internal(
        context: Context,
        sourceUri: Uri,
        maxSize: Int,
        quality: Int
    ): String? {
        return try {
            val boundsOptions = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            var inSampleSize = 1
            val rawW = boundsOptions.outWidth
            val rawH = boundsOptions.outHeight
            if (rawW > maxSize && rawH > maxSize) {
                val halfW = rawW / 2
                val halfH = rawH / 2
                while ((halfW / inSampleSize) >= maxSize && (halfH / inSampleSize) >= maxSize) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val decodedBitmap = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val width = decodedBitmap.width
            val height = decodedBitmap.height
            val scale = (maxSize.toFloat() / Math.max(width, height).coerceAtLeast(1)).coerceAtMost(1f)
            val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
            val scaledHeight = (height * scale).toInt().coerceAtLeast(1)

            val scaledBitmap = if (scaledWidth != width || scaledHeight != height) {
                android.graphics.Bitmap.createScaledBitmap(decodedBitmap, scaledWidth, scaledHeight, true).also {
                    decodedBitmap.recycle()
                }
            } else {
                decodedBitmap
            }

            val byteArray = java.io.ByteArrayOutputStream().use { out ->
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            scaledBitmap.recycle()

            val base64 = android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to encode image safely to base64: ${t.message}")
            null
        }
    }
}
