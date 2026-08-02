package com.dfcruz.compose.gallery.gradle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

data class GalleryPreviews(
    val previews: List<GalleryPreviewDetails>,
)

data class GalleryPreviewDetails(
    val id: String,
    val name: String,
    val group: String,
    val tags: List<String>,
    val simpleName: String,
    val qualifiedName: String,
    val packageName: String,
    val fileName: String,
    val previewMethodQualifiedName: String,
    val variants: List<PreviewVariantDetails>,
)

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

internal fun parsePreviewConfiguration(text: String): GalleryPreviews {
    val root = Json.parseToJsonElement(text).jsonObject

    val previews = root.arr("previews").map { element ->
        val preview = element.jsonObject

        GalleryPreviewDetails(
            id = preview.str("id") ?: "",
            name = preview.str("name") ?: "",
            group = preview.str("group") ?: "",
            tags = preview.arr("tags").mapNotNull { it.str() },
            simpleName = preview.str("simpleName") ?: "",
            qualifiedName = preview.str("qualifiedName") ?: "",
            packageName = preview.str("packageName") ?: "",
            fileName = preview.str("fileName") ?: "",
            previewMethodQualifiedName = preview.str("previewMethodQualifiedName") ?: "",
            variants = preview.arr("variants").map { variantElement ->
                val variant = variantElement.jsonObject
                PreviewVariantDetails(
                    name = variant.str("name") ?: "",
                    group = variant.str("group") ?: "",
                    apiLevel = variant.int("apiLevel"),
                    widthDp = variant.int("widthDp"),
                    heightDp = variant.int("heightDp"),
                    locale = variant.str("locale") ?: "",
                    fontScale = variant.float("fontScale") ?: 1f,
                    showSystemUi = variant.bool("showSystemUi"),
                    showBackground = variant.bool("showBackground"),
                    backgroundColor = variant.long("backgroundColor") ?: 0L,
                    uiMode = variant.int("uiMode") ?: 0,
                    device = variant.str("device") ?: "",
                    wallpaper = variant.int("wallpaper") ?: 0,
                )
            },
        )
    }

    return GalleryPreviews(previews)
}
