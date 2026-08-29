package com.dfcruz.compose.gallery.gradle

import com.dfcruz.compose.gallery.gradle.internal.aggregation.GalleryManifestTransformer
import com.dfcruz.compose.gallery.gradle.manifest.GalleryPreview
import com.dfcruz.compose.gallery.gradle.manifest.GalleryRenderStatus
import com.dfcruz.compose.gallery.gradle.manifest.ModuleGalleryManifest
import com.dfcruz.compose.gallery.gradle.manifest.RenderedPreviewVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryManifestTransformerTest {

    @Test
    fun `creates stable directory names from Gradle module paths`() {
        assertEquals("sample", GalleryManifestTransformer.moduleDirectory(":sample"))
        assertEquals("feature_profile", GalleryManifestTransformer.moduleDirectory(":feature:profile"))
        assertEquals("my_module", GalleryManifestTransformer.moduleDirectory(":my module"))
        assertEquals("root", GalleryManifestTransformer.moduleDirectory(":"))
    }

    @Test
    fun `rewrites rendered image paths into the module directory`() {
        val manifest = ModuleGalleryManifest(
            module = ":feature:profile",
            previews = listOf(
                GalleryPreview(
                    id = "profile",
                    name = "Profile",
                    group = "Screens",
                    tags = emptyList(),
                    simpleName = "ProfilePreview",
                    qualifiedName = "com.example.ProfilePreview",
                    packageName = "com.example",
                    fileName = "Profile.kt",
                    variants = listOf(
                        RenderedPreviewVariant(
                            name = "Default",
                            status = GalleryRenderStatus.SUCCESS,
                            image = "previews/Screens/Profile/Default-0.png",
                        ),
                        RenderedPreviewVariant(
                            name = "Failure",
                            status = GalleryRenderStatus.FAILED,
                            error = "No render result",
                        ),
                    ),
                ),
            ),
        )

        val rewritten = GalleryManifestTransformer.rewriteImagePaths(
            manifest = manifest,
            moduleDirectory = GalleryManifestTransformer.moduleDirectory(manifest.module),
        )

        val successfulVariant = rewritten.previews.single().variants.first()
        assertEquals(
            "previews/feature_profile/Screens/Profile/Default-0.png",
            successfulVariant.image,
        )
        assertNull(rewritten.previews.single().variants.last().image)
    }
}
