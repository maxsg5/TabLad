package com.github.maxsg5.tablad.panels

import com.github.maxsg5.tablad.ProjectColorAssigner
import com.github.maxsg5.tablad.ProjectResolver
import com.github.maxsg5.tablad.services.TabsByProjectState
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.TreeExpander
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiManager
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.IconUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Collections
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.UIManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeSelectionModel

class TabsPanel(private val project: Project, parent: Disposable) {
    private data class ModuleEntry(val name: String)
    private data class FileEntry(val file: VirtualFile, val moduleName: String)

    private val rootNode = DefaultMutableTreeNode("Root")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree: TabsTree = TabsTree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = TabsCellRenderer()
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    }

    val component: JComponent = JBScrollPane(tree)

    // Expose expand/collapse actions for the tool window title bar.
    private val treeExpander: TreeExpander = DefaultTreeExpander(tree)
    val expandAllAction: AnAction =
        CommonActionsManager.getInstance().createExpandAllAction(treeExpander, tree)
    val collapseAllAction: AnAction =
        CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, tree)

    private val filesWithErrors: MutableSet<VirtualFile> =
        Collections.synchronizedSet(HashSet())

    init {
        seedErrors()
        rebuild()

        val connection = project.messageBus.connect(parent)
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) = onEdt { rebuild() }
            override fun fileClosed(source: FileEditorManager, file: VirtualFile) = onEdt { rebuild() }
            override fun selectionChanged(event: FileEditorManagerEvent) = onEdt { tree.repaint() }
        })
        connection.subscribe(ProblemListener.TOPIC, object : ProblemListener {
            override fun problemsAppeared(file: VirtualFile) {
                if (filesWithErrors.add(file)) onEdt { tree.repaint() }
            }
            override fun problemsDisappeared(file: VirtualFile) {
                if (filesWithErrors.remove(file)) onEdt { tree.repaint() }
            }
        })
        // VCS state changes (git add/modify/revert/commit) → repaint to keep
        // filename colors in sync with Rider's Solution view.
        connection.subscribe(ChangeListListener.TOPIC, object : ChangeListListener {
            override fun changeListUpdateDone() {
                onEdt { tree.repaint() }
            }
        })
        // User changed per-project color overrides in Settings → repaint.
        connection.subscribe(TabsByProjectState.TOPIC, TabsByProjectState.Listener {
            onEdt { tree.repaint() }
        })

        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = handleIfPopup(e)
            override fun mouseReleased(e: MouseEvent) = handleIfPopup(e)
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2 || e.isPopupTrigger) return
                val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                val entry = node.userObject as? FileEntry ?: return
                FileEditorManager.getInstance(project).openFile(entry.file, true)
            }

            private fun handleIfPopup(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val row = tree.getRowForLocation(e.x, e.y)
                if (row < 0) return
                tree.selectionRows = intArrayOf(row)
                val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                // Only show the Solution-view-style menu on file rows; module
                // headers don't have meaningful Solution-Explorer actions.
                if (node.userObject !is FileEntry) return
                showContextMenu(e)
            }
        })
    }

    private fun showContextMenu(e: MouseEvent) {
        val am = ActionManager.getInstance()
        // Mirror the native editor-tab right-click menu (Close, Split, Pin,
        // Copy Path/Reference, Local History, Git, etc.). The actions in this
        // group need EDITOR_WINDOW + FILE_EDITOR data keys, provided by
        // TabsTree.getData below.
        val group = am.getAction("EditorTabPopupMenu") as? ActionGroup ?: return
        val popup = am.createActionPopupMenu("TabsByProjectPopup", group)
        popup.setTargetComponent(tree)
        popup.component.show(e.component, e.x, e.y)
    }

    private fun seedErrors() {
        val wolf = WolfTheProblemSolver.getInstance(project)
        FileEditorManager.getInstance(project).openFiles.forEach { vf ->
            if (wolf.hasSyntaxErrors(vf)) filesWithErrors.add(vf)
        }
    }

    private fun rebuild() {
        val openFiles = FileEditorManager.getInstance(project).openFiles
        val grouped = openFiles.groupBy { ProjectResolver.projectNameFor(it) ?: "(unassigned)" }
            .toSortedMap()
        rootNode.removeAllChildren()
        grouped.forEach { (moduleName, files) ->
            val moduleNode = DefaultMutableTreeNode(ModuleEntry(moduleName))
            files.sortedBy { it.name }.forEach { vf ->
                moduleNode.add(DefaultMutableTreeNode(FileEntry(vf, moduleName)))
            }
            rootNode.add(moduleNode)
        }
        treeModel.reload()
        // Expand every project group by default.
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    /** True if any open file under the given project has reported errors. */
    private fun projectHasErrors(projectName: String): Boolean {
        val snapshot = synchronized(filesWithErrors) { filesWithErrors.toList() }
        return snapshot.any { ProjectResolver.projectNameFor(it) == projectName }
    }

    private fun onEdt(block: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            if (!project.isDisposed) block()
        } else {
            app.invokeLater {
                if (!project.isDisposed) block()
            }
        }
    }

    /**
     * Tree subclass that exposes data keys for the selected file so that the
     * native EditorTabPopupMenu actions (Close, Close Others, Split Right,
     * Pin Tab, Copy Path/Reference, etc.) operate on the right file.
     *
     * Tab-bar actions in particular need EDITOR_WINDOW + FILE_EDITOR — without
     * them, "Close" / "Split" / "Pin Tab" all grey out because they don't know
     * which editor window the tab they're targeting lives in.
     */
    private inner class TabsTree(model: DefaultTreeModel) : Tree(model), DataProvider {
        override fun getData(dataId: String): Any? {
            val node = lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
            val entry = node.userObject as? FileEntry ?: return null
            val vf = entry.file
            return when {
                CommonDataKeys.PROJECT.`is`(dataId) -> project
                CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> vf
                CommonDataKeys.VIRTUAL_FILE_ARRAY.`is`(dataId) -> arrayOf(vf)
                CommonDataKeys.PSI_FILE.`is`(dataId) -> PsiManager.getInstance(project).findFile(vf)
                CommonDataKeys.NAVIGATABLE.`is`(dataId) -> OpenFileDescriptor(project, vf)
                CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId) -> arrayOf(OpenFileDescriptor(project, vf))
                PlatformCoreDataKeys.FILE_EDITOR.`is`(dataId) ->
                    FileEditorManager.getInstance(project).getSelectedEditor(vf)
                EditorWindow.DATA_KEY.`is`(dataId) -> findEditorWindowFor(vf)
                else -> null
            }
        }

        /** Find the EditorWindow currently displaying this file (any split), or fall back to the focused window. */
        private fun findEditorWindowFor(vf: VirtualFile): EditorWindow? {
            val femEx = FileEditorManager.getInstance(project) as? FileEditorManagerEx ?: return null
            return femEx.windows.firstOrNull { window -> window.fileList.contains(vf) }
                ?: femEx.currentWindow
        }
    }

    private inner class TabsCellRenderer : TreeCellRenderer {
        private val outer = JPanel(BorderLayout(4, 0))
        private val stripe = JPanel().apply { preferredSize = Dimension(5, 16); isOpaque = true }
        private val inner = SimpleColoredComponent().apply { isOpaque = false }

        init {
            outer.add(stripe, BorderLayout.WEST)
            outer.add(inner, BorderLayout.CENTER)
        }

        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ): Component {
            inner.clear()
            val node = value as? DefaultMutableTreeNode
            val defaultFg = if (selected) UIManager.getColor("Tree.selectionForeground")
            else UIManager.getColor("Tree.foreground")

            when (val payload = node?.userObject) {
                is ModuleEntry -> {
                    stripe.background = ProjectColorAssigner.colorFor(project, payload.name)
                    inner.icon = null
                    val anyErr = projectHasErrors(payload.name)
                    var style = SimpleTextAttributes.STYLE_BOLD
                    if (anyErr) style = style or SimpleTextAttributes.STYLE_WAVED
                    inner.append(
                        payload.name,
                        SimpleTextAttributes(style, defaultFg, if (anyErr) JBColor.RED else null)
                    )
                }
                is FileEntry -> {
                    stripe.background = ProjectColorAssigner.colorFor(project, payload.moduleName)
                    inner.icon = runCatching { IconUtil.getIcon(payload.file, 0, project) }.getOrNull()

                    val active = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() == payload.file
                    val hasErr = payload.file in filesWithErrors
                    val vcsColor = ChangeListManager.getInstance(project).getStatus(payload.file).color
                    val fg = if (selected) defaultFg else (vcsColor ?: defaultFg)

                    var style = SimpleTextAttributes.STYLE_PLAIN
                    if (active) style = style or SimpleTextAttributes.STYLE_BOLD
                    if (hasErr) style = style or SimpleTextAttributes.STYLE_WAVED

                    inner.append(
                        payload.file.name,
                        SimpleTextAttributes(style, fg, if (hasErr) JBColor.RED else null)
                    )
                }
                else -> {
                    stripe.background = null
                    inner.icon = null
                    inner.append(node?.userObject?.toString() ?: "")
                }
            }

            if (selected) {
                outer.background = UIManager.getColor("Tree.selectionBackground")
                outer.isOpaque = true
            } else {
                outer.background = null
                outer.isOpaque = false
            }
            return outer
        }
    }
}