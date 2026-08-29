package com.dfcruz.compose.gallery.gradle.manifest

import kotlinx.serialization.Serializable

@Serializable
data class ModuleGalleryManifest(
    val version: Int = 1,
    val module: String,
    val previews: List<GalleryPreview>,
)

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
    val variants: List<RenderedPreviewVariant>,
)

@Serializable
data class RenderedPreviewVariant(
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
