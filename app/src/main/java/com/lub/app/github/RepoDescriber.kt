package com.lub.app.github

object RepoDescriber {

    fun generateDescription(
        files: List<Pair<String, String>>,
        techStack: String
    ): String {
        // 1. Check description in package.json
        val pkgJson = files.firstOrNull { it.first.endsWith("package.json", ignoreCase = true) }?.second
        if (pkgJson != null) {
            val descMatch = Regex(""""description"\s*:\s*"([^"]+)"""").find(pkgJson)
            if (descMatch != null) {
                val clean = cleanDescription(descMatch.groupValues[1])
                if (clean.isNotEmpty()) return clean
            }
        }

        // 2. First useful line from README.md
        val readme = files.firstOrNull { it.first.equals("README.md", ignoreCase = true) }?.second
        if (readme != null) {
            val lines = readme.split('\n')
            for (line in lines) {
                val trimmed = line.trim().removePrefix("#").removePrefix(">").trim()
                if (trimmed.length > 15 && !trimmed.startsWith("![")) {
                    val clean = cleanDescription(trimmed)
                    if (clean.isNotEmpty()) return clean
                }
            }
        }

        // 3. Fallback description
        return cleanDescription("مشروع $techStack — ${files.size} ملفات، استُخرج بواسطة تطبيق لُب")
    }

    private fun cleanDescription(raw: String): String {
        var clean = raw.replace('\n', ' ').replace('\r', ' ').trim()
        clean = clean.replace(Regex("""[`*_{}\[\]()]"""), "")
        if (clean.length > 300) {
            clean = clean.take(297) + "..."
        }
        return clean
    }
}
