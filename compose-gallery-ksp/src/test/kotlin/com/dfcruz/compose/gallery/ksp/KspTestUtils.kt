package com.dfcruz.compose.gallery.ksp

import com.dfcruz.compose.gallery.ksp.model.GalleryMetadata
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

@OptIn(ExperimentalCompilerApi::class)
internal fun generateMetadata(
    fileName: String,
    source: String,
    options: Map<String, String> = emptyMap(),
): GalleryMetadata {
    val compilation = KotlinCompilation().apply {
        sources = listOf(SourceFile.kotlin(fileName, source.trimIndent()))
        inheritClassPath = true
        messageOutputStream = System.out
        configureKsp {
            symbolProcessorProviders.addAll(listOf(GalleryProcessorProvider()))
            processorOptions.putAll(options)
        }
    }
    val result = compilation.compile()
    assertEquals(
        "Compilation failed:\n${result.messages}",
        KotlinCompilation.ExitCode.OK,
        result.exitCode,
    )

    val generated = compilation.kspSourcesDir
        .resolve("resources")
        .walkTopDown()
        .firstOrNull { it.name == "gallery-metadata.json" }

    assertNotNull("gallery-metadata.json was not generated", generated)

    return Json.decodeFromString(GalleryMetadata.serializer(), generated!!.readText())
}