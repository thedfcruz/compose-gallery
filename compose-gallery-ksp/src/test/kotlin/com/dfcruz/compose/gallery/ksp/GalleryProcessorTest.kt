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
    fun `generates metadata for Preview meta annotation`() {
        val metadata = generateMetadata(
            fileName = "Screens.kt",
            source = """
                package com.dfcruz.testapp

                import androidx.compose.ui.tooling.preview.Preview
                import com.dfcruz.compose.gallery.annotations.Gallery
                
                @Preview(
                    name = "small font",
                    fontScale = 0.5f
                )
                @Preview(
                    name = "large font",
                    fontScale = 1.5f
                )
                annotation class FontScalePreviews

                @Gallery(name = "Buttons", group = "buttons")
                @FontScalePreviews
                fun ButtonPreview() {}
            """
        )

        val preview = metadata.previews.single()

        assertEquals(1, metadata.previews.size)

        assertEquals("Buttons", metadata.previews[0].name)

        assertPreviewVariant(
            preview.variants[0],
            expectedName = "small font",
            fontScale = 0.5f,
        )

        assertPreviewVariant(
            preview.variants[1],
            expectedName = "large font",
            fontScale = 1.5f,
        )
    }

    @Test
    fun `generates metadata for nested Preview meta annotations`() {
        val metadata = generateMetadata(
            fileName = "Screens.kt",
            source = """
            package com.dfcruz.testapp

            import androidx.compose.ui.tooling.preview.Preview
            import com.dfcruz.compose.gallery.annotations.Gallery

            @Preview(
                name = "phone",
                widthDp = 360
            )
            annotation class PhonePreview

            @PhonePreview
            annotation class AppPreview

            @Gallery(name = "Home", group = "screens")
            @AppPreview
            fun HomePreview() {}
        """
        )

        assertEquals(1, metadata.previews.size)

        val preview = metadata.previews.single()

        assertEquals(1, preview.variants.size)

        assertPreviewVariant(
            preview.variants.single(),
            expectedName = "phone",
            widthDp = 360,
        )
    }

    @Test
    fun `collects previews from multiple nested annotations`() {
        val metadata = generateMetadata(
            fileName = "Screens.kt",
            source = """
            package com.dfcruz.testapp

            import androidx.compose.ui.tooling.preview.Preview
            import com.dfcruz.compose.gallery.annotations.Gallery

            @Preview(name = "phone")
            annotation class PhonePreview

            @Preview(name = "tablet")
            annotation class TabletPreview

            @PhonePreview
            @TabletPreview
            annotation class DevicePreviews

            @Gallery(name = "Home", group = "screens")
            @DevicePreviews
            fun HomePreview() {}
        """
        )

        val variants = metadata.previews.single().variants

        assertEquals(2, variants.size)

        assertEquals(
            setOf("phone", "tablet"),
            variants.map { it.name }.toSet()
        )
    }

    @Test
    fun `ignores cyclic meta annotations`() {
        val metadata = generateMetadata(
            fileName = "Screens.kt",
            source = """
            package com.dfcruz.testapp

            import androidx.compose.ui.tooling.preview.Preview
            import com.dfcruz.compose.gallery.annotations.Gallery

            @LoopB
            annotation class LoopA

            @LoopA
            annotation class LoopB

            @Gallery(name = "Home", group = "screens")
            @LoopA
            fun HomePreview() {}
        """
        )

        assertTrue(metadata.previews.isEmpty())
    }

    @Test
    fun `deduplicates repeated Preview variants`() {
        val metadata = generateMetadata(
            fileName = "Screens.kt",
            source = """
            package com.dfcruz.testapp

            import androidx.compose.ui.tooling.preview.Preview
            import com.dfcruz.compose.gallery.annotations.Gallery

            @Preview(name = "phone")
            annotation class PhonePreview

            @Gallery(name = "Home", group = "screens")
            @Preview(name = "phone")
            @PhonePreview
            fun HomePreview() {}
        """
        )

        assertEquals(
            1,
            metadata.previews.single().variants.size
        )
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
    fun `does not create previews when Gallery has no Preview`() {
        val metadata = generateMetadata(
            fileName = "Plain.kt",
            source = """
                package com.dfcruz.testapp

                import com.dfcruz.compose.gallery.annotations.Gallery

                @Gallery(name = "Home", group = "screens")
                fun HomePreview() {}
            """
        )

        assertEquals(0, metadata.previews.size)
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
        expectedName: String = "Home",
        expectedGroup: String = "screens",
        expectedTags: List<String> = listOf("ui", "smoke"),
        expectedFileName: String = "HomeScreen.kt",
        expectedVariants: Int = preview.variants.size,
    ) {
        assertEquals(expectedName, preview.name)
        assertEquals(expectedGroup, preview.group)
        assertEquals(expectedTags, preview.tags)
        assertEquals("com.dfcruz.testapp", preview.packageName)
        assertEquals(expectedFileName, preview.fileName)
        assertEquals(expectedVariants, preview.variants.size)
    }

    private fun assertPreviewVariant(
        variant: PreviewVariant,
        expectedName: String,
        widthDp: Int = -1,
        heightDp: Int = -1,
        fontScale: Float = 1f,
        showBackground: Boolean = false,
    ) {
        assertEquals(expectedName, variant.name)
        assertEquals("", variant.group)
        assertEquals(-1, variant.apiLevel)
        assertEquals(widthDp, variant.widthDp)
        assertEquals(heightDp, variant.heightDp)
        assertEquals("", variant.locale)
        assertEquals(fontScale, variant.fontScale)
        assertEquals(false, variant.showSystemUi)
        assertEquals(showBackground, variant.showBackground)
        assertEquals(0L, variant.backgroundColor)
        assertEquals(0, variant.uiMode)
        assertEquals("", variant.device)
        assertEquals(-1, variant.wallpaper)
    }
}