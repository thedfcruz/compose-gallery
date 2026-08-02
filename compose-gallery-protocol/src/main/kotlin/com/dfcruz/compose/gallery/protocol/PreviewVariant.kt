package com.dfcruz.compose.gallery.protocol

import kotlinx.serialization.Serializable

@Serializable
data class PreviewVariant(
    val name: String = "",
    val group: String = "",
    val apiLevel: Int? = null,
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val locale: String = "",
    val fontScale: Float = 1f,
    val showSystemUi: Boolean = false,
    val showBackground: Boolean = false,
    val backgroundColor: Long = 0,
    val uiMode: Int = 0,
    val device: String = "",
    val wallpaper: Int = 0,
)