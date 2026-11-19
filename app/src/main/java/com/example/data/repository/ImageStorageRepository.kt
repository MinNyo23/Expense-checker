package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.data.model.AuthState
import com.example.data.model.SupabaseDomainError
import com.example.data.model.SupabaseErrorMapper
import com.example.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

interface ImageStorageDataSource {
    suspend fun uploadPrivateImage(objectPath: String, bytes: ByteArray, mimeType: String): String
    suspend fun createSignedUrl(objectPath: String, expiresInSeconds: Long): String
    suspend fun deletePrivateImage(objectPath: String)
}

class SupabaseImageStorageDataSource(
    private val clientProvider: SupabaseClientProvider
) : ImageStorageDataSource {

    override suspend fun uploadPrivateImage(
        objectPath: String,
        bytes: ByteArray,
        mimeType: String
    ): String = withContext(Dispatchers.IO) {
        val bucket = clientProvider.storage.from(ImageStorageRepository.IMAGES_BUCKET)
        bucket.upload(path = objectPath, data = bytes) {
            upsert = false
            contentType = ContentType.parse(mimeType)
        }
        objectPath
    }

    override suspend fun createSignedUrl(
        objectPath: String,
        expiresInSeconds: Long
    ): String = withContext(Dispatchers.IO) {
        val bucket = clientProvider.storage.from(ImageStorageRepository.IMAGES_BUCKET)
        bucket.createSignedUrl(path = objectPath, expiresIn = expiresInSeconds.seconds)
    }

    override suspend fun deletePrivateImage(objectPath: String) = withContext(Dispatchers.IO) {
        val bucket = clientProvider.storage.from(ImageStorageRepository.IMAGES_BUCKET)
        bucket.delete(listOf(objectPath))
        Unit
    }
}

/**
 * Manages secure uploads, short-lived signed URLs, and deletion for private images in the "images" bucket.
 *
 * Security & validation rules:
 * - Bucket is private ("images"), upsert = false.
 * - Object paths always follow "<authenticated-user-id>/<uuid>.<ext>".
 * - Accepts ONLY image/jpeg, image/png, image/webp, image/gif verified by magic bytes (+ ContentResolver MIME).
 * - Rejects images larger than 5 MiB (5 * 1024 * 1024 bytes).
 */
