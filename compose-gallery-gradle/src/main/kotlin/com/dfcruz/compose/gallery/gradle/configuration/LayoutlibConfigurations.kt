package com.dfcruz.compose.gallery.gradle.configuration

import com.dfcruz.compose.gallery.gradle.tasks.PrepareGalleryLayoutlibTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider

/** Declares the isolated dependencies required by the Layoutlib renderer. */
internal class LayoutlibConfigurations(
    private val project: Project,
) {

    fun invoke(): LayoutlibSetup {
        val rendererClasspath = resolvableConfiguration("galleryLayoutlibRenderer")
        val runtimeArchive = resolvableConfiguration("galleryLayoutlibRuntime")
        val resourcesArchive = resolvableConfiguration("galleryLayoutlibResources")

        project.dependencies.add(
            rendererClasspath.name,
            "com.android.tools.compose:compose-preview-renderer:$RENDERER_VERSION",
        )
        project.dependencies.add(
            rendererClasspath.name,
            "com.android.tools.layoutlib:layoutlib:$LAYOUTLIB_VERSION",
        )
        project.dependencies.add(
            runtimeArchive.name,
            "com.android.tools.layoutlib:layoutlib-runtime:$LAYOUTLIB_VERSION:${osClassifier()}",
        )
        project.dependencies.add(
            resourcesArchive.name,
            "com.android.tools.layoutlib:layoutlib-resources:$LAYOUTLIB_VERSION",
        )

        val prepareTask = project.tasks.register(
            "prepareGalleryLayoutlib",
            PrepareGalleryLayoutlibTask::class.java,
        ) {
            description = "Prepares Layoutlib resources used internally by Compose Gallery."
            runtimeJar.from(runtimeArchive)
            resourcesJar.from(resourcesArchive)
            layoutlibDir.set(project.layout.buildDirectory.dir("preview-layoutlib"))
        }

        return LayoutlibSetup(prepareTask, rendererClasspath)
    }

    private fun resolvableConfiguration(name: String): Configuration =
        project.configurations.create(name) {
            isCanBeConsumed = false
            isCanBeResolved = true
        }

    private fun osClassifier(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            "mac" in os || "darwin" in os -> if ("aarch64" in arch || "arm" in arch) "mac-arm" else "mac"
            "win" in os -> "win"
            else -> "linux"
        }
    }

    private companion object {
        const val LAYOUTLIB_VERSION = "16.2.1"
        const val RENDERER_VERSION = "0.0.1-alpha15"
    }
}

/** Shared Layoutlib classpath and preparation task. */
internal data class LayoutlibSetup(
    val prepareTask: TaskProvider<PrepareGalleryLayoutlibTask>,
    val rendererClasspath: Configuration,
)
