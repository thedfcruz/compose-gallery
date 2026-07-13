package com.dfcruz.compose.gallery.protocol

import kotlinx.serialization.Serializable

@Serializable
data class GalleryMetadata(
    val version: Int,
    val previews: List<GalleryPreview>
)



