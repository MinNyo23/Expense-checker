package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.memory.MemoryCache
import coil.request.Options
import okio.Buffer

/**
 * Shared Coil ImageLoader optimized for smooth 60/120fps LazyColumn scrolling:
 * - Supports synced Base64 data URIs ("data:image/...;base64,...") off the UI thread.
 * - Uses 25% of app heap for MemoryCache and 40MB DiskCache.
 * - Enables RGB_565 when alpha is unnecessary and hardware bitmaps for fast GPU rendering.
 */
object CoupleImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: buildImageLoader(context.applicationContext).also { instance = it }
        }
    }

    private fun buildImageLoader(appContext: Context): ImageLoader {
        return ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("couple_image_cache"))
                    .maxSizeBytes(40L * 1024L * 1024L)
                    .build()
            }
            .components {
                add(Base64DataUriFetcher.Factory())
            }
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowHardware(true)
            .crossfade(120)
            .respectCacheHeaders(false)
            .build()
    }
}

/**
 * Decodes "data:image/...;base64,<payload>" strings off the UI thread inside Coil's pipeline
 * so couple photo memos and avatars are downscaled & cached in RAM instead of stalling scrolling.
 */
private class Base64DataUriFetcher(
    private val dataUri: String,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val commaIndex = dataUri.indexOf(',')
        if (commaIndex == -1 || commaIndex >= dataUri.length - 1) {
            throw IllegalArgumentException("Invalid Base64 image data URI")
        }
        val header = dataUri.substring(0, commaIndex)
        val mimeType = header
            .removePrefix("data:")
            .substringBefore(';')
            .ifBlank { "image/jpeg" }

        val base64Payload = dataUri.substring(commaIndex + 1).trim()
        if (base64Payload.isEmpty()) {
            throw IllegalArgumentException("Empty Base64 image payload")
        }
        val decodedBytes = Base64.decode(base64Payload, Base64.DEFAULT)
        val buffer = Buffer().write(decodedBytes)

        return SourceResult(
            source = ImageSource(buffer, options.context),
            mimeType = mimeType,
            dataSource = DataSource.MEMORY
        )
    }

    class Factory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.startsWith("data:image/", ignoreCase = true)) {
                Base64DataUriFetcher(data, options)
            } else {
                null
            }
        }
    }
}
