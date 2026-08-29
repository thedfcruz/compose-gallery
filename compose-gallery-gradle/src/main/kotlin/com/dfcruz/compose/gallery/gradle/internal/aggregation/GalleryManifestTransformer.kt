package com.dfcruz.compose.gallery.gradle.internal.aggregation

import com.dfcruz.compose.gallery.gradle.manifest.ModuleGalleryManifest

internal object GalleryManifestTransformer {

    fun moduleDirectory(modulePath: String): String =
        modulePath
            .removePrefix(":")
            .split(":")
            .filter(String::isNotBlank)
            .joinToString("_") { fileNameSegment(it) }
            .ifBlank { "root" }

    fun rewriteImagePaths(
        manifest: ModuleGalleryManifest,
        moduleDirectory: String,
    ): ModuleGalleryManifest = manifest.copy(
        previews = manifest.previews.map { preview ->
            preview.copy(
                variants = preview.variants.map { variant ->
                    val image = variant.image
                    if (image.isNullOrBlank()) variant else {
                        variant.copy(
                            image = "previews/$moduleDirectory/${image.removePrefix("previews/")}",
                        )
                    }
                },
            )
        },
    )

    private fun fileNameSegment(value: String): String =
        value.trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .replace(Regex("_+"), "_")
}
