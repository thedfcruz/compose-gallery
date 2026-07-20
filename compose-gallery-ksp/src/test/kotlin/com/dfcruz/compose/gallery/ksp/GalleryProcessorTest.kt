package com.dfcruz.compose.gallery.ksp

import com.dfcruz.compose.gallery.protocol.GalleryPreview
import com.dfcruz.compose.gallery.protocol.PreviewVariant
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class GalleryProcessorTest {

    @Test
    fun `generates metadata for a Gallery preview`() {
        val metadata = generateMetadata(
            fileName = "HomeScreen.kt",
            source = """
                package com.dfcruz.testapp

                import androidx.compose.ui.tooling.preview.Preview
                import com.dfcruz.compose.gallery.annotations.Gallery

                @Gallery(name = "Home", group = "screens", tags = ["ui", "smoke"])
                @Preview(name = "default", widthDp = 360, heightDp = 720, showBackground = true)
                fun HomeScreenPreview() {}
            """
        )

        assertEquals(1, metadata.version)
        assertEquals(1, metadata.previews.size)

        val preview = metadata.previews.single()
        assertGalleryPreview(preview, expectedVariants = 1)

        assertPreviewVariant(
            preview.variants.single(),
            expectedName = "default",
            widthDp = 360,
            heightDp = 720,
            showBackground = true,
        )
    }

    @Test
    fun `generates one variant per Preview annotation`() {
        val metadata = generateMetadata(
            fileName = "HomeScreen.kt",
            source = """
                package com.dfcruz.testapp

                import androidx.compose.ui.tooling.preview.Preview
                import com.dfcruz.compose.gallery.annotations.Gallery

                @Gallery(name = "Home", group = "screens", tags = ["ui", "smoke"])
                @Preview(name = "phone")
                @Preview(name = "tablet", widthDp = 720, heightDp = 1280)
                fun HomeScreenPreview() {}
            """
        )

        val preview = metadata.previews.single()

        assertGalleryPreview(preview, expectedVariants = 2)

        assertPreviewVariant(
            preview.variants[0],
            expectedName = "phone",
        )

        assertPreviewVariant(
            preview.variants[1],
            expectedName = "tablet",
            widthDp = 720,
            heightDp = 1280,
        )
    }

    @Test
    fun `generates metadata for multiple Gallery functions`() {
        val metadata = generateMetadata(
            fileName = "Screens.kt",
            source = """
                package com.dfcruz.testapp

                import androidx.compose.ui.tooling.preview.Preview
                import com.dfcruz.compose.gallery.annotations.Gallery

                @Gallery(name = "Home", group = "screens")
                @Preview
                fun HomePreview() {}

                @Gallery(name = "Settings", group = "screens")
                @Preview
                fun SettingsPreview() {}
            """
        )

        assertEquals(2, metadata.previews.size)

        assertEquals("Home", metadata.previews[0].name)
        assertEquals("Settings", metadata.previews[1].name)
    }

    @Test
    fun `ignores Preview functions without Gallery`() {
        val metadata = generateMetadata(
            fileName = "Plain.kt",
            source = """
                package com.dfcruz.testapp

                import androidx.compose.ui.tooling.preview.Preview

                @Preview
                fun HomePreview() {}
            """
        )

        assertTrue(metadata.previews.isEmpty())
    }

    @Test
    fun `creates empty variants when Gallery has no Preview`() {
        val metadata = generateMetadata(
            fileName = "Plain.kt",
            source = """
                package com.dfcruz.testapp

                import com.dfcruz.compose.gallery.annotations.Gallery

                @Gallery(name = "Home", group = "screens")
                fun HomePreview() {}
            """
        )

        assertEquals(1, metadata.previews.size)
        assertTrue(metadata.previews.single().variants.isEmpty())
    }

    @Test
    fun `uses empty tags when Gallery tags are omitted`() {
        val metadata = generateMetadata(
            fileName = "Plain.kt",
            source = """
                package com.dfcruz.testapp

                import androidx.compose.ui.tooling.preview.Preview
                import com.dfcruz.compose.gallery.annotations.Gallery

                @Gallery(name = "Home", group = "screens")
                @Preview
                fun HomePreview() {}
            """
        )

        assertTrue(metadata.previews.single().tags.isEmpty())
    }

    @Test
    fun `produces stable ids`() {
        val first = generateMetadata(
            fileName = "Home.kt",
            source = """
                package com.dfcruz.testapp

                import androidx.compose.ui.tooling.preview.Preview
                import com.dfcruz.compose.gallery.annotations.Gallery

                @Gallery(name = "Home", group = "screens")
                @Preview
                fun HomePreview() {}
            """
        )

        val second = generateMetadata(
            fileName = "Home.kt",
            source = """
                package com.dfcruz.testapp

                import androidx.compose.ui.tooling.preview.Preview
                import com.dfcruz.compose.gallery.annotations.Gallery

                @Gallery(name = "Home", group = "screens")
                @Preview
                fun HomePreview() {}
            """
        )

        assertEquals(
            first.previews.single().id,
            second.previews.single().id,
        )
    }

    private fun assertGalleryPreview(
        preview: GalleryPreview,
        expectedVariants: Int,
    ) {
        assertEquals("Home", preview.name)
        assertEquals("screens", preview.group)
        assertEquals(listOf("ui", "smoke"), preview.tags)
        assertEquals("com.dfcruz.testapp", preview.packageName)
        assertEquals("HomeScreen.kt", preview.fileName)
        assertEquals(expectedVariants, preview.variants.size)
    }

    private fun assertPreviewVariant(
        variant: PreviewVariant,
        expectedName: String,
        widthDp: Int = -1,
        heightDp: Int = -1,
        showBackground: Boolean = false,
    ) {
        assertEquals(expectedName, variant.name)
        assertEquals("", variant.group)
        assertEquals(-1, variant.apiLevel)
        assertEquals(widthDp, variant.widthDp)
        assertEquals(heightDp, variant.heightDp)
        assertEquals("", variant.locale)
        assertEquals(1f, variant.fontScale)
        assertEquals(false, variant.showSystemUi)
        assertEquals(showBackground, variant.showBackground)
        assertEquals(0L, variant.backgroundColor)
        assertEquals(0, variant.uiMode)
        assertEquals("", variant.device)
        assertEquals(-1, variant.wallpaper)
    }
}