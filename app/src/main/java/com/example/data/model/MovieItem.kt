package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MovieItem(
    val id: String = "",
    val title: String = "",
    val isWatched: Boolean = false,
    val addedBy: String = "",
    val note: String = "",
    val addedAt: String = "",
    val watchedAt: String = ""
)
