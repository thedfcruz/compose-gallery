package com.dfcruz.compose.compose.gallery.intellij

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager

class GalleryPanel(private val project: Project) : JPanel(BorderLayout()) {

    private var zoomFactor = 1.0
    private val allEntries = mutableListOf<PreviewEntry>()
    private val collapsedGroups = mutableSetOf<String>()

    private val gradleRunner by lazy { GalleryGradleRunner(project) }

    private val galleryDir = File(project.basePath, "build/gallery")
    private val manifestFile
        get() = File(galleryDir, "gallery.json")

    private data class PreviewEntry(
        val file: File?,
        val name: String,
        val group: String?,
        val methodFqn: String?,
        val module: String,
        val status: String,
        val error: String?,
    )

    private val imageContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(8)
        background = UIManager.getColor("Panel.background")
    }

    private val scrollPane = JBScrollPane(imageContainer).apply {
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        border = null
    }

    private val searchField = SearchTextField(false).apply {
        textEditor.emptyText.text = "Filter by name or group…"
    }

    private val statusLabel = JLabel("Loading…").apply {
        foreground = UIManager.getColor("Label.disabledForeground")
        font = font.deriveFont(11f)
    }

    private val refreshButton = JButton(AllIcons.Actions.Refresh).apply {
        toolTipText = "Reload images from disk"
        isBorderPainted = false
        isContentAreaFilled = false
        preferredSize = Dimension(28, 28)
    }

    private val runTaskButton = JButton(AllIcons.Actions.Execute).apply {
        toolTipText = "Run Gradle task to regenerate previews"
        isBorderPainted = false
        isContentAreaFilled = false
        preferredSize = Dimension(28, 28)
    }

    private val moduleDropdown = ComboBox(arrayOf("All")).apply {
        preferredSize = Dimension(140, 28)
        toolTipText = "Filter by module"
    }

    private val groupDropdown = ComboBox(arrayOf("All")).apply {
        preferredSize = Dimension(140, 28)
        toolTipText = "Filter by group"
    }

    private val zoomInButton = JButton(AllIcons.Graph.ZoomIn).apply {
        toolTipText = "Zoom in"
        isBorderPainted = false
        isContentAreaFilled = false
        preferredSize = Dimension(28, 28)
    }

    private val zoomOutButton = JButton(AllIcons.Graph.ZoomOut).apply {
        toolTipText = "Zoom out"
        isBorderPainted = false
        isContentAreaFilled = false
        preferredSize = Dimension(28, 28)
    }

    private val zoomResetButton = JButton(AllIcons.Graph.ActualZoom).apply {
        toolTipText = "Reset zoom"
        isBorderPainted = false
        isContentAreaFilled = false
        preferredSize = Dimension(28, 28)
    }

    init {
        val iconsRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            border = JBUI.Borders.empty(4, 8, 0, 8)
            add(refreshButton)
            add(runTaskButton)
        }

        val searchRow = JPanel(BorderLayout(4, 0)).apply {
            border = JBUI.Borders.empty(4, 8, 2, 8)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                    add(moduleDropdown)
                    add(groupDropdown)
                },
                BorderLayout.WEST
            )
            add(searchField, BorderLayout.CENTER)
            val rowHeight = maxOf(
                moduleDropdown.preferredSize.height,
                groupDropdown.preferredSize.height,
                searchField.preferredSize.height
            )

            listOf(moduleDropdown, groupDropdown, searchField).forEach {
                it.preferredSize = Dimension(it.preferredSize.width, rowHeight)
            }
        }

        val statusRow = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 8, 4, 8)
            add(statusLabel, BorderLayout.WEST)
        }

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(iconsRow)
            add(searchRow)
            add(statusRow)
        }

        val zoomPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 4)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(
                    1, 0, 0, 0,
                    UIManager.getColor("Separator.foreground")
                ),
                JBUI.Borders.empty(2, 8, 4, 8)
            )
            add(zoomOutButton)
            add(zoomResetButton)
            add(zoomInButton)
        }

        add(topPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(zoomPanel, BorderLayout.SOUTH)

        refreshButton.addActionListener { loadImages() }
        runTaskButton.addActionListener {
            statusLabel.text = "Running Gradle task…"
            gradleRunner.generate(
                task = "generateGallery",
                onSuccess = {
                    SwingUtilities.invokeLater {
                        statusLabel.text = "Done — reloading…"
                        loadImages()
                    }
                }, onFailure = { errorMessage ->
                    SwingUtilities.invokeLater {
                        statusLabel.text = "Gradle task failed"
                        JOptionPane.showMessageDialog(
                            this,
                            errorMessage,
                            "Gradle task failed",
                            JOptionPane.ERROR_MESSAGE,
                        )
                    }
                }
            )
        }
        zoomInButton.addActionListener { adjustZoom(+0.25) }
        zoomOutButton.addActionListener { adjustZoom(-0.25) }
        zoomResetButton.addActionListener {
            zoomFactor = 1.0
            rebuildImageCards()
        }

        searchField.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = rebuildImageCards()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = rebuildImageCards()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = rebuildImageCards()
        })

        moduleDropdown.addActionListener { rebuildImageCards() }
        groupDropdown.addActionListener { rebuildImageCards() }

        addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) = rebuildImageCards()
        })

        loadImages()
        SwingUtilities.invokeLater { rebuildImageCards() }
    }

    private fun loadImages() {
        statusLabel.text = "Loading…"
        imageContainer.removeAll()
        allEntries.clear()

        ApplicationManager.getApplication().executeOnPooledThread {
            val entries = parseManifest(manifestFile)

            SwingUtilities.invokeLater {
                allEntries.addAll(entries)

                val groups = allEntries
                    .mapNotNull { it.group }
                    .distinct()
                    .sorted()

                val modules = allEntries
                    .map { it.module }
                    .distinct()
                    .sorted()


                moduleDropdown.removeAllItems()
                moduleDropdown.addItem("All")
                modules.forEach { moduleDropdown.addItem(it) }

                groupDropdown.removeAllItems()
                groupDropdown.addItem("All")
                groups.forEach { groupDropdown.addItem(it) }

                statusLabel.text = if (allEntries.isEmpty()) {
                    "No previews found — click Generate"
                } else {
                    "${allEntries.size} preview(s)"
                }

                rebuildImageCards()
            }
        }
    }

    private fun parseManifest(file: File): List<PreviewEntry> {
        if (!file.isFile) return emptyList()

        return runCatching {
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            val modules = root["modules"] as? JsonArray ?: return emptyList()

            buildList {
                modules.forEach { moduleElement ->
                    val moduleObject = moduleElement.jsonObject

                    val module = (moduleObject["module"] as? JsonPrimitive)
                        ?.content
                        ?.takeIf { it.isNotBlank() }
                        ?: return@forEach

                    val previews = moduleObject["previews"] as? JsonArray
                        ?: return@forEach

                    previews.forEach { previewElement ->
                        val preview = previewElement.jsonObject

                        val name = (preview["name"] as? JsonPrimitive)
                            ?.content
                            ?.takeIf { it.isNotBlank() }
                            ?: return@forEach

                        val group = (preview["group"] as? JsonPrimitive)
                            ?.content
                            ?.takeIf { it.isNotBlank() }

                        val methodFqn = (preview["previewMethodFqn"] as? JsonPrimitive)
                            ?.content
                            ?.takeIf { it.isNotBlank() }

                        val variants = preview["variants"] as? JsonArray
                            ?: return@forEach

                        variants.forEach { variantElement ->
                            val variant = variantElement.jsonObject

                            val variantName = (variant["name"] as? JsonPrimitive)
                                ?.content
                                ?.takeIf { it.isNotBlank() }
                                ?: name

                            val status = (variant["status"] as? JsonPrimitive)
                                ?.content
                                ?: "FAILED"

                            val image = (variant["image"] as? JsonPrimitive)
                                ?.content
                                ?.takeIf { it.isNotBlank() }

                            val error = (variant["error"] as? JsonPrimitive)
                                ?.content
                                ?.takeIf { it.isNotBlank() }

                            add(
                                PreviewEntry(
                                    file = image?.let { File(galleryDir, it) },
                                    name = variantName,
                                    group = group,
                                    methodFqn = methodFqn,
                                    module = module,
                                    status = status,
                                    error = error,
                                )
                            )
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun rebuildImageCards() {
        val query = searchField.text.trim().lowercase()
        val selectedModule = moduleDropdown.selectedItem as? String
        val selectedGroup = groupDropdown.selectedItem as? String

        imageContainer.removeAll()

        val filtered = allEntries.filter { entry ->
            (query.isEmpty() ||
                    entry.name.lowercase().contains(query) ||
                    entry.group?.lowercase()?.contains(query) == true ||
                    entry.module.lowercase().contains(query)) &&
                    (selectedModule == null ||
                            selectedModule == "All" ||
                            entry.module == selectedModule) &&
                    (selectedGroup == null ||
                            selectedGroup == "All" ||
                            entry.group == selectedGroup)
        }

        val byModule = filtered.groupBy { it.module }

        byModule
            .toSortedMap()
            .forEach { (module, entries) ->
                imageContainer.add(buildModuleHeader(module, entries))
                imageContainer.add(Box.createRigidArea(Dimension(0, 8)))

                val byGroup = entries.groupBy { it.group }

                byGroup
                    .toSortedMap(compareBy(nullsFirst()) { it })
                    .forEach { (group, groupEntries) ->
                        if (group != null) {
                            imageContainer.add(
                                buildGroupHeader(group, groupEntries)
                            )
                            imageContainer.add(
                                Box.createRigidArea(Dimension(0, 8))
                            )
                        }

                        if (group == null || group !in collapsedGroups) {
                            groupEntries.forEach { entry ->
                                imageContainer.add(buildCard(entry))
                                imageContainer.add(
                                    Box.createRigidArea(Dimension(0, 16))
                                )
                            }
                        }
                    }
            }

        imageContainer.revalidate()
        imageContainer.repaint()
    }

    private fun buildModuleHeader(
        module: String,
        entries: List<PreviewEntry>,
    ): JPanel {
        return JPanel(BorderLayout()).apply {
            background = UIManager.getColor("Tree.rowBackground")
            border = JBUI.Borders.empty(8, 12)
            maximumSize = Dimension(Int.MAX_VALUE, 40)
            alignmentX = LEFT_ALIGNMENT

            val label = JLabel("$module  (${entries.size})").apply {
                font = font.deriveFont(Font.BOLD, 14f)
            }

            add(label, BorderLayout.WEST)
        }
    }

    private fun buildGroupHeader(group: String, entries: List<PreviewEntry>): JPanel {
        val isCollapsed = group in collapsedGroups
        val arrow = if (isCollapsed) "▶" else "▼"

        return JPanel(BorderLayout()).apply {
            background = UIManager.getColor("Tree.rowBackgroundSelectionColorInactive")
            border = JBUI.Borders.empty(6, 12)
            maximumSize = Dimension(Int.MAX_VALUE, 36)
            alignmentX = LEFT_ALIGNMENT

            val label = JLabel("$arrow  $group  (${entries.size})").apply {
                font = font.deriveFont(Font.BOLD, 12f)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }
            add(label, BorderLayout.WEST)

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (group in collapsedGroups) collapsedGroups.remove(group)
                    else collapsedGroups.add(group)
                    rebuildImageCards()
                }
            })
        }
    }

    private fun buildCard(entry: PreviewEntry): JPanel {

        val image = runCatching { ImageIO.read(entry.file) }.getOrNull()

        val scaledImg = image?.let {
            val width = (it.width * zoomFactor).toInt()
            val height = (it.height * zoomFactor).toInt()

            it.getScaledInstance(width, height, Image.SCALE_SMOOTH)
        }

        val icon = scaledImg?.let { ImageIcon(it) }

        val nameLabel = JLabel(entry.name).apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = UIManager.getColor("Label.disabledForeground")
            alignmentX = LEFT_ALIGNMENT
        }

        val overflowButton = JButton("⋯").apply {
            isBorderPainted = false
            isContentAreaFilled = false
            font = font.deriveFont(Font.BOLD, 16f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(28, 20)
            toolTipText = "Open full-size preview"
            addActionListener { openDetailDialog(entry) }
        }

        val headerRow = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(6)
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 24)
            add(nameLabel, BorderLayout.WEST)
            add(overflowButton, BorderLayout.EAST)
        }

        val imageLabel = JLabel(icon).apply {
            if (icon == null) text = "Could not load image"
            horizontalAlignment = SwingConstants.LEFT
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Click to navigate to source"
        }

        imageLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    entry.methodFqn?.let(::navigateToFunction)
                }
            }
        })

        val card = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = UIManager.getColor("Panel.background")
            border = JBUI.Borders.empty(0, 12)
            alignmentX = LEFT_ALIGNMENT
            add(headerRow)
            add(imageLabel)
        }

        return card
    }

    private fun adjustZoom(delta: Double) {
        zoomFactor = (zoomFactor + delta).coerceIn(0.25, 4.0)
        rebuildImageCards()
    }

    private fun navigateToFunction(fqn: String) {
        val functionName = fqn.substringAfterLast('.')
        val fileName = "${fqn.substringBeforeLast('.').substringAfterLast('.')}.kt"

        ApplicationManager.getApplication().executeOnPooledThread {
            val scope = GlobalSearchScope.projectScope(project)
            val virtualFiles = FilenameIndex.getVirtualFilesByName(
                fileName,
                scope,
            )

            val target = virtualFiles
                .asSequence()
                .mapNotNull { virtualFile ->
                    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                        ?: return@mapNotNull null

                    PsiTreeUtil
                        .findChildrenOfType(psiFile, KtNamedFunction::class.java)
                        .firstOrNull { it.name == functionName }
                }
                .firstOrNull()

            if (target != null) {
                ApplicationManager.getApplication().invokeLater {
                    FileEditorManager.getInstance(project).openFile(
                        target.containingFile.virtualFile,
                        true,
                    )
                    target.navigate(true)
                }
            }
        }
    }

    private fun openDetailDialog(entry: PreviewEntry) {
        val img = runCatching { ImageIO.read(entry.file) }.getOrNull() ?: return
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val maxW = (screenSize.width * 0.8).toInt()
        val scaled = if (img.width > maxW) {
            img.getScaledInstance(maxW, -1, Image.SCALE_SMOOTH)
        } else img

        val dialog = JDialog(
            SwingUtilities.getWindowAncestor(this) as? Frame,
            entry.name,
            true
        )
        val closeButton = JButton("Close").apply {
            addActionListener { dialog.dispose() }
        }
        dialog.contentPane.add(
            JScrollPane(
                JPanel(BorderLayout()).apply {
                    border = JBUI.Borders.empty(16)
                    add(JLabel(ImageIcon(scaled)), BorderLayout.CENTER)
                }
            ).apply { border = null },
            BorderLayout.CENTER
        )
        dialog.contentPane.add(
            JPanel(FlowLayout(FlowLayout.RIGHT)).apply { add(closeButton) },
            BorderLayout.SOUTH
        )
        dialog.pack()
        dialog.setLocationRelativeTo(this)
        dialog.isVisible = true
    }
}