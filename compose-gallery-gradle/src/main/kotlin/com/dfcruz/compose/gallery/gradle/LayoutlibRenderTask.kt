package com.dfcruz.compose.gallery.gradle

import com.dfcruz.compose.gallery.gradle.LayoutlibRenderTask.Companion.COMPOSE_VIEW_ADAPTER
import com.dfcruz.compose.gallery.gradle.LayoutlibRenderTask.Companion.RENDER_TIMEOUT_MINUTES
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.concurrent.TimeUnit

abstract class LayoutlibRenderTask : DefaultTask() {

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kspManifest: RegularFileProperty

    /** The unpacked Layoutlib data dir from [LayoutlibRenderTask]; pinned + constant, so tracked by version only. */
    @get:Internal
    abstract val layoutlibDir: DirectoryProperty

    /** `compose-preview-renderer` + the Layoutlib framework classes (`layoutlib`) — the renderer's own classpath. */
    @get:Classpath
    abstract val rendererClasspath: ConfigurableFileCollection

    /** The module's runtime dependencies as `android-classes-jar`s (the same view AGP uses for unit tests). */
    @get:Classpath
    abstract val appClasspath: ConfigurableFileCollection

    /** This module's compiled classes (`kotlin-classes/<variant>`, under `build/tmp`) — tracked, so a code edit
     *  re-renders the thumbnails. */
    @get:Classpath
    abstract val projectClasspath: ConfigurableFileCollection

    /** The module's generated `R.jar`(s). `@Internal` because they live under the shared `build/intermediates`
     *  tree; declaring that as a tracked input trips Gradle's overlapping-output validation when a render is
     *  scheduled alongside the unit tests. Ordering is guaranteed by the task's explicit dependencies instead. */
    @get:Internal
    abstract val rClassPath: ConfigurableFileCollection

    /** The linked resources `.ap_` (from the unit-test resource link) so Layoutlib resolves `@string`/themes/etc.
     *  `@Internal` for the same `build/intermediates` overlap reason as [rClassPath]. */
    @get:Internal
    abstract val resourceApk: ConfigurableFileCollection

    @get:Input
    abstract val namespace: Property<String>

    @get:Input
    abstract val apiLevel: Property<String>

    /** Scratch dir for the generated JSON + raw PNGs + results.json (not the consumed thumbnails). */
    @get:Internal
    abstract val workDir: DirectoryProperty

    @get:OutputDirectory
    abstract val previewsDir: DirectoryProperty

    private data class PreviewShot(
        val group: String?,
        val name: String,
        val methodFqn: String,
        val id: String,
        //val params: List<HPreviewParam>,
        val locale: String?,
        val widthDp: Int?,
        val heightDp: Int?,
        val device: String?,
    )

    //internal data class HPreviewParam(val name: String = "", val provider: String = "")

