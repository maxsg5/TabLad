package com.github.maxsg5.tablad

import com.github.maxsg5.tablad.services.TabsByProjectState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorPanel
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class Config(private val project: Project) : Configurable {
    private val rows = LinkedHashMap<String, ColorPanel>()

    override fun getDisplayName(): String = "Tabs by Project"

    override fun createComponent(): JComponent {
        rows.clear()
        val state = TabsByProjectState.getInstance(project)
        val knownProjects = collectKnownProjects().sorted()

        val main = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(4)
        }

        // Header
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2
        main.add(
            JLabel("<html>Override the auto-assigned color per .NET project. " +
                    "<br>Clear a swatch to revert that project to its hashed default.</html>"),
            gbc
        )

        if (knownProjects.isEmpty()) {
            gbc.gridy++
            main.add(
                JLabel("<html><i>No projects discovered yet — open any file from a project " +
                        "to make it appear here.</i></html>").apply { foreground = JBColor.GRAY },
                gbc
            )
        } else {
            gbc.gridwidth = 1
            knownProjects.forEachIndexed { i, projectName ->
                gbc.gridx = 0; gbc.gridy = i + 1; gbc.weightx = 0.0
                main.add(JLabel(projectName), gbc)

                gbc.gridx = 1; gbc.weightx = 1.0
                val cp = ColorPanel()
                cp.selectedColor = state.getOverride(projectName)
                rows[projectName] = cp
                main.add(cp, gbc)
            }
        }

        // Reset button
        gbc.gridx = 0; gbc.gridy = (knownProjects.size + 2); gbc.gridwidth = 2; gbc.weightx = 0.0
        main.add(
            JButton("Reset all to auto").apply {
                addActionListener { rows.values.forEach { it.selectedColor = null } }
            },
            gbc
        )

        // Push everything to the top
        gbc.gridy++; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
        main.add(Box.createVerticalGlue(), gbc)

        return main
    }

    override fun isModified(): Boolean {
        val state = TabsByProjectState.getInstance(project)
        return rows.any { (projectName, panel) ->
            panel.selectedColor != state.getOverride(projectName)
        }
    }

    override fun apply() {
        val state = TabsByProjectState.getInstance(project)
        rows.forEach { (projectName, panel) ->
            state.setOverride(projectName, panel.selectedColor)
        }
        ProjectColorAssigner.invalidateAutoCache()
        // Notify the tool window to repaint.
        project.messageBus.syncPublisher(TabsByProjectState.TOPIC).overridesChanged()
    }

    override fun reset() {
        val state = TabsByProjectState.getInstance(project)
        rows.forEach { (projectName, panel) ->
            panel.selectedColor = state.getOverride(projectName)
        }
    }

    override fun disposeUIResources() {
        rows.clear()
    }

    /** Union of: projects of currently-open files + projects that already have overrides stored. */
    private fun collectKnownProjects(): Set<String> {
        val fromOpenFiles = FileEditorManager.getInstance(project).openFiles
            .mapNotNull { ProjectResolver.projectNameFor(it) }
            .toSet()
        val fromOverrides = TabsByProjectState.getInstance(project).knownProjects()
        return fromOpenFiles + fromOverrides
    }
}