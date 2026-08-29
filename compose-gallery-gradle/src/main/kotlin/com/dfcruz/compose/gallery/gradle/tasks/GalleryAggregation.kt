package com.dfcruz.compose.gallery.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/** Connects every module Gallery task to the root aggregation tasks. */
internal class GalleryAggregation(
    private val project: Project,
) {

    fun invoke(
        galleryDirectory: Provider<Directory>,
        renderTask: TaskProvider<RenderGalleryPreviewsTask>,
    ) {
        val rootTasks = rootTasks()
        rootTasks.aggregateTask.configure {
            moduleGalleryDirectories.from(galleryDirectory)
            dependsOn(renderTask)
        }
    }

    private fun rootTasks(): RootGalleryTasks {
        val root = project.rootProject
        return root.extensions.findByType(RootGalleryTasks::class.java)
            ?: root.extensions.create(ROOT_TASKS_EXTENSION, RootGalleryTasks::class.java)
                .also { tasks ->
                    tasks.aggregateTask = root.tasks.register(
                        "aggregateGallery",
                        AggregateGalleryTask::class.java,
                    ) {
                        group = "gallery"
                        description = "Aggregates Gallery previews from all participating modules."
                        outputDir.set(root.layout.buildDirectory.dir("gallery"))
                    }
                    root.tasks.register("generateGallery") {
                        group = "gallery"
                        description = "Renders and aggregates all Compose previews."
                        dependsOn(tasks.aggregateTask)
                    }
                }
    }

    /** Root-scoped task providers; this avoids looking up or realizing tasks from each module. */
    internal open class RootGalleryTasks {
        lateinit var aggregateTask: TaskProvider<AggregateGalleryTask>
    }

    private companion object {
        const val ROOT_TASKS_EXTENSION = "composeGalleryRootTasks"
    }
}