    @TaskAction
    fun render() {
        check(JavaVersion.current() >= JavaVersion.VERSION_17) {
            "gallery: the device-free Layoutlib renderer needs JDK 17+ to run Gradle " +
                    "(current: ${JavaVersion.current()}). " +
                    "Point your Gradle JVM (org.gradle.java.home, or a Java toolchain) at 17 or newer."
        }

        val thumbs = previewsDir.get().asFile.apply { mkdirs() }
        // This render is authoritative for the thumbnails — drop stale PNGs (renamed/removed previews) so they don't
        // linger after a renderer rename/move.
        thumbs.listFiles()?.forEach { if (it.name.endsWith(".png")) it.delete() }

        val shots = buildPreviewShots()
        // Shots should be the list of Previews to render
        if (shots.isEmpty()) {
            logger.lifecycle("gallery: no @GalleryPreview to render (layoutlib).")
            return
        }

        logger.info("gallery: running number of shots:${shots.size}")

        if (resourceApk.files.none { it.isFile }) {
            logger.warn(
                "gallery: no unit-test linked resources (.ap_) found — previews " +
                        "using @string/@drawable/themes may render blank. Enable " +
                        "android.testOptions.unitTests.isIncludeAndroidResources = true.",
            )
        }
        if (namespace.get().isBlank()) {
            logger.warn(
                "gallery: could not resolve this module's android namespace — its own R resources may not render.",
            )
        }

        val work = workDir.get().asFile.apply { mkdirs() }
        val pngDir = work.resolve("png").apply { mkdirs() }
        val resultsFile = work.resolve("results.json").apply { delete() }

        val input = buildJsonObject {
            put("fontsPath", "")
            put("layoutlibPath", layoutlibDir.get().asFile.absolutePath)
            put("outputFolder", pngDir.absolutePath)
            put("metaDataFolder", work.resolve("meta").absolutePath)
            putJsonArray("classPath") { appClasspath.files.forEach { add(it.absolutePath) } }
            putJsonArray("projectClassPath") {
                (projectClasspath.files + rClassPath.files).forEach { add(it.absolutePath) }
            }
            put("namespace", namespace.get())
            put("resourceApkPath", resourceApk.files.firstOrNull { it.isFile }?.absolutePath ?: "")
            put("resultsFilePath", resultsFile.absolutePath)
            putJsonArray("screenshots") {
                shots.forEach { s ->
                    addJsonObject {
                        put("methodFQN", s.methodFqn)
                        /*
                        // @PreviewParameter args: the renderer instantiates the composable's sample value from each provider.
                        // limit=1 ⇒ render just the first value (one thumbnail, not one per provider element).
                        putJsonArray("methodParams") {
                            s.params.forEach { p ->
                                addJsonObject {
                                    put("provider", p.provider)
                                    put("name", p.name)
                                    put("limit", "1")
                                }
                            }
                        }

                         */
                        // A default phone device so EVERY preview has a bounded canvas. Without it, a plain `@Preview` (no
                        // device) on an unsized lazy layout (LazyColumn/Grid) measures to 0 → a 1×1 blank thumbnail; a
                        // `@DevicePreviews`-annotated preview already carries its own device and renders fine either way.
                        putJsonObject("previewParams") {
                            put("apiLevel", apiLevel.get())
                            // Honor @Preview sizing (#13): an explicit `device` passes through; otherwise widthDp/heightDp
                            // synthesize a device spec (an unset dimension keeps the default 411/891 canvas). The fixed phone
                            // default (above) still applies when the preview declares no size, so unsized lazy layouts stay bounded.
                            put(
                                "device",
                                s.device
                                    ?: "spec:width=${s.widthDp ?: 411}dp,height=${s.heightDp ?: 891}dp,dpi=420",
                            )
                            // The @Preview(locale = …) qualifier, extracted by KSP (a multipreview's meta-annotation isn't
                            // visible to the renderer itself, so it must be passed explicitly).
                            s.locale?.let { put("locale", it) }
                        }
                        put("previewId", s.id)
                    }
                }
            }
        }
        val inputFile = work.resolve("input.json")
        inputFile.writeText(Json.encodeToString(JsonObject.serializer(), input))

        // Run the renderer as a child process. It keeps its JVM alive after finishing (non-daemon IntelliJ/Layoutlib
        // threads never return from main), so blocking on exit would stall the build for minutes. Instead we poll
        // results.json — the renderer's true completion signal, one entry per previewId — and terminate the process
        // the moment every preview is in. A hard cap bounds a genuinely stuck render. (`--enable-native-access` is
        // valid on JDK 17+ and silences the Layoutlib native-memory warning.)
        val javaBin = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
        val cp = rendererClasspath.files.joinToString(File.pathSeparator) { it.absolutePath }
        val command =
            listOf(
                javaBin,
                "--enable-native-access=ALL-UNNAMED",
                "-cp",
                cp,
                "com.android.tools.render.common.MainKt",
                inputFile.absolutePath,
            )
        logger.lifecycle("gallery: layoutlib rendering ${shots.size} preview(s)…")
        val started = System.currentTimeMillis()
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(work.resolve("renderer.log"))
            .start()
        val wantedIds = shots.map { it.id }.toSet()
        var exitedOnOwn = false
        var stalled = false
        var landed = 0
        var lastProgressAt = started
        while (System.currentTimeMillis() - started < RENDER_TIMEOUT_MINUTES * 60_000L) {
            if (process.waitFor(2, TimeUnit.SECONDS)) {
                exitedOnOwn = true
                break
            }
            val results = if (resultsFile.isFile) readLayoutlibResults(resultsFile) else emptyMap()
            if (results.keys.containsAll(wantedIds)) break
            // Stall guard: a Compose-Multiplatform preview Layoutlib can't draw hangs the renderer mid-preview, so
            // results.json stops growing. If no NEW preview has landed for RENDER_STALL_SECONDS, give up (in AUTO the
            // Robolectric pass renders these) instead of burning the full hard cap on a render that will never finish.
            if (results.size > landed) {
                landed = results.size
                lastProgressAt = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - lastProgressAt > RENDER_STALL_SECONDS * 1000L) {
                stalled = true
                break
            }
        }
        if (process.isAlive) process.destroyForcibly()
        val elapsed = (System.currentTimeMillis() - started) / 1000
        // The renderer writes results.json atomically at the very end then lingers (non-daemon threads), so it is
        // almost always force-killed above; report that honestly rather than a meaningless "exit=0".
        val exitInfo = when {
            exitedOnOwn -> "exit=${
                runCatching { process.exitValue() }.getOrDefault(-1)
            }"

            stalled -> "killed after stalling ${RENDER_STALL_SECONDS}s with no new preview"

            else -> "killed after completion"
        }