class ImageStorageRepository(
    private val dataSource: ImageStorageDataSource,
    private val authStateProvider: () -> AuthState,
    private val timeProviderMillis: () -> Long = { System.currentTimeMillis() }
) {
    private data class CachedSignedUrl(
        val url: String,
        val expiresAtMillis: Long
    )

    private val signedUrlCache = ConcurrentHashMap<String, CachedSignedUrl>()

    suspend fun uploadFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val state = authStateProvider()
        if (state !is AuthState.SignedIn) {
            return@withContext Result.failure(SupabaseDomainError.AuthenticationRequired())
        }
        val resolver: ContentResolver = context.contentResolver
        val declaredMime = resolver.getType(uri)?.lowercase()?.trim()

        if (declaredMime != null && declaredMime !in ALLOWED_MIME_TYPES.keys) {
            return@withContext Result.failure(SupabaseDomainError.UnsupportedImageType())
        }

        val bytes = try {
            resolver.openInputStream(uri)?.use { stream ->
                readWithSizeLimit(stream, MAX_IMAGE_BYTES)
            } ?: return@withContext Result.failure(SupabaseDomainError.UploadFailed("Unable to read selected photo."))
        } catch (tooLarge: SupabaseDomainError.FileTooLarge) {
            return@withContext Result.failure(tooLarge)
        } catch (e: Exception) {
            return@withContext Result.failure(SupabaseErrorMapper.mapError(e))
        }

        uploadValidatedBytes(bytes, declaredMime)
    }

    suspend fun uploadValidatedBytes(
        bytes: ByteArray,
        declaredMimeType: String? = null
    ): Result<String> {
        val state = authStateProvider()
        if (state !is AuthState.SignedIn) {
            return Result.failure(SupabaseDomainError.AuthenticationRequired())
        }
        val authUserId = state.authUserId.trim()
        if (authUserId.isBlank() || authUserId.contains("..") || authUserId.contains("/")) {
            return Result.failure(SupabaseDomainError.AuthenticationRequired())
        }

        if (bytes.isEmpty()) {
            return Result.failure(SupabaseDomainError.UploadFailed("Selected image file is empty."))
        }
        if (bytes.size > MAX_IMAGE_BYTES) {
            return Result.failure(SupabaseDomainError.FileTooLarge())
        }

        val detectedMime = detectMimeTypeFromMagicBytes(bytes)
            ?: return Result.failure(SupabaseDomainError.UnsupportedImageType())

        val cleanDeclared = declaredMimeType?.lowercase()?.trim()
        if (cleanDeclared != null &&
            cleanDeclared.isNotBlank() &&
            cleanDeclared !in ALLOWED_MIME_TYPES.keys
        ) {
            return Result.failure(SupabaseDomainError.UnsupportedImageType())
        }
        if (cleanDeclared != null && cleanDeclared.isNotBlank() && cleanDeclared != detectedMime) {
            // Do not trust spoofed MIME or renamed extensions
            return Result.failure(SupabaseDomainError.UnsupportedImageType())
        }

        val ext = ALLOWED_MIME_TYPES[detectedMime]
            ?: return Result.failure(SupabaseDomainError.UnsupportedImageType())
        val objectPath = "$authUserId/${UUID.randomUUID()}.$ext"

        return try {
            val storedPath = dataSource.uploadPrivateImage(
                objectPath = objectPath,
                bytes = bytes,
                mimeType = detectedMime
            )
            Result.success(storedPath)
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }

    suspend fun getSignedUrl(
        objectPath: String,
        forceRefresh: Boolean = false
    ): Result<String> {
        val state = authStateProvider()
        if (state !is AuthState.SignedIn) {
            return Result.failure(SupabaseDomainError.AuthenticationRequired())
        }
        val cleanPath = objectPath.trim()
        if (!isValidPrivateObjectPath(cleanPath)) {
            return Result.failure(SupabaseDomainError.ValidationError("Invalid image object path."))
        }

        val now = timeProviderMillis()
        if (!forceRefresh) {
            val cached = signedUrlCache[cleanPath]
            if (cached != null && cached.expiresAtMillis - SIGNED_URL_REFRESH_BUFFER_MS > now) {
                return Result.success(cached.url)
            }
        }

        return try {
            val signedUrl = dataSource.createSignedUrl(cleanPath, SIGNED_URL_EXPIRY_SECONDS)
            if (signedUrl.isBlank()) {
                return Result.failure(SupabaseDomainError.SignedUrlExpired())
            }
            signedUrlCache[cleanPath] = CachedSignedUrl(
                url = signedUrl,
                expiresAtMillis = now + SIGNED_URL_EXPIRY_SECONDS * 1000L
            )
            Result.success(signedUrl)
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }

    fun invalidateSignedUrl(objectPath: String) {
        signedUrlCache.remove(objectPath.trim())
    }

    suspend fun deleteImage(objectPath: String): Result<Unit> {
        val state = authStateProvider()
        if (state !is AuthState.SignedIn) {
            return Result.failure(SupabaseDomainError.AuthenticationRequired())
        }
        val cleanPath = objectPath.trim()
        if (!isValidPrivateObjectPath(cleanPath)) {
            return Result.success(Unit)
        }
        signedUrlCache.remove(cleanPath)
        return try {
            dataSource.deletePrivateImage(cleanPath)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(SupabaseErrorMapper.mapError(e))
        }
    }

    companion object {
        const val IMAGES_BUCKET = "images"
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024 // 5 MiB
        const val SIGNED_URL_EXPIRY_SECONDS = 600L // 10 minutes
        private const val SIGNED_URL_REFRESH_BUFFER_MS = 30_000L

        val ALLOWED_MIME_TYPES = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
            "image/gif" to "gif"
        )

        fun isValidPrivateObjectPath(path: String): Boolean {
            val trimmed = path.trim()
            if (trimmed.isBlank()) return false
            if (trimmed.startsWith("/") || trimmed.contains("..")) return false
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("data:", ignoreCase = true)
            ) return false
            val parts = trimmed.split("/")
            return parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
        }

        private fun readWithSizeLimit(input: java.io.InputStream, maxBytes: Int): ByteArray {
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    throw SupabaseDomainError.FileTooLarge()
                }
                buffer.write(chunk, 0, read)
            }
            return buffer.toByteArray()
        }

        fun detectMimeTypeFromMagicBytes(bytes: ByteArray): String? {
            if (bytes.size < 4) return null
            // JPEG: FF D8 FF
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
                return "image/jpeg"
            }
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if (bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte()
            ) {
                return "image/png"
            }
            // GIF: GIF87a or GIF89a
            if (bytes.size >= 6 &&
                bytes[0] == 'G'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == '8'.code.toByte() &&
                (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
                bytes[5] == 'a'.code.toByte()
            ) {
                return "image/gif"
            }
            // WebP: RIFF....WEBP
            if (bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() &&
                bytes[11] == 'P'.code.toByte()
            ) {
                return "image/webp"
            }
            return null
        }
    }
}
