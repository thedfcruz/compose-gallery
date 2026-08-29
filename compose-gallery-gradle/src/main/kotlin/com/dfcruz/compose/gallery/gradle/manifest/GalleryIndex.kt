package com.dfcruz.compose.gallery.gradle.manifest

import kotlinx.serialization.Serializable

@Serializable
data class GalleryIndex(
    val modules: List<GalleryIndexModule>,
)

@Serializable
data class GalleryIndexModule(
    val module: String,
    val previews: List<GalleryPreview>,
)
