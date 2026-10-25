package com.lub.app.parser

/**
 * تحليل شجرة المشروع ASCII (├──, └──, │) واستخراج المسارات الكاملة للملفات
 */
object TreeParser {

    private val TREE_BRANCH_REGEX = Regex("""^[│|\s]*(?:├──|└──|\|--|\\--|─-)\s*(.+)$""")

    fun isTreeBlock(text: String): Boolean {
        val lines = text.split('\n')
        val branchCount = lines.count { TREE_BRANCH_REGEX.containsMatchIn(it) }
        return branchCount >= 2
    }

    fun parseTreePaths(treeText: String): List<String> {
        val lines = treeText.split('\n')
        val resultPaths = mutableListOf<String>()
        val pathStack = mutableListOf<Pair<Int, String>>() // (depth, name)

        for (line in lines) {
            val match = TREE_BRANCH_REGEX.find(line) ?: continue
            val rawName = match.groupValues[1].trim().removeSuffix("/")
            if (rawName.isEmpty()) continue

            // Determine depth by character offset of the branch
            val depth = line.indexOfAny(charArrayOf('├', '└', '|', '\\'))

            while (pathStack.isNotEmpty() && pathStack.last().first >= depth) {
                pathStack.removeAt(pathStack.size - 1)
            }

            pathStack.add(Pair(depth, rawName))

            // If it looks like a file (has an extension)
            if (rawName.contains('.')) {
                val fullPath = pathStack.joinToString("/") { it.second }
                resultPaths.add(PathSanitizer.sanitize(fullPath))
            }
        }

        return resultPaths
    }
}
