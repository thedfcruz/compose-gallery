package com.dfcruz.compose.gallery.ksp.model

import kotlinx.serialization.Serializable

@Serializable
data class GalleryMetadata(
    val version: Int,
    val previews: List<GalleryPreview>
)



