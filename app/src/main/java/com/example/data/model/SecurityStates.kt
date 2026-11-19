package com.example.data.model

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * UI-level authentication lifecycle states required by the Supabase Auth + RLS model.
 */
sealed interface AuthState {
    data object SignedOut : AuthState
    data object SigningIn : AuthState
    data class SignedIn(
        val authUserId: String,
        val email: String,
        val coupleId: String,
        val userName: String,
        val expiresAtEpochSec: Long = 0L
    ) : AuthState

    data class SessionExpired(
        val message: String = "Your session has expired. Please sign in again."
    ) : AuthState

    data class EmailConfirmationRequired(
        val email: String,
        val message: String = "Please confirm your email address before signing in."
    ) : AuthState

    data class AccountNotLinked(
        val authUserId: String,
        val email: String,
        val message: String = "Your account is signed in, but it is not linked to a couple profile yet."
    ) : AuthState

    data class NetworkError(
        val message: String = "Network unavailable. Please check your internet connection and try again."
    ) : AuthState

    data class InvalidCredentials(
        val message: String = "Invalid email or password. Please check your credentials and try again."
    ) : AuthState
}

/**
 * Reusable UI resource state supporting Loading, Empty, Success, and Error + Retry.
 */
sealed interface ResourceState<out T> {
    data object Loading : ResourceState<Nothing>
    data object Empty : ResourceState<Nothing>
    data class Success<T>(val data: T) : ResourceState<T>
    data class Error(val message: String) : ResourceState<Nothing>
}

sealed class SupabaseDomainError(
    val code: String,
    val userMessage: String,
    cause: Throwable? = null
) : Exception(userMessage, cause) {
    class AuthenticationRequired(cause: Throwable? = null) :
        SupabaseDomainError("authentication_required", "Authentication required. Please sign in.", cause)

    class InvalidCredentials(cause: Throwable? = null) :
        SupabaseDomainError("invalid_credentials", "Invalid email or password.", cause)

    class EmailConfirmationRequired(cause: Throwable? = null) :
        SupabaseDomainError("email_confirmation_required", "Email confirmation required. Please verify your inbox.", cause)

    class SessionExpired(cause: Throwable? = null) :
        SupabaseDomainError("session_expired", "Session expired. Please sign in again.", cause)

    class AccountNotLinked(cause: Throwable? = null) :
        SupabaseDomainError("account_not_linked", "Account is not linked to a couple profile yet.", cause)

    class PermissionDenied(detail: String? = null, cause: Throwable? = null) :
        SupabaseDomainError(
            "permission_denied",
            detail ?: "Permission denied. You do not have access to this couple workspace.",
            cause
        )

    class FileTooLarge(cause: Throwable? = null) :
        SupabaseDomainError("file_too_large", "File too large. Maximum image size is 5 MiB.", cause)

    class UnsupportedImageType(cause: Throwable? = null) :
        SupabaseDomainError("unsupported_image_type", "Unsupported image type. Use JPEG, PNG, WebP, or GIF.", cause)

    class UploadFailed(detail: String? = null, cause: Throwable? = null) :
        SupabaseDomainError("upload_failed", detail ?: "Image upload failed. Please try again.", cause)

    class SignedUrlExpired(cause: Throwable? = null) :
        SupabaseDomainError("signed_url_expired", "Signed image URL expired. Tap to reload.", cause)

    class NetworkUnavailable(cause: Throwable? = null) :
        SupabaseDomainError("network_unavailable", "Network unavailable. Please check your connection and retry.", cause)

    class ValidationError(detail: String) :
        SupabaseDomainError("validation_error", detail, null)
}

/**
 * Maps Supabase SDK, HTTP, storage, and network errors into safe user-facing messages.
 * Never exposes raw SQL, JWTs, access tokens, or internal database schema names.
 */
object SupabaseErrorMapper {
    private val jwtLikeRegex = Regex("""eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]*""")
    private val rawSqlKeywords = listOf(
        "select ", "insert ", "update ", "delete ", "postgres", "pgrst",
        "pg_catalog", "sqlstate", "relation ", "violates row-level security",
        "constraint", "user_accounts", "password_hash", "auth.uid()", "jwt",
        "bearer ", "authorization", "service_role", "stacktrace"
    )

    fun mapError(throwable: Throwable, fallback: String = "Unable to complete request. Please try again."): SupabaseDomainError {
        if (throwable is SupabaseDomainError) return throwable

        if (throwable is UnknownHostException ||
            throwable is ConnectException ||
            throwable is SocketTimeoutException ||
            throwable is SSLHandshakeException ||
            throwable is IOException
        ) {
            return SupabaseDomainError.NetworkUnavailable(throwable)
        }

        val raw = (throwable.message ?: "").lowercase()
        return when {
            raw.contains("unable to resolve host") ||
                raw.contains("failed to connect") ||
                raw.contains("timeout") ||
                raw.contains("network is unreachable") ||
                raw.contains("connection reset") ||
                raw.contains("chain validation failed") -> SupabaseDomainError.NetworkUnavailable(throwable)

            raw.contains("email not confirmed") ||
                raw.contains("confirm your email") -> SupabaseDomainError.EmailConfirmationRequired(throwable)

            raw.contains("invalid login credentials") ||
                raw.contains("invalid_credentials") ||
                raw.contains("invalid credentials") ||
                raw.contains("invalid_grant") -> SupabaseDomainError.InvalidCredentials(throwable)

            raw.contains("jwt expired") ||
                raw.contains("session_not_found") ||
                raw.contains("refresh_token_not_found") ||
                raw.contains("token is expired") -> SupabaseDomainError.SessionExpired(throwable)

            raw.contains("authentication required") ||
                raw.contains("not authenticated") ||
                raw.contains("no session") ||
                raw.contains("401") -> SupabaseDomainError.AuthenticationRequired(throwable)

            raw.contains("row-level security") ||
                raw.contains("permission denied") ||
                raw.contains("42501") ||
                raw.contains("403") -> SupabaseDomainError.PermissionDenied(cause = throwable)

            raw.contains("payload too large") ||
                raw.contains("413") ||
                raw.contains("file too large") ||
                raw.contains("exceeds") -> SupabaseDomainError.FileTooLarge(throwable)

            raw.contains("mime") ||
                raw.contains("unsupported image") ||
                raw.contains("invalid content type") -> SupabaseDomainError.UnsupportedImageType(throwable)

            raw.contains("signed url") ||
                raw.contains("url expired") ||
                raw.contains("token has expired") -> SupabaseDomainError.SignedUrlExpired(throwable)

            raw.contains("upload") ||
                raw.contains("bucket") ||
                raw.contains("storage") -> SupabaseDomainError.UploadFailed(cause = throwable)

            else -> SupabaseDomainError.UploadFailed(sanitizeMessage(throwable.message, fallback), throwable)
        }
    }

    fun toUserMessage(throwable: Throwable, fallback: String = "Something went wrong. Please try again."): String {
        return mapError(throwable, fallback).userMessage
    }

    fun sanitizeMessage(rawMessage: String?, fallback: String = "Unable to complete request. Please try again."): String {
        val text = rawMessage?.trim().orEmpty()
        if (text.isBlank()) return fallback
        if (jwtLikeRegex.containsMatchIn(text)) return fallback
        val lower = text.lowercase()
        if (rawSqlKeywords.any { lower.contains(it) }) return fallback
        if (lower.contains("http://") || lower.contains("https://")) {
            // Avoid leaking internal endpoint URLs or query tokens
            return fallback
        }
        return text
    }
}
