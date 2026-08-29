package com.dfcruz.compose.gallery.gradle.tasks

import com.dfcruz.compose.gallery.gradle.ComposeGalleryExtension
import com.dfcruz.compose.gallery.gradle.configuration.AndroidVariantArtifacts
import com.dfcruz.compose.gallery.gradle.configuration.LayoutlibSetup
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/** Registers the Gallery tasks for one Android module. */
internal class GalleryTasks(
    private val project: Project,
    private val extension: ComposeGalleryExtension,
    private val layoutlib: LayoutlibSetup,
) {
    fun invoke(): TaskProvider<RenderGalleryPreviewsTask> {
        val artifacts = AndroidVariantArtifacts(project, extension.variant)
        val galleryDirectory = project.layout.buildDirectory.dir("gallery")

        val renderTask = project.tasks.register(
            "renderGalleryLayoutlib",
            RenderGalleryPreviewsTask::class.java
        ) {
            description = "Renders this module's Gallery previews with Layoutlib."
            previewConfigurationFile.set(artifacts.previewConfigurationFile)
            rendererClasspath.from(layoutlib.rendererClasspath)
            layoutlibDir.set(layoutlib.prepareTask.flatMap { it.layoutlibDir })
            namespace.convention(project.provider { project.androidNamespace() })
            apiLevel.convention(LAYOUTLIB_API)
            module.convention(project.path)
            variant.set(extension.variant)
            renderTimeoutSeconds.set(extension.renderTimeoutSeconds)
            failOnRenderFailure.set(extension.failOnRenderFailure)
            workDir.set(project.layout.buildDirectory.dir("preview-render"))
            thumbnailsDirectory.set(galleryDirectory.map { it.dir("previews") })

            artifacts.addRuntimeClasspathTo(appClasspath)
            artifacts.addProjectClassesTo(projectClasspath)
            artifacts.addRClassesTo(rClassPath)
            artifacts.addResourceApkTo(resourceApk)

            dependsOn(layoutlib.prepareTask)
            dependsOn(artifacts.kspTaskName)
            dependsOn(artifacts.compileKotlinTaskName)
            dependsOn(artifacts.processResourcesTask)
            dependsOn(project.tasks.matching { it.name == artifacts.compileJavaTaskName.get() })
        }

        project.tasks.register("generateGallery") {
            group = "gallery"
            description = "Renders every @Preview component in this module."
            dependsOn(renderTask)
        }

        return renderTask
    }

    /** Reads the Android namespace without adding an AGP compile-time dependency to this published plugin. */
    private fun Project.androidNamespace(): String =
        extensions.findByName("android")?.let { android ->
            runCatching {
                android.javaClass.getMethod("getNamespace").invoke(android) as? String
            }.getOrNull()
        } ?: ""

    private companion object {
        const val LAYOUTLIB_API = "36"
    }
}
