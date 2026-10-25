package com.lub.app.essence

import com.lub.app.capture.CapturedSegment

enum class SegmentType {
    CODE,
    FILE_NAME_HINT,
    EXPLANATION,
    NOISE
}

object SegmentClassifier {

    private val CODE_SYMBOLS = setOf('{', '}', ';', '=', '>', '<', '(', ')', '[', ']', '$', '/', '\\', ':', '"', '\'', '`')
    private val CODE_KEYWORDS = setOf(
        "import", "export", "function", "const", "let", "var", "class", "def", "return",
        "package", "public", "private", "fun", "val", "interface", "struct", "fn",
        "include", "using", "from", "async", "await", "if", "else", "for", "while"
    )

    private val FILE_HINT_PATTERNS = listOf(
        Regex("""^(?:file|path|ملف|مسار)[:\s]+[`'"]?([a-zA-Z0-9_\-./\\]+\.[a-zA-Z0-9]+)[`'"]?""", RegexOption.IGNORE_CASE),
        Regex("""^###?\s*[`'"]?([a-zA-Z0-9_\-./\\]+\.[a-zA-Z0-9]+)[`'"]?"""),
        Regex("""^\*\*([a-zA-Z0-9_\-./\\]+\.[a-zA-Z0-9]+)\*\*$"""),
        Regex("""^[📄📁]\s*[`'"]?([a-zA-Z0-9_\-./\\]+\.[a-zA-Z0-9]+)[`'"]?"""),
        Regex("""^[a-zA-Z0-9_\-./\\]+\.(?:js|ts|tsx|jsx|kt|kts|java|py|html|css|json|xml|yaml|yml|md|rs|go|sh|c|cpp|h|sql)$""", RegexOption.IGNORE_CASE)
    )

    fun classify(segment: CapturedSegment): SegmentType {
        val text = segment.text.trim()
        if (text.isEmpty() || NoiseFilter.isNoise(text)) {
            return SegmentType.NOISE
        }

        // Check if it's a file name hint
        if (text.length <= 160 && FILE_HINT_PATTERNS.any { it.containsMatchIn(text) }) {
            return SegmentType.FILE_NAME_HINT
        }

        // Calculate code score
        val lines = segment.lines
        val totalChars = text.length
        val symbolCount = text.count { it in CODE_SYMBOLS }
        val symbolDensity = symbolCount.toFloat() / totalChars.coerceAtLeast(1)

        val words = text.lowercase().split(Regex("""\W+"""))
        val keywordCount = words.count { it in CODE_KEYWORDS }

        val hasIndentation = lines.any { it.startsWith("  ") || it.startsWith("\t") }
        val isMultiLine = lines.size >= 3

        var score = 0
        if (symbolDensity > 0.12f) score += 3
        if (keywordCount >= 2) score += 3
        if (hasIndentation) score += 2
        if (isMultiLine) score += 2
        if (text.contains("```") || text.contains("~~~")) score += 4

        // Explanation signals: standard punctuation sentences, conversational words
        val arabicCharCount = text.count { it in '\u0600'..'\u06FF' }
        val isPredominantlyArabicText = arabicCharCount > totalChars * 0.4f
        if (isPredominantlyArabicText && keywordCount == 0 && symbolDensity < 0.08f) {
            return SegmentType.EXPLANATION
        }

        return if (score >= 4) SegmentType.CODE else SegmentType.EXPLANATION
    }
}
