package com.lub.app.parser

/**
 * اكتشاف بيئة وتقنية المشروع من الملفات المستخلصة (package.json, build.gradle, requirements.txt...)
 */
object StackDetector {

    fun detect(filePaths: List<String>, fileContents: Map<String, String> = emptyMap()): String {
        val pathSet = filePaths.map { it.lowercase() }.toSet()

        // 1. Android
        if (pathSet.any { it.endsWith("build.gradle.kts") || it.endsWith("build.gradle") || it.endsWith("androidmanifest.xml") }) {
            return "Android (Kotlin/Java)"
        }

        // 2. Flutter
        if (pathSet.any { it.endsWith("pubspec.yaml") }) {
            return "Flutter / Dart"
        }

        // 3. Rust
        if (pathSet.any { it.endsWith("cargo.toml") }) {
            return "Rust"
        }

        // 4. Go
        if (pathSet.any { it.endsWith("go.mod") }) {
            return "Go"
        }

        // 5. Python
        if (pathSet.any { it.endsWith("requirements.txt") || it.endsWith("pyproject.toml") || it.endsWith("pipfile") || it.endsWith(".py") }) {
            return "Python"
        }

        // 6. Node / JavaScript / TypeScript
        val pkgJsonPath = filePaths.firstOrNull { it.endsWith("package.json", ignoreCase = true) }
        if (pkgJsonPath != null) {
            val content = fileContents[pkgJsonPath] ?: ""
            return when {
                content.contains("\"next\"") -> "Next.js / React"
                content.contains("\"react\"") -> "React"
                content.contains("\"vue\"") -> "Vue.js"
                content.contains("\"@angular") -> "Angular"
                content.contains("\"svelte\"") -> "Svelte"
                content.contains("\"express\"") -> "Node.js (Express)"
                else -> "Node.js / Web"
            }
        }

        // 7. General Web
        if (pathSet.any { it.endsWith(".html") || it.endsWith(".css") || it.endsWith(".js") }) {
            return "HTML / CSS / JavaScript"
        }

        return "مشروع برمجي متنوع"
    }
}
