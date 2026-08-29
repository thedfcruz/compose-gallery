package com.dfcruz.compose.gallery.gradle

import com.dfcruz.compose.gallery.gradle.internal.layoutlib.LayoutlibResultsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LayoutlibResultsParserTest {

    @Test
    fun `parses successful and failed renderer results`() {
        val directory = Files.createTempDirectory("layoutlib-results-test").toFile()
        val resultsFile = directory.resolve("results.json").apply {
            writeText(
                """
                {
                  "screenshotResults": [
                    {
                      "previewId": "success",
                      "imagePath": "success.png",
                      "error": { "status": "SUCCESS" }
                    },
                    {
                      "previewId": "failed",
                      "error": {
                        "status": "FAILED",
                        "brokenClasses": [{ "className": "com.example.Missing" }],
                        "missingClasses": ["androidx.compose.ui.tooling.ComposeViewAdapter"],
                        "problems": [{ "stackTrace": "first line\nsecond line" }]
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )
        }

        val results = LayoutlibResultsParser.read(resultsFile)

        assertEquals(2, results.size)
        assertEquals("success.png", results.getValue("success").imagePath)
        assertEquals(listOf("com.example.Missing"), results.getValue("failed").brokenClasses)
        assertEquals(
            listOf("androidx.compose.ui.tooling.ComposeViewAdapter"),
            results.getValue("failed").missingClasses,
        )
        assertEquals(listOf("first line"), results.getValue("failed").problems)
    }

    @Test
    fun `accepts a valid image only for an unbroken successful result`() {
        val directory = Files.createTempDirectory("layoutlib-success-test").toFile()
        val image = directory.resolve("preview.png").apply { writeText("png") }
        val resultsFile = directory.resolve("results.json").apply {
            writeText(
                """
                {
                  "screenshotResults": [
                    { "previewId": "success", "imagePath": "preview.png", "error": { "status": "SUCCESS" } },
                    {
                      "previewId": "placeholder",
                      "error": {
                        "status": "SUCCESS",
                        "missingClasses": ["androidx.compose.ui.tooling.ComposeViewAdapter"]
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )
        }

        val results = LayoutlibResultsParser.read(resultsFile)

        assertTrue(LayoutlibResultsParser.isSuccess(results.getValue("success"), image))
        assertFalse(LayoutlibResultsParser.isSuccess(results.getValue("placeholder"), image))
    }
}
