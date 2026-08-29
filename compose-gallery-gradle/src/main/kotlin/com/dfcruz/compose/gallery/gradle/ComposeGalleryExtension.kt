package com.dfcruz.compose.gallery.gradle

import org.gradle.api.provider.Property

/**
 * Configuration for the Compose Gallery Gradle plugin.
 */
abstract class ComposeGalleryExtension {

    /** Android variant whose previews are collected and rendered. Defaults to `debug`. */
    abstract val variant: Property<String>

    /** Fails the build when one or more previews cannot be rendered. Defaults to `false`. */
    abstract val failOnRenderFailure: Property<Boolean>

    /** Maximum time allowed for the renderer to finish, in seconds. Defaults to 600. */
    abstract val renderTimeoutSeconds: Property<Int>
}