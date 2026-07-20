package com.dfcruz.compose.gallery.ksp

import com.dfcruz.compose.gallery.protocol.GalleryMetadata
import com.dfcruz.compose.gallery.protocol.GalleryPreview
import com.dfcruz.compose.gallery.protocol.PreviewVariant
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class GalleryProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private var invoked = false
    private val json = Json { prettyPrint = true }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()
        invoked = true

        val previews = collectGalleryPreviews(resolver)

        if (previews.isEmpty()) {
            logger.warn("Gallery: no @Gallery-annotated functions found in this compilation")
        } else {
            logger.info("Gallery: generated metadata for ${previews.size} preview(s)")
        }

        writeMetadata(previews)

        return emptyList()
    }

    private fun collectGalleryPreviews(resolver: Resolver): List<GalleryPreview> {
        return collectDirectPreviewVariants(resolver)
    }

    /**
     * Collects preview variants from functions annotated with `@Gallery` that
     * declare `@Preview` annotations directly.
     */
    private fun collectDirectPreviewVariants(resolver: Resolver): List<GalleryPreview> =
        resolver
            .getSymbolsWithAnnotation(GALLERY, true)
            .filterIsInstance<KSFunctionDeclaration>()
            .map(::buildGalleryPreview)
            .toList()


    private fun buildGalleryPreview(fn: KSFunctionDeclaration): GalleryPreview {
        val annotations = fn.annotations.toList()
        val gallery =
            annotations.findQualifiedAnnotation(GALLERY) ?: error("@Gallery annotation expected")

        val variants = annotations
            .resolvePreviewAnnotations()
            .map { it.toPreviewVariant(fn) }

        return GalleryPreview(
            id = stableId(fn),
            name = gallery.getString("name") ?: "",
            group = gallery.getString("group") ?: "",
            tags = (gallery.getArgument("tags") as? List<*>)
                ?.filterIsInstance<String>()
                .orEmpty(),
            simpleName = fn.simpleName.asString(),
            qualifiedName = fn.qualifiedName?.asString().orEmpty(),
            packageName = fn.packageName.asString(),
            fileName = fn.containingFile?.fileName.orEmpty(),
            variants = variants,
        )
    }

    private fun writeMetadata(previews: List<GalleryPreview>) {
        codeGenerator.createNewFile(
            dependencies = Dependencies.ALL_FILES,
            packageName = "",
            fileName = "gallery-metadata",
            extensionName = "json",
        ).use {
            it.write(
                json.encodeToString(
                    GalleryMetadata(
                        version = 1,
                        previews = previews
                    )
                ).encodeToByteArray()
            )
        }
    }

    private fun KSAnnotation.toPreviewVariant(fn: KSFunctionDeclaration): PreviewVariant =
        PreviewVariant(
            previewMethodQualifiedName = jvmMethodFqn(fn),
            name = getString("name") ?: "",
            group = getString("group") ?: "",
            apiLevel = getInt("apiLevel") ?: -1,
            widthDp = getInt("widthDp") ?: -1,
            heightDp = getInt("heightDp") ?: -1,
            locale = getString("locale") ?: "",
            fontScale = getFloat("fontScale") ?: 1f,
            showSystemUi = getBoolean("showSystemUi") ?: false,
            showBackground = getBoolean("showBackground") ?: false,
            backgroundColor = getLong("backgroundColor") ?: 0,
            uiMode = getInt("uiMode") ?: 0,
            device = getString("device") ?: "",
            wallpaper = getInt("wallpaper") ?: 0,
        )

    /**
     * The JVM method FQN of a `@Preview` function, as the standalone Layoutlib renderer (`compose-preview-renderer`)
     * resolves it: it splits on the final `.` into <class>/<method>, so a top-level fun must be addressed via its
     * **file facade** (`HomeScreen.kt` → `…HomeScreenKt`), not its Kotlin FQN (`…HomeScreenPreview`, which would
     * mis-split into class `…HomeScreen` / method `Preview`). A member preview uses its enclosing class FQN.
     */
    private fun jvmMethodFqn(fn: KSFunctionDeclaration): String {
        val file = requireNotNull(fn.containingFile) {
            "Expected ${fn.simpleName.asString()} to belong to a source file."
        }

        val method = fn.simpleName.asString()
        val owner = fn.parentDeclaration as? KSClassDeclaration
        if (owner != null) {
            // A member preview: the renderer splits methodFQN on the last '.' then `loadClass(owner)`, so the owner must
            // be its JVM **binary** name — nested classes / `Companion` are joined by '$', not '.'.
            val nesting = generateSequence(owner) { it.parentDeclaration as? KSClassDeclaration }
                .map { it.simpleName.asString() }.toList().asReversed()
            val pkg = owner.packageName.asString()
            val binaryOwner = (if (pkg.isBlank()) "" else "$pkg.") + nesting.joinToString("\$")
            return "$binaryOwner.$method"
        }

        val pkg = fn.packageName.asString()
        return (if (pkg.isBlank()) "" else "$pkg.") + facadeClassName(file) + "." + method
    }

    /** Kotlin's top-level file facade class name: `@file:JvmName("X")` → `X`, else `<FileName>` sanitized to a
     *  valid identifier, first char upper-cased, with the `Kt` suffix (`home-screen.kt` → `Home_screenKt`). */
    private fun facadeClassName(file: KSFile): String {
        (file.annotations.firstOrNull { it.shortName.asString() == "JvmName" }
            ?.getArgument("name") as? String)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val base = file.fileName.substringBeforeLast('.')
        val sanitized =
            base.map { if (it.isLetterOrDigit() || it == '_') it else '_' }.joinToString("")
        return sanitized.replaceFirstChar { it.uppercaseChar() } + "Kt"
    }

    private fun stableId(fn: KSFunctionDeclaration): String {
        val basis = fn.qualifiedName?.asString()
            ?: "${fn.packageName.asString()}.${fn.containingFile?.fileName}.${fn.simpleName.asString()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(basis.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(12)
    }

    private fun KSAnnotation.getQualifiedName(): String? =
        annotationType.resolve().declaration.qualifiedName?.asString()

    private fun KSAnnotation.getArgument(name: String): Any? =
        arguments.firstOrNull { it.name?.getShortName() == name }?.value

    private fun KSAnnotation.getString(name: String): String? = getArgument(name) as? String
    private fun KSAnnotation.getInt(name: String): Int? = getArgument(name) as? Int
    private fun KSAnnotation.getFloat(name: String): Float? = getArgument(name) as? Float
    private fun KSAnnotation.getLong(name: String): Long? = getArgument(name) as? Long
    private fun KSAnnotation.getBoolean(name: String): Boolean? = getArgument(name) as? Boolean

    private fun Iterable<KSAnnotation>.findQualifiedAnnotation(
        qualifiedName: String
    ): KSAnnotation? = firstOrNull { it.getQualifiedName() == qualifiedName }

    private fun List<KSAnnotation>.resolvePreviewAnnotations(): List<KSAnnotation> =
        this.filter { it.getQualifiedName() == PREVIEW }

    private companion object {
        const val GALLERY = "com.dfcruz.compose.gallery.annotations.Gallery"
        const val PREVIEW = "androidx.compose.ui.tooling.preview.Preview"
    }
}


