package com.lub.app.github

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RepoNamer {

    fun suggestRepoName(
        files: List<Pair<String, String>>,
        defaultPrefix: String = "lub"
    ): String {
        // 1. Check package.json name
        val pkgJson = files.firstOrNull { it.first.endsWith("package.json", ignoreCase = true) }?.second
        if (pkgJson != null) {
            val nameMatch = Regex(""""name"\s*:\s*"([^"]+)"""").find(pkgJson)
            if (nameMatch != null) {
                val clean = cleanRepoName(nameMatch.groupValues[1])
                if (clean.isNotEmpty()) return clean
            }
        }

        // 2. Check pubspec.yaml name
        val pubspec = files.firstOrNull { it.first.endsWith("pubspec.yaml", ignoreCase = true) }?.second
        if (pubspec != null) {
            val nameMatch = Regex("""^name:\s*([a-zA-Z0-9_\-]+)""", RegexOption.MULTILINE).find(pubspec)
            if (nameMatch != null) {
                val clean = cleanRepoName(nameMatch.groupValues[1])
                if (clean.isNotEmpty()) return clean
            }
        }

        // 3. Check Cargo.toml name
        val cargo = files.firstOrNull { it.first.endsWith("Cargo.toml", ignoreCase = true) }?.second
        if (cargo != null) {
            val nameMatch = Regex("""^name\s*=\s*"([^"]+)"""", RegexOption.MULTILINE).find(cargo)
            if (nameMatch != null) {
                val clean = cleanRepoName(nameMatch.groupValues[1])
                if (clean.isNotEmpty()) return clean
            }
        }

        // 4. Check rootProject.name in settings.gradle
        val settingsGradle = files.firstOrNull { it.first.endsWith("settings.gradle.kts") || it.first.endsWith("settings.gradle") }?.second
        if (settingsGradle != null) {
            val nameMatch = Regex("""rootProject\.name\s*=\s*['"]([^'"]+)['"]""").find(settingsGradle)
            if (nameMatch != null) {
                val clean = cleanRepoName(nameMatch.groupValues[1])
                if (clean.isNotEmpty()) return clean
            }
        }

        // 5. Check H1 in README.md
        val readme = files.firstOrNull { it.first.equals("README.md", ignoreCase = true) }?.second
        if (readme != null) {
            val h1Match = Regex("""^#\s+([^\n]+)""", RegexOption.MULTILINE).find(readme)
            if (h1Match != null) {
                val clean = cleanRepoName(h1Match.groupValues[1])
                if (clean.isNotEmpty()) return clean
            }
        }

        // Fallback: lub-<yyyyMMdd-HHmm>
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "$defaultPrefix-$timestamp"
    }

    /**
     * تنظيف الاسم إلى ASCII صغير: a-z, 0-9, ., _, -، بلا نقطة في البداية، وبحد أقصى 100 حرف
     */
    fun cleanRepoName(raw: String): String {
        var clean = raw.trim().lowercase(Locale.US)

        // If scoped package like @scope/project, take project
        if (clean.startsWith("@") && clean.contains("/")) {
            clean = clean.substringAfter("/").replace("/", "-")
        }

        // Replace spaces and invalid chars with dashes
        clean = clean.replace(Regex("""[^a-z0-9._\-]"""), "-")
        // Collapse multiple dashes
        clean = clean.replace(Regex("""-+"""), "-")
        // Remove leading/trailing dots and dashes
        clean = clean.trimStart('.', '-').trimEnd('.', '-')

        if (clean.length > 100) {
            clean = clean.take(100).trimEnd('-', '.')
        }

        if (clean.isBlank()) {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            return "lub-$timestamp"
        }

        return clean
    }
}
