package com.dfcruz.compose.compose.gallery.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.io.File
import javax.swing.SwingUtilities

class GalleryGradleRunner(
    private val project: Project,
) {

    fun generate(
        task: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching {
                val projectPath = project.basePath
                    ?: error("Project base path is unavailable")

                val gradlew = File(projectPath, "gradlew")
                val gradle = if (gradlew.exists()) {
                    gradlew.absolutePath
                } else {
                    "gradle"
                }
                val command = listOf(gradle, task)

                val process = ProcessBuilder(command)
                    .directory(File(projectPath))
                    .redirectErrorStream(true)
                    .start()

                val output = process.inputStream
                    .bufferedReader()
                    .readText()

                val exitCode = process.waitFor()

                SwingUtilities.invokeLater {
                    if (exitCode == 0) {
                        onSuccess()
                    } else {
                        onFailure(
                            "Gradle task failed (exit $exitCode)\n\n" +
                                    output.takeLast(1000)
                        )
                    }
                }
            }.onFailure { error ->
                SwingUtilities.invokeLater {
                    onFailure("Error: ${error.message}")
                }
            }
        }
    }
}