        val byId = readLayoutlibResults(resultsFile)
        var ok = 0
        var failed = 0
        shots.forEach { s ->
            val res = byId[s.id]
            val img = res?.imagePath?.let { pngDir.resolve(it) }
            val success = isLayoutlibSuccess(res, img)
            if (success && img != null) {
                val dest = if (s.group.isNullOrEmpty()) {
                    "${s.name}.png"
                } else {
                    "${s.group}/${s.name}.png"
                }
                img.copyTo(thumbs.resolve(dest), overwrite = true)
                ok++
            } else {
                failed++
                val why = when {
                    res == null -> "no result"

                    res.brokenClasses.isNotEmpty() ->
                        "missing classes on the render classpath — ${res.brokenClasses.joinToString()}"

                    res.missingClasses.any { it.endsWith("ComposeViewAdapter") } ->
                        "the preview host '$COMPOSE_VIEW_ADAPTER' is-- missing from the render classpath"

                    res.missingClasses.isNotEmpty() ->
                        "missing classes on the render classpath — ${res.missingClasses.joinToString()}"


                    res.problems.isNotEmpty() -> "render problem — ${res.problems.first()}"

                    res.message != null -> res.message

                    res.status != null && res.status != "SUCCESS" -> res.status

                    else -> "no image produced"
                }
                logger.warn("gallery: layoutlib render failed for '${s.name}' (${s.methodFqn}): $why")
            }
        }

        if (ok == 0) {
            logger.warn(
                "gallery: layoutlib rendered 0/${shots.size} thumbnail(s) in ${elapsed}s ($exitInfo). " +
                        "See ${resultsFile.absolutePath} and ${work.resolve("renderer.log")}.",
            )
        } else {
            logger.lifecycle(
                "gallery: layoutlib rendered $ok/${shots.size} thumbnail(s) in ${elapsed}s" +
                        (if (failed > 0) " ($failed failed)" else "") + ".",
            )
        }

