package com.dfcruz.compose.gallery.ksp

import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class JvmMethodFqnTest {

    private data class Case(
        val label: String,
        val fileName: String,
        val source: String,
        val expectedFqn: String,
    )

    private val cases = listOf(
        Case(
            label = "top-level function",
            fileName = "HomeScreen.kt",
            source = """
                package com.dfcruz.testapp
                import com.dfcruz.compose.gallery.annotations.Gallery
                import androidx.compose.ui.tooling.preview.Preview

                @Gallery(name = "n", group = "g")
                @Preview
                fun HomeScreenPreview() {}
            """,
            expectedFqn = "com.dfcruz.testapp.HomeScreenKt.HomeScreenPreview",
        ),
        Case(
            label = "member function in a class",
            fileName = "HomeScreen.kt",
            source = """
                package com.dfcruz.testapp
                import com.dfcruz.compose.gallery.annotations.Gallery
                import androidx.compose.ui.tooling.preview.Preview

                class HomeScreen {
                    @Gallery(name = "n", group = "g")
                    @Preview
                    fun preview() {}
                }
            """,
            expectedFqn = "com.dfcruz.testapp.HomeScreen.preview",
        ),
        Case(
            label = "function in a nested class",
            fileName = "HomeScreen.kt",
            source = """
                package com.dfcruz.testapp
                import com.dfcruz.compose.gallery.annotations.Gallery
                import androidx.compose.ui.tooling.preview.Preview

                class Outer {
                    class Inner {
                        @Gallery(name = "n", group = "g")
                        @Preview
                        fun preview() {}
                    }
                }
            """,
            expectedFqn = "com.dfcruz.testapp.Outer\$Inner.preview",
        ),
        Case(
            label = "function in a companion object",
            fileName = "HomeScreen.kt",
            source = """
                package com.dfcruz.testapp
                import com.dfcruz.compose.gallery.annotations.Gallery
                import androidx.compose.ui.tooling.preview.Preview

                class HomeScreen {
                    companion object {
                        @Gallery(name = "n", group = "g")
                        @Preview
                        fun preview() {}
                    }
                }
            """,
            expectedFqn = "com.dfcruz.testapp.HomeScreen\$Companion.preview",
        ),
        Case(
            label = "top-level function with @file:JvmName override",
            fileName = "HomeScreen.kt",
            source = """
                @file:JvmName("Screens")
                package com.dfcruz.testapp
                import com.dfcruz.compose.gallery.annotations.Gallery
                import androidx.compose.ui.tooling.preview.Preview

                @Gallery(name = "n", group = "g")
                @Preview
                fun HomeScreenPreview() {}
            """,
            expectedFqn = "com.dfcruz.testapp.Screens.HomeScreenPreview",
        ),
    )

    @Test
    fun `jvmMethodFqn resolves correct JVM addressing per declaration shape`() {
        cases.forEach { case ->
            val metadata = generateMetadata(
                fileName = case.fileName,
                source = case.source
            )

            assertEquals(
                "[${case.label}] wrong previewMethodQualifiedName",
                case.expectedFqn,
                metadata.previews.first().variants.first().previewMethodQualifiedName,
            )
        }
    }
}