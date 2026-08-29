package com.dfcruz.compose.gallery.gradle.tasks

import com.dfcruz.compose.gallery.gradle.manifest.GalleryIndex
import com.dfcruz.compose.gallery.gradle.manifest.GalleryIndexModule
import com.dfcruz.compose.gallery.gradle.manifest.GalleryPreview
import com.dfcruz.compose.gallery.gradle.manifest.ModuleGalleryManifest
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class AggregateGalleryTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleGalleryDirectories: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @TaskAction
    fun aggregate() {
        val out = outputDir.get().asFile.apply { mkdirs() }
        val previewsOut = out.resolve("previews")

        if (previewsOut.exists()) {
            previewsOut.deleteRecursively()
        }
        previewsOut.mkdirs()

        val modules = moduleGalleryDirectories.files
            .mapNotNull { moduleGalleryDir ->
                val galleryModule = readGalleryModule(moduleGalleryDir) ?: return@mapNotNull null
                val moduleDirectory = sanitizeModulePath(galleryModule.module)

                copyModulePreviews(
                    moduleGalleryDir = moduleGalleryDir,
                    moduleDirectory = moduleDirectory,
                    previewsOut = previewsOut,
                )

                galleryModule.rewriteImagePaths(moduleDirectory)
            }
            .groupBy(ModuleGalleryManifest::module)
            .toSortedMap()
            .map { (module, galleryModules) ->
                GalleryIndexModule(
                    module = module,
                    previews = galleryModules
                        .flatMap(ModuleGalleryManifest::previews)
                        .sortedBy(GalleryPreview::name),
                )
            }

        val merged = GalleryIndex(modules)

        out.resolve("gallery.json").writeText(
            json.encodeToString(GalleryIndex.serializer(), merged)
        )

        val totalPreviews = modules.sumOf { it.previews.size }
        val totalModules = modules.size
        logger.lifecycle("gallery: aggregated $totalPreviews preview(s) across $totalModules module(s).")
    }

    private fun readGalleryModule(moduleGalleryDir: File): ModuleGalleryManifest? {
        val manifest = moduleGalleryDir
            .resolve("gallery-module.json")
            .takeIf { it.isFile }
            ?: return null

        return runCatching {
            json.decodeFromString<ModuleGalleryManifest>(manifest.readText())
        }.getOrNull()
            ?.takeIf { it.module.isNotBlank() }
    }

    private fun ModuleGalleryManifest.rewriteImagePaths(
        moduleDirectory: String,
    ): ModuleGalleryManifest = copy(
        previews = previews.map { preview ->
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

    private fun copyModulePreviews(
        moduleGalleryDir: File,
        moduleDirectory: String,
        previewsOut: File,
    ) {
        val modulePreviews = moduleGalleryDir.resolve("previews")

        if (!modulePreviews.isDirectory) {
            return
        }

        modulePreviews.walkTopDown()
            .filter { it.isFile && it.extension == "png" }
            .forEach { png ->
                val relative = png.relativeTo(modulePreviews)

                val destination = previewsOut
                    .resolve(moduleDirectory)
                    .resolve(relative)

                destination.parentFile.mkdirs()
                png.copyTo(destination, overwrite = true)
            }
    }

    private fun sanitizeModulePath(module: String): String =
        module
            .removePrefix(":")
            .split(":")
            .filter { it.isNotBlank() }
            .joinToString("_") { sanitizeFileName(it) }
            .ifBlank { "root" }

    private fun sanitizeFileName(name: String): String =
        name.trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .replace(Regex("_+"), "_")
}