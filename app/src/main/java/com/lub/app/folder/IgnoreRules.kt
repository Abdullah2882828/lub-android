package com.lub.app.folder

/**
 * قواعد الاستبعاد التلقائي للمجلدات والملفات المؤقتة والضخمة (node_modules, build, .git...)
 */
object IgnoreRules {

    val DEFAULT_IGNORE_PATTERNS = setOf(
        "node_modules",
        ".git",
        ".gradle",
        "build",
        "dist",
        "out",
        "target",
        "__pycache__",
        ".pytest_cache",
        ".venv",
        "venv",
        ".idea",
        ".vscode",
        ".DS_Store",
        "Thumbs.db",
        "*.apk",
        "*.aab",
        "*.exe",
        "*.so",
        "*.o",
        "*.pyc"
    )

    fun shouldIgnore(path: String, customIgnores: Set<String> = emptySet()): Boolean {
        val segments = path.replace('\\', '/').split('/')
        val allIgnores = DEFAULT_IGNORE_PATTERNS + customIgnores

        for (seg in segments) {
            if (seg in allIgnores) return true
            if (seg.startsWith(".") && seg != ".github" && seg != ".env.example" && seg != ".gitignore") {
                if (seg == ".git" || seg == ".gradle" || seg == ".idea" || seg == ".vscode") return true
            }
        }

        val fileName = segments.lastOrNull() ?: ""
        if (fileName in allIgnores) return true
        if (fileName.endsWith(".pyc") || fileName.endsWith(".apk") || fileName == ".DS_Store") return true

        return false
    }
}
