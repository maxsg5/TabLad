package com.github.maxsg5.tablad

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tabs.JBTabsEx
import com.intellij.ui.tabs.TabInfo

/**
 * Sorts native Rider editor tabs by owning .NET project, then by filename
 * within each project. Effect: tabs from the same project sit next to each
 * other in the native tab bar, matching the grouping in the TabLad side panel.
 *
 * Triggered on every fileOpened. Walks each split's EditorWindow → its
 * EditorTabbedContainer → its JBTabs and calls JBTabs.sortTabs() with the
 * project-grouping Comparator.
 *
 * Stability caveat: reaches through `EditorWindow.tabbedPane.tabs`. The
 * `tabbedPane` property is on `EditorWindow` which lives in the platform's
 * `@ApiStatus.Internal` `impl` package. JetBrains may refactor it between
 * Rider releases. Every access is wrapped in runCatching so a breaking change
 * downgrades to "tabs stay in default chronological order" — never a crash.
 *
 * If you see "TabLad: native tab sort failed" in the IDE log, the access
 * path needs patching; the rest of the plugin keeps working.
 */
class NativeTabSorter : ProjectActivity {
    override suspend fun execute(project: Project) {
        sortAllWindows(project)
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    scheduleResort(project)
                }
            }
        )
    }

    private fun scheduleResort(project: Project) {
        // Defer to next EDT tick so we don't fight Rider mid-update during
        // the same fileOpened handler chain.
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) sortAllWindows(project)
        }
    }

    private fun sortAllWindows(project: Project) {
        runCatching {
            val femEx = FileEditorManager.getInstance(project) as? FileEditorManagerEx ?: return
            femEx.windows.forEach { window -> sortWindow(window) }
        }.onFailure {
            thisLogger().warn("TabLad: native tab sort failed (internal API may have shifted)", it)
        }
    }

    private fun sortWindow(window: EditorWindow) {
        runCatching {
            // sortTabs is on JBTabsEx, not the base JBTabs interface.
            (window.tabbedPane.tabs as? JBTabsEx)?.sortTabs(TAB_COMPARATOR)
        }
    }

    companion object {
        private val TAB_COMPARATOR = Comparator<TabInfo> { a, b ->
            val fa = a.`object` as? VirtualFile
            val fb = b.`object` as? VirtualFile
            if (fa == null || fb == null) return@Comparator 0
            // Files without a resolvable .csproj sort last, after named groups.
            val pa = ProjectResolver.projectNameFor(fa) ?: "￿"
            val pb = ProjectResolver.projectNameFor(fb) ?: "￿"
            val byProject = pa.compareTo(pb, ignoreCase = true)
            if (byProject != 0) byProject else fa.name.compareTo(fb.name, ignoreCase = true)
        }
    }
}
