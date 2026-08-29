package com.dfcruz.compose.gallery.gradle.internal.layoutlib

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

internal data class LayoutlibShotResult(
    val imagePath: String?,
    val status: String?,
    val brokenClasses: List<String>,
    val missingClasses: List<String>,
    val problems: List<String>,
    val message: String?,
)

internal object LayoutlibResultsParser {

    fun read(file: File): Map<String, LayoutlibShotResult> {
        if (!file.isFile) return emptyMap()
        val root = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }
            .getOrNull() ?: return emptyMap()
        val results = root["screenshotResults"] as? JsonArray ?: return emptyMap()

        return results.mapNotNull { element ->
            val result = element.jsonObject
            val id = result.stringOrNull("previewId") ?: return@mapNotNull null
            val error = result["error"] as? JsonObject
            val brokenClasses = (error?.get("brokenClasses") as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.stringOrNull("className") }
                .orEmpty()
            val missingClasses = (error?.get("missingClasses") as? JsonArray)
                ?.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                        ?: (it as? JsonObject)?.stringOrNull("className")
                }
                .orEmpty()
            val problems = (error?.get("problems") as? JsonArray)
                ?.mapNotNull { problem ->
                    (problem as? JsonObject)?.let {
                        it.stringOrNull("stackTrace")?.lineSequence()?.firstOrNull(String::isNotBlank)
                            ?: it.stringOrNull("html")
                    }
                }
                .orEmpty()
            val message = error?.stringOrNull("message")?.takeIf(String::isNotBlank)
                ?: error?.stringOrNull("stackTrace")?.takeIf(String::isNotBlank)

            id to LayoutlibShotResult(
                imagePath = result.stringOrNull("imagePath"),
                status = error?.stringOrNull("status"),
                brokenClasses = brokenClasses,
                missingClasses = missingClasses,
                problems = problems,
                message = message,
            )
        }.toMap()
    }

    fun isSuccess(result: LayoutlibShotResult?, image: File?): Boolean =
        result != null && (result.status == null || result.status == "SUCCESS") &&
                result.brokenClasses.isEmpty() && result.problems.isEmpty() &&
                result.missingClasses.none { it.endsWith("ComposeViewAdapter") } &&
                image != null && image.isFile && image.length() > 0L

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
