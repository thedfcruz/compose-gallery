package com.dfcruz.compose.gallery.gradle.configuration

/** Android variant naming conventions used by the Gallery task pipeline. */
internal data class GalleryVariant(
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "gallery: variant must not be blank." }
    }

    val capitalizedName: String = name.replaceFirstChar(Char::uppercase)
    val kspTaskName: String = "ksp${capitalizedName}Kotlin"
    val compileKotlinTaskName: String = "compile${capitalizedName}Kotlin"
    val compileJavaTaskName: String = "compile${capitalizedName}JavaWithJavac"
    val processResourcesTaskName: String = "process${capitalizedName}Resources"
    val previewConfigurationPath: String = "generated/ksp/$name/resources/gallery-metadata.json"
}
