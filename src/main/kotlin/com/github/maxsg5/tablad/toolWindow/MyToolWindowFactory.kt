package com.github.maxsg5.tablad.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.github.maxsg5.tablad.MyBundle
import com.github.maxsg5.tablad.panels.TabsPanel
import com.github.maxsg5.tablad.services.MyProjectService
import javax.swing.JButton


class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TabsPanel(project, toolWindow.disposable)
        val content = ContentFactory.getInstance().createContent(panel.component, null, false)
        toolWindow.contentManager.addContent(content)
        // Title-bar buttons: standard IntelliJ Expand-All / Collapse-All icons,
        // wired to the tree's TreeExpander.
        toolWindow.setTitleActions(listOf(panel.expandAllAction, panel.collapseAllAction))
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {

        private val service = toolWindow.project.service<MyProjectService>()

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            val label = JBLabel(MyBundle["randomLabel", "?"])

            add(label)
            add(JButton(MyBundle["shuffle"]).apply {
                addActionListener {
                    label.text = MyBundle["randomLabel", service.getRandomNumber()]
                }
            })
        }
    }
}
