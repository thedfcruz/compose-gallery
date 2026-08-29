package com.dfcruz.compose.gallery.gradle

import com.dfcruz.compose.gallery.gradle.manifest.parsePreviewConfiguration
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class PreviewConfigurationParsingTest {

    @Test
    fun `parses preview configuration with nested variants`() {
        val configuration = parsePreviewConfiguration(
            """
            {
              "previews": [
                {
                  "id": "home-preview",
                  "name": "Home",
                  "group": "Screens",
                  "tags": ["home", "primary"],
                  "simpleName": "HomePreview",
                  "qualifiedName": "com.example.HomePreview",
                  "packageName": "com.example",
                  "fileName": "Home.kt",
                  "previewMethodQualifiedName": "com.example.HomeKt.HomePreview",
                  "variants": [
                    {
                      "name": "Dark",
                      "apiLevel": 36,
                      "widthDp": 411,
                      "heightDp": 891,
                      "locale": "pt-PT",
                      "fontScale": 1.5,
                      "showSystemUi": true,
                      "showBackground": true,
                      "backgroundColor": 4294967295,
                      "uiMode": 32,
                      "device": "id:pixel_5",
                      "wallpaper": 1
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, configuration.previews.size)
        val preview = configuration.previews.single()
        assertEquals("home-preview", preview.id)
        assertEquals(listOf("home", "primary"), preview.tags)
        assertEquals("com.example.HomeKt.HomePreview", preview.previewMethodQualifiedName)

        val variant = preview.variants.single()
        assertEquals("Dark", variant.name)
        assertEquals(36, variant.apiLevel)
        assertEquals(411, variant.widthDp)
        assertEquals(1.5f, variant.fontScale)
        assertEquals(4294967295L, variant.backgroundColor)
    }

    @Test
    fun `uses defaults and ignores unknown fields`() {
        val configuration = parsePreviewConfiguration(
            """
            {
              "ignoredRootField": true,
              "previews": [
                {
                  "name": "Minimal",
                  "ignoredPreviewField": "ignored",
                  "variants": [
                    { "ignoredVariantField": 42 }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val preview = configuration.previews.single()
        assertEquals("Minimal", preview.name)
        assertEquals(emptyList<String>(), preview.tags)
        assertEquals("", preview.previewMethodQualifiedName)

        val variant = preview.variants.single()
        assertNull(variant.apiLevel)
        assertEquals(1f, variant.fontScale)
        assertEquals(false, variant.showSystemUi)
        assertEquals(0L, variant.backgroundColor)
    }
}
