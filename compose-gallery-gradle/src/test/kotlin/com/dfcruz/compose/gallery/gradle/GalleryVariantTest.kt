package com.dfcruz.compose.gallery.gradle

import com.dfcruz.compose.gallery.gradle.configuration.GalleryVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GalleryVariantTest {

    @Test
    fun `derives task names and metadata path`() {
        val variant = GalleryVariant("demoDebug")

        assertEquals("kspDemoDebugKotlin", variant.kspTaskName)
        assertEquals("compileDemoDebugKotlin", variant.compileKotlinTaskName)
        assertEquals("compileDemoDebugJavaWithJavac", variant.compileJavaTaskName)
        assertEquals("processDemoDebugResources", variant.processResourcesTaskName)
        assertEquals(
            "generated/ksp/demoDebug/resources/gallery-metadata.json",
            variant.previewConfigurationPath,
        )
    }

    @Test
    fun `rejects a blank variant`() {
        assertThrows(IllegalArgumentException::class.java) {
            GalleryVariant("   ")
        }
    }
}
