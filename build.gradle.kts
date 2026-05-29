import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // Rider requires useInstaller=false; see
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1852
        rider("2024.3") {
            useInstaller = false
        }
        jetbrainsRuntime()
        testFramework(TestFrameworkType.Platform)
    }
}

// Project-level IntelliJ Platform configuration.
// Disable bytecode instrumentation — required step for plugins that use Swing .form (GUI Designer)
// files, which we don't. Leaving it on triggers a known bug on Windows + non-JBR system JDK where
// the task tries to read a `Packages` subdirectory of JAVA_HOME that only exists in JetBrains Runtime.
// See https://github.com/JetBrains/intellij-platform-gradle-plugin/issues (search "Packages does not exist")
intellijPlatform {
    instrumentCode = false
}