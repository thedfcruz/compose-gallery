package com.dfcruz.compose.gallery.gradle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
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
    abstract val dependencyGalleryDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun aggregate() {
        val json = Json { prettyPrint = true }

        val out = outputDir.get().asFile.apply { mkdirs() }
        val previewsOut = out.resolve("previews")

        if (previewsOut.exists()) {
            previewsOut.deleteRecursively()
        }
        previewsOut.mkdirs()

        val byModule = mutableMapOf<String, MutableList<JsonObject>>()

        dependencyGalleryDirs.files.forEach { moduleGalleryDir ->
            val manifest = moduleGalleryDir
                .resolve("gallery-module.json")
                .takeIf { it.isFile }
                ?: return@forEach

            val root = runCatching {
                json.parseToJsonElement(manifest.readText()).jsonObject
            }.getOrNull() ?: return@forEach

            val module = root["module"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: return@forEach

            val moduleDirectory = sanitizeModulePath(module)

            val previews = root["previews"] as? JsonArray
                ?: return@forEach

            previews.forEach { element ->
                val entry = element as? JsonObject
                    ?: return@forEach

                val aggregatedEntry = rewriteImagePaths(
                    entry = entry,
                    moduleDirectory = moduleDirectory,
                )

                byModule
                    .getOrPut(module) { mutableListOf() }
                    .add(aggregatedEntry)
            }

            copyModulePreviews(
                moduleGalleryDir = moduleGalleryDir,
                moduleDirectory = moduleDirectory,
                previewsOut = previewsOut,
            )
        }

        val modules = buildJsonArray {
            byModule.keys
                .sorted()
                .forEach { module ->
                    add(
                        buildJsonObject {
                            put("module", JsonPrimitive(module))
                            putJsonArray("previews") {
                                byModule[module]
                                    ?.sortedBy { preview ->
                                        preview["name"]
                                            ?.jsonPrimitive
                                            ?.contentOrNull
                                            ?: ""
                                    }
                                    ?.forEach { preview ->
                                        add(preview)
                                    }
                            }
                        }
                    )
                }
        }

        val merged = buildJsonObject {
            put("modules", modules)
        }

        out.resolve("gallery.json").writeText(
            json.encodeToString(
                JsonObject.serializer(),
                merged,
            )
        )

        val totalPreviews = byModule.values.sumOf { it.size }
        val totalModules = byModule.size
        logger.lifecycle("gallery: aggregated $totalPreviews preview(s) across $totalModules module(s).")
    }

    private fun rewriteImagePaths(
        entry: JsonObject,
        moduleDirectory: String,
    ): JsonObject {
        val variants = entry["variants"] as? JsonArray
            ?: return entry

        val rewrittenVariants = buildJsonArray {
            variants.forEach { element ->
                val variant = element as? JsonObject
                    ?: return@forEach

                val image = variant["image"]
                    ?.jsonPrimitive
                    ?.contentOrNull

                val rewrittenVariant = if (!image.isNullOrBlank()) {
                    JsonObject(
                        variant + (
                                "image" to JsonPrimitive(
                                    "previews/$moduleDirectory/${image.removePrefix("previews/")}"
                                )
                                )
                    )
                } else {
                    variant
                }

                add(rewrittenVariant)
            }
        }

        return JsonObject(
            entry + ("variants" to rewrittenVariants)
        )
    }

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