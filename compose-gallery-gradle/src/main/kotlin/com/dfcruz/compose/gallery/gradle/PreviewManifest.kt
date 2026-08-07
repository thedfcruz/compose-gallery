package com.dfcruz.compose.gallery.gradle

import kotlinx.serialization.Serializable

@Serializable
data class GalleryPreviews(
    val previews: List<GalleryPreviewDetails> = emptyList(),
)

@Serializable
data class GalleryPreviewDetails(
    val id: String = "",
    val name: String = "",
    val group: String = "",
    val tags: List<String> = emptyList(),
    val simpleName: String = "",
    val qualifiedName: String = "",
    val packageName: String = "",
    val fileName: String = "",
    val previewMethodQualifiedName: String = "",
    val variants: List<PreviewVariantDetails> = emptyList(),
)

@Serializable
data class PreviewVariantDetails(
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