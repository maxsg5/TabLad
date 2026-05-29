package com.github.maxsg5.tablad

import com.intellij.openapi.vfs.VirtualFile

/**
 * Maps a file to its owning .NET project by walking up the directory tree
 * looking for a .csproj / .fsproj / .vbproj / .shproj sibling.
 *
 * Why this instead of [com.intellij.openapi.roots.ProjectFileIndex.getModuleForFile]?
 * In Rider, every file in the solution maps to a single IntelliJ Module
 * named "rider.module" — the IntelliJ Module abstraction does not reflect
 * Rider's .NET project boundaries. The real project structure is held by
 * ReSharper / the Rider WorkspaceModel, but the simplest frontend-only
 * proxy is the .csproj filename on disk.
 *
 * Edge cases NOT handled (acceptable for v0.1):
 *  - Files included from outside their .csproj directory via <Compile Include="..\foo.cs" />
 *  - Shared projects (.shproj) where membership is governed by .projitems
 *  - Linked files
 */
object ProjectResolver {
    private val PROJECT_EXTS = setOf("csproj", "fsproj", "vbproj", "shproj")

    fun projectNameFor(file: VirtualFile): String? {
        var dir = file.parent
        while (dir != null) {
            val children = dir.children ?: emptyArray()
            val projectFile = children.firstOrNull { child ->
                !child.isDirectory && (child.extension?.lowercase() in PROJECT_EXTS)
            }
            if (projectFile != null) return projectFile.nameWithoutExtension
            dir = dir.parent
        }
        return null
    }
}