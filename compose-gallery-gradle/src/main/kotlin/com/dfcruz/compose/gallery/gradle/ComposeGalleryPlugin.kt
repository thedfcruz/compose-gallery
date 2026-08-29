package com.dfcruz.compose.gallery.gradle

import com.dfcruz.compose.gallery.gradle.configuration.LayoutlibConfigurations
import com.dfcruz.compose.gallery.gradle.tasks.GalleryAggregation
import com.dfcruz.compose.gallery.gradle.tasks.GalleryTasks
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Configures the Compose Gallery extension and its task pipeline. */
abstract class ComposeGalleryPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val gallery = project.extensions.create("gallery", ComposeGalleryExtension::class.java)
        gallery.variant.convention("debug")
        gallery.failOnRenderFailure.convention(false)
        gallery.renderTimeoutSeconds.convention(10 * 60)

        val layoutlib = LayoutlibConfigurations(project).invoke()
        val renderTask = GalleryTasks(project, gallery, layoutlib).invoke()

        GalleryAggregation(project).invoke(
            galleryDirectory = project.layout.buildDirectory.dir("gallery"),
            renderTask = renderTask,
        )
    }
}
