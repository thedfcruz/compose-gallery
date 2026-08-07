package com.dfcruz.compose.gallery.gradle

import kotlinx.serialization.Serializable

@Serializable
data class GalleryModule(
    val version: Int = 1,
    val module: String,
    val previews: List<GalleryModulePreview>,
)

@Serializable
data class GalleryModulePreview(
    val id: String,
    val name: String,
    val group: String,
    val tags: List<String>,
    val simpleName: String,
    val qualifiedName: String,
    val packageName: String,
    val fileName: String,
    val variants: List<GalleryModuleVariant>,
)

@Serializable
data class GalleryModuleVariant(
    val name: String,
    val status: GalleryRenderStatus,
    val image: String? = null,
    val error: String? = null,
)

@Serializable
enum class GalleryRenderStatus {
    SUCCESS,
    FAILED,
}