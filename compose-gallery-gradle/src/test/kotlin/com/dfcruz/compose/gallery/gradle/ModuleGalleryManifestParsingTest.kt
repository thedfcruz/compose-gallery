package com.dfcruz.compose.gallery.gradle

import com.dfcruz.compose.gallery.gradle.manifest.GalleryRenderStatus
import com.dfcruz.compose.gallery.gradle.manifest.parseModuleGalleryManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModuleGalleryManifestParsingTest {

    @Test
    fun `parses rendered previews and ignores unknown fields`() {
        val manifest = parseModuleGalleryManifest(
            """
            {
              "version": 1,
              "module": ":sample",
              "ignoredField": true,
              "previews": [
                {
                  "id": "primary-button",
                  "name": "Primary Button",
                  "group": "Buttons",
                  "tags": ["primary"],
                  "simpleName": "PrimaryButtonPreview",
                  "qualifiedName": "com.example.PrimaryButtonPreview",
                  "packageName": "com.example",
                  "fileName": "Buttons.kt",
                  "variants": [
                    {
                      "name": "Default",
                      "status": "SUCCESS",
                      "image": "previews/Buttons/Primary_Button/Default-0.png"
                    },
                    {
                      "name": "Failure",
                      "status": "FAILED",
                      "error": "No image produced"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, manifest.version)
        assertEquals(":sample", manifest.module)
        val preview = manifest.previews.single()
        assertEquals("Primary Button", preview.name)

        val successfulVariant = preview.variants.first()
        assertEquals(GalleryRenderStatus.SUCCESS, successfulVariant.status)
        assertEquals("previews/Buttons/Primary_Button/Default-0.png", successfulVariant.image)
        assertNull(successfulVariant.error)

        val failedVariant = preview.variants.last()
        assertEquals(GalleryRenderStatus.FAILED, failedVariant.status)
        assertNull(failedVariant.image)
        assertEquals("No image produced", failedVariant.error)
    }
}
