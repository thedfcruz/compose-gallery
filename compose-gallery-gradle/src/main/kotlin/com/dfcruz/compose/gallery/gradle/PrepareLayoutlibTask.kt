package com.dfcruz.compose.gallery.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Assembles the Layoutlib **data directory** the standalone renderer points at (`layoutlibPath`) — entirely
 * inside the plugin, so a consumer only applies `com.github.skydoves.navgraph` + adds `@NavPreview`:
 *
 *  1. unpack the OS-specific `com.android.tools.layoutlib:layoutlib-runtime:<version>:<os>` jar, which ships
 *     `data/` at its top level (native render libs, fonts, icu, hyphenation, `build.prop` → `ro.build.version.sdk=36`);
 *  2. inject the framework resources — `com.android.tools.layoutlib:layoutlib-resources:<version>` copied
 *     verbatim as `data/framework_res.jar` (the runtime jar deliberately omits it so the per-OS native jar and
 *     the OS-independent resources can version + cache separately).
 *
 * Pinned + cached: the inputs are immutable published artifacts, so after the first run Gradle's up-to-date
 * check skips it. Not `@CacheableTask` — the ~80 MB unpacked tree shouldn't bloat a shared build cache.
 */
abstract class PrepareLayoutlibTask : DefaultTask() {

    /** The OS-classifier `layoutlib-runtime` jar (`mac-arm`/`mac`/`linux`/`win`) — unpacked to [layoutlibDir]. */
    @get:Classpath
    abstract val runtimeJar: ConfigurableFileCollection

    /** The `layoutlib-resources` jar — copied verbatim to `data/framework_res.jar`. */
    @get:Classpath
    abstract val resourcesJar: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val layoutlibDir: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun prepare() {
        val out = layoutlibDir.get().asFile
        val runtime = runtimeJar.files.firstOrNull { it.name.endsWith(".jar") }
            ?: error("gallery: no layoutlib-runtime jar resolved (com.android.tools.layoutlib:layoutlib-runtime:<os>).")
        val resources = resourcesJar.files.firstOrNull { it.name.endsWith(".jar") }
            ?: error("gallery: no layoutlib-resources jar resolved (com.android.tools.layoutlib:layoutlib-resources).")

        // Clean + unpack the runtime jar (yields data/ at the top level), then drop in the framework resources.
        // (`kotlin-dsl` enables SAM-with-receiver for org.gradle.api.Action, so these configure by receiver.)
        fs.delete { delete(out) }
        fs.copy {
            from(archives.zipTree(runtime))
            into(out)
        }
        fs.copy {
            from(resources)
            into(out.resolve("data"))
            rename { "framework_res.jar" }
        }
        logger.lifecycle("gallery: prepared Layoutlib data at ${out.absolutePath}")
    }

}