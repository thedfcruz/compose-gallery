package com.dfcruz.compose.gallery.protocol

import kotlinx.serialization.Serializable

@Serializable
data class GalleryPreview(
    val id: String,
    val name: String,
    val group: String,
    val tags: List<String>,
    val simpleName: String,
    val qualifiedName: String,
    val packageName: String,
    val fileName: String,
    val previewMethodQualifiedName: String,
    val variants: List<PreviewVariant>
)