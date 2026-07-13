package com.dfcruz.compose.gallery.annotations

/**
 * Marks a Compose Preview to be included in the Compose Gallery.
 *
 * @property name Optional display name shown in the gallery.
 * @property group Optional group used to organize previews.
 * @property tags Optional tags associated with the preview.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Gallery(
    val name: String = "",
    val group: String = "",
    val tags: Array<String> = [],
)