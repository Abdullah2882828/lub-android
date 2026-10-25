package com.lub.app.parser

data class RawCodeBlock(
    val header: String?,
    val language: String,
    val content: String,
    val hint: String?
)

/**
 * استخراج كتل الأكواد من النصوص الخام بمختلف الأنماط (Markdown, Cat EOF, فواصل الملفات)
 */
object BlockParser {

    private val MARKDOWN_FENCE_REGEX = Regex("""(?s)(?:^|\n)(?:```|~~~)([a-zA-Z0-9_\-.:/\\]*)\r?\n(.*?)(?:^|\n)(?:```|~~~)(?:\r?\n|$)""")
    private val EQUALS_FILE_SEPARATOR_REGEX = Regex("""(?s)(?:^|\n)===\s*(?:FILE|File|file|ملف)?:\s*([^\n=]+)\s*===\r?\n(.*?)(?=(?:^|\n)===\s*(?:FILE|File|file|ملف)?:|$)""")
    private val CAT_EOF_REGEX = Regex("""(?s)cat\s*<<['"]?EOF['"]?\s*>\s*([^\n]+)\r?\n(.*?)\r?\nEOF""")

    fun parseRawBlocks(fullText: String): List<RawCodeBlock> {
        val blocks = mutableListOf<RawCodeBlock>()

        // 1. Check for === FILE: path === separators
        val equalsMatches = EQUALS_FILE_SEPARATOR_REGEX.findAll(fullText).toList()
        if (equalsMatches.isNotEmpty()) {
            for (match in equalsMatches) {
                val path = match.groupValues[1].trim()
                val code = match.groupValues[2].trim()
                if (code.isNotBlank()) {
                    blocks.add(
                        RawCodeBlock(
                            header = path,
                            language = "auto",
                            content = code,
                            hint = null
                        )
                    )
                }
            }
            if (blocks.isNotEmpty()) return blocks
        }

        // 2. Check for cat <<EOF > file
        val catMatches = CAT_EOF_REGEX.findAll(fullText).toList()
        if (catMatches.isNotEmpty()) {
            for (match in catMatches) {
                val path = match.groupValues[1].trim()
                val code = match.groupValues[2].trim()
                if (code.isNotBlank()) {
                    blocks.add(
                        RawCodeBlock(
                            header = path,
                            language = "auto",
                            content = code,
                            hint = null
                        )
                    )
                }
            }
            if (blocks.isNotEmpty()) return blocks
        }

        // 3. Standard Markdown code fences: ```lang:path or ```lang
        val fenceMatches = MARKDOWN_FENCE_REGEX.findAll(fullText).toList()
        for (match in fenceMatches) {
            val rawInfo = match.groupValues[1].trim()
            val code = match.groupValues[2].trim()

            var lang = "text"
            var header: String? = null

            if (rawInfo.contains(':')) {
                val parts = rawInfo.split(':', limit = 2)
                lang = parts[0].trim()
                header = parts[1].trim()
            } else if (rawInfo.contains('.')) {
                header = rawInfo
                lang = rawInfo.substringAfterLast('.', "text")
            } else if (rawInfo.isNotEmpty()) {
                lang = rawInfo
            }

            // Find previous hint text within 240 chars preceding the block
            val matchStart = match.range.first
            val hintText = if (matchStart > 0) {
                val startSub = (matchStart - 240).coerceAtLeast(0)
                fullText.substring(startSub, matchStart).trim()
            } else null

            if (code.isNotBlank()) {
                blocks.add(
                    RawCodeBlock(
                        header = header,
                        language = lang,
                        content = code,
                        hint = hintText
                    )
                )
            }
        }

        return blocks
    }
}
