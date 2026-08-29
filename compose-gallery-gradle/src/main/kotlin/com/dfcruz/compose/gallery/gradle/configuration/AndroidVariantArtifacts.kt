package com.dfcruz.compose.gallery.gradle.configuration

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

/** Lazily derives the Android and KSP inputs for the configured Gallery variant. */
internal class AndroidVariantArtifacts(
    private val project: Project,
    variant: Provider<String>,
) {

    private val galleryVariant: Provider<GalleryVariant> = variant.map(::GalleryVariant)

    val previewConfigurationFile: Provider<RegularFile> =
        project.layout.buildDirectory.file(galleryVariant.map(GalleryVariant::previewConfigurationPath))

    val kspTaskName: Provider<String> =
        galleryVariant.map(GalleryVariant::kspTaskName)

    val compileKotlinTaskName: Provider<String> =
        galleryVariant.map(GalleryVariant::compileKotlinTaskName)

    val compileJavaTaskName: Provider<String> =
        galleryVariant.map(GalleryVariant::compileJavaTaskName)

    val processResourcesTask: Provider<Task> = galleryVariant.flatMap { selectedVariant ->
        project.tasks.named(selectedVariant.processResourcesTaskName)
    }

    fun addProjectClassesTo(classpath: ConfigurableFileCollection) {
        classpath.from(project.layout.buildDirectory.dir(galleryVariant.map { "tmp/kotlin-classes/${it.name}" }))
        classpath.from(
            galleryVariant.map {
                "intermediates/built_in_kotlinc/${it.name}/compile${it.capitalizedName}Kotlin/classes"
            }.let(project.layout.buildDirectory::dir),
        )
        classpath.from(
            galleryVariant.map {
                "intermediates/javac/${it.name}/compile${it.capitalizedName}JavaWithJavac/classes"
            }.let(project.layout.buildDirectory::dir),
        )
    }

    fun addRuntimeClasspathTo(classpath: ConfigurableFileCollection) {
        classpath.from(
            galleryVariant.flatMap { selectedVariant ->
                project.configurations.named("${selectedVariant.name}RuntimeClasspath")
            }.map { runtimeClasspath ->
                runtimeClasspath.incoming.artifactView {
                    attributes.attribute(
                        ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                        "android-classes-jar",
                    )
                }.files
            },
        )
    }

    fun addRClassesTo(classpath: ConfigurableFileCollection) {
        classpath.from(project.provider {
            val selectedVariant = galleryVariant.get().name
            project.fileTree(project.layout.buildDirectory.dir("intermediates")) {
                include(
                    "**/compile_and_runtime_r_class_jar/$selectedVariant/process*Resources/R.jar",
                    "**/compile_and_runtime_r_class_jar/${selectedVariant}UnitTest/process*Resources/R.jar",
                )
            }
        })
    }

    /** Adds the resource package produced by AGP without depending on its intermediate output location. */
    fun addResourceApkTo(resources: ConfigurableFileCollection) {
        resources.from(
            processResourcesTask.map { task ->
                task.outputs.files.asFileTree.matching {
                    include("**/*.$RESOURCE_APK_EXTENSION")
                }
            },
        )
    }

    private companion object {
        const val RESOURCE_APK_EXTENSION = "ap_"
    }
}
