package com.lub.app.parser

data class InferredFileInfo(
    val filePath: String,
    val source: InferenceSource,
    val confidence: Float,
    val warning: String?
)

enum class InferenceSource {
    HEADER,
    FIRST_LINE_COMMENT,
    PREVIOUS_HINT,
    TREE_MATCH,
    DEFAULT_FALLBACK
}

object FilenameInference {

    private val FIRST_LINE_COMMENT_REGEXES = listOf(
        Regex("""^(?://|#|--|/\*|<!--)\s*(?:filename|file|path|ملف|مسار)[:\s]+[`'"]?([a-zA-Z0-9_\-./\\]+\.[a-zA-Z0-9]+)[`'"]?""", RegexOption.IGNORE_CASE),
        Regex("""^(?://|#|--)\s*([a-zA-Z0-9_\-./\\]+\.(?:js|ts|tsx|jsx|kt|kts|java|py|html|css|json|xml|yaml|yml|md|rs|go|sh|sql))\s*$""", RegexOption.IGNORE_CASE)
    )

    private val HINT_PATTERNS = listOf(
        Regex("""(?:create|edit|in|file|path|ملف|مسار|في)\s+[`'"]?([a-zA-Z0-9_\-./\\]+\.[a-zA-Z0-9]+)[`'"]?""", RegexOption.IGNORE_CASE),
        Regex("""[`'"]([a-zA-Z0-9_\-./\\]+\.(?:js|ts|tsx|jsx|kt|kts|java|py|html|css|json|xml|yaml|yml|md|rs|go|sh|sql))[`'"]""", RegexOption.IGNORE_CASE)
    )

    fun inferFilename(
        header: String?,
        firstLine: String?,
        hint: String?,
        language: String,
        candidateTreePaths: List<String> = emptyList(),
        index: Int = 1
    ): InferredFileInfo {
        // 1. Header if it looks like a file path
        if (!header.isNullOrBlank()) {
            val cleanHeader = header.trim().removePrefix("`").removeSuffix("`")
            if (cleanHeader.contains('.') && !cleanHeader.contains(' ')) {
                return InferredFileInfo(
                    filePath = PathSanitizer.sanitize(cleanHeader),
                    source = InferenceSource.HEADER,
                    confidence = 0.95f,
                    warning = null
                )
            }
        }

        // 2. Comment in the first line of code
        if (!firstLine.isNullOrBlank()) {
            for (pattern in FIRST_LINE_COMMENT_REGEXES) {
                val match = pattern.find(firstLine.trim())
                if (match != null) {
                    val rawName = match.groupValues[1]
                    return InferredFileInfo(
                        filePath = PathSanitizer.sanitize(rawName),
                        source = InferenceSource.FIRST_LINE_COMMENT,
                        confidence = 0.92f,
                        warning = null
                    )
                }
            }
        }

        // 3. Hint from surrounding text
        if (!hint.isNullOrBlank()) {
            for (pattern in HINT_PATTERNS) {
                val match = pattern.find(hint)
                if (match != null) {
                    val rawName = match.groupValues[1]
                    return InferredFileInfo(
                        filePath = PathSanitizer.sanitize(rawName),
                        source = InferenceSource.PREVIOUS_HINT,
                        confidence = 0.85f,
                        warning = null
                    )
                }
            }
        }

        // 4. Project tree matching
        if (candidateTreePaths.isNotEmpty()) {
            val ext = getExtensionForLanguage(language)
            val matched = candidateTreePaths.firstOrNull { it.endsWith(".$ext", ignoreCase = true) }
            if (matched != null) {
                return InferredFileInfo(
                    filePath = matched,
                    source = InferenceSource.TREE_MATCH,
                    confidence = 0.78f,
                    warning = "تمت مطابقة المسار من شجرة المشروع"
                )
            }
        }

        // 5. Default fallback based on language
        val ext = getExtensionForLanguage(language)
        val defaultName = "file_$index.$ext"
        return InferredFileInfo(
            filePath = defaultName,
            source = InferenceSource.DEFAULT_FALLBACK,
            confidence = 0.40f,
            warning = "اسم ملف مقترح تلقائياً لعدم وجود اسم صريح"
        )
    }

    private fun getExtensionForLanguage(lang: String): String {
        return when (lang.lowercase()) {
            "kotlin", "kt" -> "kt"
            "javascript", "js" -> "js"
            "typescript", "ts" -> "ts"
            "python", "py" -> "py"
            "html" -> "html"
            "css" -> "css"
            "json" -> "json"
            "markdown", "md" -> "md"
            "rust", "rs" -> "rs"
            "go" -> "go"
            "shell", "sh", "bash" -> "sh"
            "java" -> "java"
            else -> "txt"
        }
    }
}
