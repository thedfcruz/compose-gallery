package com.dfcruz.compose.gallery.gradle

import kotlinx.serialization.Serializable

@Serializable
data class AggregatedGallery(
    val modules: List<AggregatedGalleryModule>,
)

@Serializable
data class AggregatedGalleryModule(
    val module: String,
    val previews: List<GalleryModulePreview>,
)
