package com.example.data.model

import kotlinx.serialization.Serializable

/**
 * Memo item stored in public.memos.
 *
 * When an image is attached, [image_url] stores the private Storage object path
 * inside the "images" bucket: "<authenticated-user-id>/<generated-uuid>.<ext>"
 * (never a public URL or Base64 payload). UI components resolve [image_url]
 * via short-lived signed URLs from ImageStorageRepository.
 */
@Serializable
data class CoupleMemo(
    val id: String,
    val couple_id: String,
    val user_name: String,
    val title: String = "",
    val note: String = "",
    val image_url: String = "",
    val date: String = "",
    val created_at: String = ""
)
