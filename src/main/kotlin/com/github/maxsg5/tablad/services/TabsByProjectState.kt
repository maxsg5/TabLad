package com.github.maxsg5.tablad.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import java.awt.Color

/**
 * Persistent per-project store for color overrides.
 *
 * Storage: `<project>/.idea/tabsByProject.xml` (or per-workspace .xml depending on Rider config).
 * Survives Rider restarts. One file per opened solution.
 */
@Service(Service.Level.PROJECT)
@State(name = "TabsByProject", storages = [Storage("tabsByProject.xml")])
class TabsByProjectState : PersistentStateComponent<TabsByProjectState.PersistedState> {
    /** Top-level state object that gets XML-serialized. Must be a public class with a no-arg ctor. */
    class PersistedState {
        // Map<projectName, hexColor>. LinkedHashMap to keep insertion order stable across saves.
        var overrides: MutableMap<String, String> = LinkedHashMap()
    }

    private var state = PersistedState()

    override fun getState(): PersistedState = state
    override fun loadState(loaded: PersistedState) {
        state = loaded
    }

    fun getOverride(projectName: String): Color? {
        val hex = state.overrides[projectName] ?: return null
        return runCatching { Color.decode(hex) }.getOrNull()
    }

    fun setOverride(projectName: String, color: Color?) {
        if (color == null) {
            state.overrides.remove(projectName)
        } else {
            state.overrides[projectName] =
                String.format("#%02X%02X%02X", color.red, color.green, color.blue)
        }
    }

    fun knownProjects(): Set<String> = state.overrides.keys.toSet()

    companion object {
        fun getInstance(project: Project): TabsByProjectState = project.service()

        /** Fired when the Settings page applies new overrides; the tool window listens to repaint. */
        val TOPIC: Topic<Listener> = Topic.create("TabsByProject.overridesChanged", Listener::class.java)
    }

    fun interface Listener {
        fun overridesChanged()
    }
}