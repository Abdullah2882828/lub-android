package com.lub.app.essence

/**
 * فحص وإزالة أرقام الأسطر المتسلسلة من كتل الأكواد عند نسخها من عارضات الأكواد
 */
object LineNumberStripper {

    private val LINE_NUM_REGEX = Regex("""^\s*(\d{1,5})[\s.:|│]\s*(.*)$""")

    fun stripLineNumbers(content: String): Pair<String, Boolean> {
        val lines = content.split('\n')
        if (lines.size < 3) return Pair(content, false)

        var matchedCount = 0
        var expectedSeq = -1
        var isSequential = true

        for (line in lines) {
            val match = LINE_NUM_REGEX.find(line)
            if (match != null) {
                matchedCount++
                val num = match.groupValues[1].toIntOrNull() ?: -1
                if (expectedSeq != -1 && num != expectedSeq + 1 && num != expectedSeq) {
                    isSequential = false
                }
                expectedSeq = num
            }
        }

        val ratio = matchedCount.toFloat() / lines.size.toFloat()
        if (ratio >= 0.8f) {
            val strippedLines = lines.map { line ->
                val match = LINE_NUM_REGEX.find(line)
                if (match != null) {
                    match.groupValues[2]
                } else {
                    line
                }
            }
            return Pair(strippedLines.joinToString("\n"), true)
        }

        return Pair(content, false)
    }
}
