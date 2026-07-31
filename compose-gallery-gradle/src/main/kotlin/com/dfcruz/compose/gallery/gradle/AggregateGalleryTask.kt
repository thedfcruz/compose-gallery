package com.dfcruz.compose.gallery.gradle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

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
        val previewsOut = out.resolve("previews").apply { mkdirs() }

        // group name -> list of preview entries belonging to that group
        val byGroup = mutableMapOf<String, MutableList<JsonObject>>()

        dependencyGalleryDirs.files.forEach { moduleGalleryDir ->
            val manifest = moduleGalleryDir.resolve("gallery-module.json")
                .takeIf { it.isFile } ?: return@forEach

            val root = runCatching {
                json.parseToJsonElement(manifest.readText()).jsonObject
            }.getOrNull() ?: return@forEach

            val galleries = root["galleries"] as? JsonArray

            galleries?.forEach { node ->
                val nodeObj = node as? JsonObject ?: return@forEach
                val group = nodeObj.str("group")?.takeIf { it.isNotBlank() } ?: "General"
                val previews = nodeObj["previews"] as? JsonArray ?: return@forEach
                previews.forEach { el ->
                    val entry = el as? JsonObject ?: return@forEach
                    byGroup.getOrPut(group) { mutableListOf() }.add(entry)
                }
            }

            // Copy PNGs regardless of manifest shape/parse outcome
            val modulePreviews = moduleGalleryDir.resolve("previews")
            if (modulePreviews.isDirectory) {
                modulePreviews.walkTopDown()
                    .filter { it.isFile && it.extension == "png" }
                    .forEach { png ->
                        val relative = png.relativeTo(modulePreviews)
                        val dest = previewsOut.resolve(relative)
                        dest.parentFile.mkdirs()
                        png.copyTo(dest, overwrite = true)
                    }
            }
        }

        // Build merged manifest grouped by group name, sorted alphabetically
        // "General" always goes last
        val sortedGroups = byGroup.keys
            .sortedWith(compareBy { if (it == "General") "zzz$it" else it })

        val mergedPreviews = buildJsonArray {
            sortedGroups.forEach { group ->
                byGroup[group]?.forEach { entry ->
                    // Re-stamp the group in case it was null/blank originally
                    add(JsonObject(entry + ("group" to JsonPrimitive(group))))
                }
            }
        }

        val merged = buildJsonObject {
            put("previews", mergedPreviews)
        }

        out.resolve("gallery.json")
            .writeText(json.encodeToString(JsonObject.serializer(), merged))

        val totalPreviews = byGroup.values.sumOf { it.size }
        val totalGroups = byGroup.size
        logger.lifecycle(
            "gallery: aggregated $totalPreviews preview(s) across " +
                    "$totalGroups group(s) from ${dependencyGalleryDirs.files.size} module(s).",
        )
    }
}
