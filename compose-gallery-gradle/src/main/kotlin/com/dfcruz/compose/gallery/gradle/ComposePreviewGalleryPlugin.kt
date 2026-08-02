package com.dfcruz.compose.gallery.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

abstract class ComposePreviewGalleryPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            afterEvaluate {
                val variant = androidVariant(this)
                //validateRuntimeClasspath(this, variant)
                val layoutlib = prepLayoutlib(this)
                wire(this, layoutlib, variant)
            }
        }
    }

    private fun wire(
        project: Project,
        layoutlib: LayoutlibSetup,
        androidVariant: String,
    ) {
        with(project) {
            val galleryDir = layout.buildDirectory.dir("gallery")
            val previewsDir = galleryDir.map { it.dir("previews") }

            val kspManifestFile =
                layout.buildDirectory.file(
                    "generated/ksp/$androidVariant/resources/gallery-metadata.json"
                )

            val render = registerLayoutlibRender(
                project = this,
                variant = androidVariant,
                setup = layoutlib,
                manifestFile = kspManifestFile,
                scratchDir = layout.buildDirectory.dir("preview-render"),
                previewsDir = previewsDir,
            )

            registerWithRootAggregator(
                project = this,
                galleryDir = galleryDir,
                renderTask = render,
            )

            tasks.register("generateGallery") {
                group = "gallery"
                description = "Renders every @Preview component in this module."
                dependsOn(render)
            }
        }
    }

    private fun registerWithRootAggregator(
        project: Project,
        galleryDir: Provider<Directory>,
        renderTask: TaskProvider<LayoutlibRenderTask>,
    ) {
        val root = project.rootProject

        val aggregateTask = root.tasks.findByName("aggregateGallery")
            ?.let {
                root.tasks.named(
                    "aggregateGallery",
                    AggregateGalleryTask::class.java,
                )
            }
            ?: root.tasks.register(
                "aggregateGallery",
                AggregateGalleryTask::class.java,
            ) {
                outputDir.set(
                    root.layout.buildDirectory.dir("gallery")
                )
            }

        aggregateTask.configure {
            dependencyGalleryDirs.from(galleryDir)
            dependsOn(renderTask)
        }

        if (root.tasks.findByName("generateGallery") == null) {
            root.tasks.register("generateGallery") {
                group = "gallery"
                description = "Renders and aggregates all Compose previews."
                dependsOn(aggregateTask)
            }
        }
    }


    /** The Android variant to extract: an explicit `gallery { variant }`, else the first `…DebugKotlin` KSP task's
     *  variant (so flavored projects work unconfigured), else `"debug"`. */
    private fun androidVariant(project: Project): String {
        return project.tasks.names
            .filter { it.startsWith("ksp") && it.endsWith("DebugKotlin") && "Test" !in it }
            .minOrNull()
            ?.removePrefix("ksp")?.removeSuffix("Kotlin")?.replaceFirstChar { it.lowercase() }
            ?: "debug"
    }

    private fun validateRuntimeClasspath(project: Project, variant: String) {
        project.configurations.findByName("${variant}RuntimeClasspath")
            ?: error(
                "gallery: no '${variant}RuntimeClasspath' configuration — set gallery { " +
                        "variant } to a real variant of this module (e.g. \"demoDebug\").",
            )
    }

    private fun prepLayoutlib(project: Project): LayoutlibSetup {
        with(project) {
            // The renderer's own classpath: the standalone preview renderer + the Layoutlib framework classes.
            val renderer = configurations.maybeCreate("galleryLayoutlibRenderer").apply {
                isCanBeConsumed = false
                isCanBeResolved = true
            }
            dependencies.add(
                renderer.name,
                "com.android.tools.compose:compose-preview-renderer:$LAYOUTLIB_RENDERER_VERSION",
            )

            dependencies.add(
                renderer.name,
                "com.android.tools.layoutlib:layoutlib:$LAYOUTLIB_VERSION",
            )
            // The Layoutlib data dir is assembled from the OS-native runtime jar + the framework resources jar.
            val runtimeCfg = configurations.maybeCreate("galleryLayoutlibRuntime").apply {
                isCanBeConsumed = false
                isCanBeResolved = true
            }
            dependencies.add(
                runtimeCfg.name,
                "com.android.tools.layoutlib:layoutlib-runtime:$LAYOUTLIB_VERSION:${layoutlibOsClassifier()}",
            )
            val resourcesCfg = configurations.maybeCreate("galleryLayoutlibResources").apply {
                isCanBeConsumed = false
                isCanBeResolved = true
            }
            dependencies.add(
                resourcesCfg.name,
                "com.android.tools.layoutlib:layoutlib-resources:$LAYOUTLIB_VERSION",
            )
            val prepare =
                tasks.register("prepareGalleryLayoutlib", PrepareLayoutlibTask::class.java) {
                    runtimeJar.from(runtimeCfg)
                    resourcesJar.from(resourcesCfg)
                    layoutlibDir.set(layout.buildDirectory.dir("preview-layoutlib"))
                }

            return LayoutlibSetup(prepare, renderer)
        }
    }

    /** Register + configure a [LayoutlibRenderTask] instance: the shared renderer/prepare from [setup], pointed at
     *  this pipeline's [manifestFile] / scratch [scratchDir] / [previewsDir] / [indexOut], plus the variant's
     *  app/project/R classpath + linked resources (identical for both pipelines — same module/variant). KMP feeds
     *  the consuming app's resources via [wireKmpConsumerResources]. */
    private fun registerLayoutlibRender(
        project: Project,
        variant: String,
        setup: LayoutlibSetup,
        manifestFile: Provider<RegularFile>,
        scratchDir: Provider<Directory>,
        previewsDir: Provider<Directory>,
    ): TaskProvider<LayoutlibRenderTask> {
        with(project) {
            val artifactType = Attribute.of("artifactType", String::class.java)

            val renderTask =
                tasks.register("renderGalleryLayoutlib", LayoutlibRenderTask::class.java) {
                    kspManifest.set(manifestFile)
                    rendererClasspath.from(setup.renderer)
                    layoutlibDir.set(setup.prepare.flatMap { it.layoutlibDir })
                    namespace.set(androidNamespace())
                    apiLevel.set(LAYOUTLIB_API)
                    module.set(project.path)
                    workDir.set(scratchDir)
                    this.previewsDir.set(previewsDir)

                    val kspTaskName = "ksp${variant.replaceFirstChar { it.uppercase() }}Kotlin"
                    dependsOn(setup.prepare)
                    dependsOn(tasks.matching { it.name == kspTaskName })

                    val cap = variant.replaceFirstChar { it.uppercase() }
                    // The module's runtime dependencies, viewed as android-classes-jars (AGP's own unit-test view).
                    appClasspath.from(
                        configurations.getByName("${variant}RuntimeClasspath").incoming
                            .artifactView {
                                attributes.attribute(
                                    artifactType,
                                    "android-classes-jar",
                                )
                            }
                            .files,
                    )
                    // This module's own compiled classes — the location depends on the Kotlin integration: the
                    // `kotlin-android` plugin writes build/tmp/kotlin-classes/<variant>; AGP's built-in Kotlin (AGP 9,
                    // android.builtInKotlin) writes intermediates/built_in_kotlinc/<variant>/compile<V>Kotlin/classes.
                    // Include both (+ javac for Java sources) as SPECIFIC dirs (not a broad build/intermediates tree → no
                    // overlapping-output validation); absent ones contribute nothing. compile<V>Kotlin is a task dep.
                    projectClasspath.from(layout.buildDirectory.dir("tmp/kotlin-classes/$variant"))
                    projectClasspath.from(
                        layout.buildDirectory.dir(
                            "intermediates/built_in_kotlinc/$variant/compile${cap}Kotlin/classes",
                        ),
                    )
                    projectClasspath.from(
                        layout.buildDirectory.dir(
                            "intermediates/javac/$variant/compile${cap}JavaWithJavac/classes",
                        ),
                    )
                    rClassPath.from(
                        layout.buildDirectory.dir("intermediates").map { d ->
                            // The FULL R closure — this module's R AND every dependency's R, incl. androidx (e.g.
                            // androidx.customview.poolingcontainer.R, which a ComposeView-backed preview loads at render). For an
                            // app the AAPT2-linked R is under compile_and_runtime_r_class_jar/<variant> (the app links
                            // everything); for a library the main R is module-only, so the closure lives under <variant>UnitTest.
                            // CRUCIAL: take ONLY the linked `process<…>Resources` R — whose IDs match the unit-test `.ap_` we
                            // feed — NOT the sibling `generate<…>StubRFile` R, a stub with PHANTOM ids. With non-transitive R a
                            // cross-module `R.string.x` is a non-final field resolved at render time; if the stub (listed first)
                            // wins the classloader, the id points at nothing in the `.ap_` → Resources$NotFoundException → a
                            // blank/failed render (this is why feature modules that reference another module's R went blank).
                            fileTree(d.asFile).matching {
                                include(
                                    "**/compile_and_runtime_r_class_jar/$variant/process*Resources/R.jar",
                                    "**/compile_and_runtime_r_class_jar/${variant}UnitTest/process*Resources/R.jar",
                                )
                            }
                        },
                    )
                    // The linked resources (.ap_) AGP produces for unit tests — gives Layoutlib the app's @string/themes/etc.
                    resourceApk.from(
                        layout.buildDirectory.dir("intermediates").map { dir ->
                            fileTree(dir).matching {
                                include(
                                    "**/linked_resources_binary_format/$variant/**/linked-resources-binary-format-*.ap_",
                                    "**/apk_for_local_test/${variant}UnitTest/**/apk-for-local-test.ap_",
                                )
                            }
                        }
                    )
                    // Materialize this variant's classes (transitively R) + the unit-test linked resources before rendering.
                    dependsOn("compile${cap}Kotlin")
                    dependsOn(tasks.matching { it.name == "compile${cap}JavaWithJavac" })
                    dependsOn(tasks.matching { it.name == "package${cap}UnitTestForUnitTest" })
                }
            return renderTask
        }
    }

    /** The module's resource namespace (`android { namespace }`), read reflectively so `compose-nav-graph-gradle` needs no AGP
     *  compile dependency. The renderer uses it to resolve the app's R class. */
    private fun Project.androidNamespace(): String =
        extensions.findByName("android")?.let { android ->
            runCatching {
                android.javaClass.getMethod("getNamespace").invoke(android) as? String
            }.getOrNull()
        } ?: ""

    /** The `com.android.tools.layoutlib:layoutlib-runtime` native classifier for the host running Gradle. */
    private fun layoutlibOsClassifier(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            "mac" in os || "darwin" in os -> if ("aarch64" in arch || "arm" in arch) "mac-arm" else "mac"
            "win" in os -> "win"
            else -> "linux"
        }
    }

    /** The Layoutlib renderer classpath + the prepare task, created once and shared by both render pipelines. */
    private data class LayoutlibSetup(
        val prepare: TaskProvider<PrepareLayoutlibTask>,
        val renderer: Configuration,
    )

    private companion object {
        // Layoutlib backend: the pinned version tuple (renderer ↔ Layoutlib must stay an atomic pair) + the render
        // API level. Maven Layoutlib 16.2.1 ships Android 16 / API 36 (build.prop: ro.build.version.sdk=36).
        const val LAYOUTLIB_VERSION = "16.2.1"
        const val LAYOUTLIB_RENDERER_VERSION = "0.0.1-alpha15"
        const val LAYOUTLIB_API = "36"
        val GALLERY_ATTRIBUTE = Attribute.of("com.dfcruz.gallery", String::class.java)
    }
}