package com.github.maxsg5.tablad

import com.github.maxsg5.tablad.services.TabsByProjectState
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

object ProjectColorAssigner {
    /** Process-wide auto-color cache. Same project name → same hashed color across all open solutions. */
    private val cache = ConcurrentHashMap<String, Color>()

    /**
     * Knuth's multiplicative constant — 2^32 * (golden ratio - 1).
     * Spreads consecutive String.hashCodes across the hue circle with ~61.8% spacing,
     * which is the optimal "least-adjacent" distribution. Avoids the problem where
     * "ConsoleApp1" / "ConsoleApp2" / "ConsoleApp3" hashCodes differ by 1 and map
     * to nearly identical hues.
     */
    private const val KNUTH_MULTIPLIER = 2654435761L

    /**
     * Resolve the color for a project: user override (from settings) wins; otherwise hash.
     * Requires Project to consult the per-project overrides service.
     */
    fun colorFor(project: Project, projectName: String): Color {
        TabsByProjectState.getInstance(project).getOverride(projectName)?.let { return it }
        return cache.computeIfAbsent(projectName) { hash(it) }
    }

    fun invalidateAutoCache() {
        cache.clear()
    }

    private fun hash(projectName: String): Color {
        val scrambled = (projectName.hashCode().toLong() * KNUTH_MULTIPLIER) and 0xFFFFFFFFL
        val hue = scrambled.toFloat() / 0xFFFFFFFFL.toFloat()
        val dark = !JBColor.isBright()
        val saturation = if (dark) 0.60f else 0.65f
        val brightness = if (dark) 0.82f else 0.88f
        return Color.getHSBColor(hue, saturation, brightness)
    }
}