        // Copy KSP manifest into gallery dir so aggregator finds it alongside the PNGs
        val manifestDest = previewsDir.get().asFile.parentFile.resolve("gallery-module.json")
        val manifestSource = kspManifest.orNull?.asFile
        if (manifestSource != null && manifestSource.isFile) {
            manifestSource.copyTo(manifestDest, overwrite = true)
        } else {
            manifestDest.writeText("""{"previews":[]}""")
        }
    }

    private companion object {
        /** Hard cap on a single render run — bounds a genuinely stuck renderer (completion is detected far sooner). */
        const val RENDER_TIMEOUT_MINUTES: Int = 10

        /** Give up if no new preview lands for this long. Layoutlib that can't draw a Compose-Multiplatform screen hangs
         *  mid-preview; without this an all-CMP app burns the full [RENDER_TIMEOUT_MINUTES] before AUTO falls back to
         *  Robolectric. Generous enough to clear renderer startup plus the slowest legitimate single preview. */
        const val RENDER_STALL_SECONDS: Int = 60

        /** The renderer's preview host. When it isn't on the render classpath the renderer emits a gray
         *  "…ComposeViewAdapter" placeholder yet still reports `status:SUCCESS` (with the class in `missingClasses`), so it
         *  must be detected explicitly. Absent on KMP modules when Compose `ui-tooling` isn't on the render classpath. */
        const val COMPOSE_VIEW_ADAPTER: String = "androidx.compose.ui.tooling.ComposeViewAdapter"
    }

    /**
     * One entry of the Layoutlib renderer's `results.json` (`screenshotResults[]`), keyed by `previewId`. The
     * single source of truth for "which previews Layoutlib failed" — shared by [LayoutlibRenderTask] (which copies
     * the successful PNGs) and the `prepareNavGraphRobolectricRenderList` action (which re-renders the failures under
     * Robolectric in `AUTO` mode).
     */
    internal data class ShotResult(
        val imagePath: String?,
        val status: String?,
        val brokenClasses: List<String>,
        val missingClasses: List<String>,
        val problems: List<String>,
        val message: String?,
    )

    internal fun isLayoutlibSuccess(result: ShotResult?, image: File?): Boolean =
        result != null && (result.status == null || result.status == "SUCCESS") &&
                result.brokenClasses.isEmpty() && result.problems.isEmpty() &&
                result.missingClasses.none { it.endsWith("ComposeViewAdapter") } &&
                image != null && image.isFile && image.length() > 0L


    /**
     * Parse the renderer's `results.json` (`screenshotResults[]`) → `previewId` → image + render status. The `error`
     * object is present even on success (status SUCCESS); `brokenClasses` lists classes Layoutlib failed to load
     * (⇒ an error placeholder, not a real render), `missingClasses` lists classes the renderer couldn't find at all
     * (the preview host [COMPOSE_VIEW_ADAPTER] lands here), and `problems` carries non-fatal-looking layout/measure
     * failures.
     */
    internal fun readLayoutlibResults(file: File): Map<String, ShotResult> {
        if (!file.isFile) return emptyMap()
        val root = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }
            .getOrNull() ?: return emptyMap()
        val arr = root["screenshotResults"] as? JsonArray ?: return emptyMap()
        return arr.mapNotNull { el ->
            val o = el.jsonObject

            val id = o.str("previewId") ?: return@mapNotNull null
            val err = o["error"] as? JsonObject
            val broken =
                (err?.get("brokenClasses") as? JsonArray)?.mapNotNull {
                    (it as? JsonObject)?.str("className")
                }
                    ?: emptyList()
            // The renderer reports an absent class (incl. the preview host ComposeViewAdapter) here — as bare strings, NOT
            // in `brokenClasses` — and still reports SUCCESS with a placeholder image, so this field must be parsed too.
            val missing =
                (err?.get("missingClasses") as? JsonArray)?.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull ?: (it as? JsonObject)?.str("className")
                }
                    ?: emptyList()
            val problems = (err?.get("problems") as? JsonArray)?.mapNotNull { p ->
                (p as? JsonObject)?.let {
                    it.str("stackTrace")?.lineSequence()?.firstOrNull()
                        ?.takeIf { l -> l.isNotBlank() }
                        ?: it.str("html")
                }
            } ?: emptyList()
            val message =
                err?.str("message")?.takeIf { it.isNotBlank() }
                    ?: err?.str("stackTrace")?.takeIf { it.isNotBlank() }
            id to ShotResult(
                o.str("imagePath"),
                err?.str("status"),
                broken,
                missing,
                problems,
                message,
            )
        }.toMap()
    }

    private fun buildPreviewShots(): List<PreviewShot> {
        val manifestFile = kspManifest.orNull?.asFile?.takeIf { it.isFile } ?: return emptyList()
        val previewConfiguration = parsePreviewConfiguration(manifestFile.readText())

        return buildList {
            var i = 0

            previewConfiguration.previews.forEach { preview ->
                preview.variants.forEach { variant ->
                    val method = variant.previewMethodQualifiedName

                    if (method.isNullOrBlank()) {
                        logger.warn(
                            "gallery: @Gallery '${preview.name}' has no JVM methodFqn (regenerate KSP) — skipping."
                        )
                    } else {
                        add(
                            PreviewShot(
                                group = preview.group,
                                name = (variant.name.ifEmpty { preview.name.ifEmpty { preview.simpleName } }) + i,
                                methodFqn = method,
                                id = preview.id.ifBlank { "prev${i++}" },
                                locale = variant.locale.takeIf { it.isNotEmpty() },
                                widthDp = variant.widthDp?.takeIf { it > 0f },
                                heightDp = variant.heightDp?.takeIf { it > 0f },
                                device = variant.device.takeIf { it.isNotEmpty() },
                            )
                        )
                    }
                }
            }
        }
    }
}



