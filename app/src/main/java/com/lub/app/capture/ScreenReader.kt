package com.lub.app.capture

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.lub.app.service.CaptureBus

data class CapturedSegment(
    val text: String,
    val bounds: Rect,
    val className: String,
    val viewId: String,
    val lines: List<String>,
    val sourcePkg: String
)

object ScreenReader {

    private const val MAX_NODES = 1200
    private const val MAX_DEPTH = 50

    /**
     * قراءة محتوى الشاشة الحالي بأمان واستخراج مقاطع النصوص مع ترتيبها ومواضعها
     */
    fun readActiveWindow(
        rootNode: AccessibilityNodeInfo?,
        sourcePkg: String
    ): List<CapturedSegment> {
        if (rootNode == null) return emptyList()

        val rawList = mutableListOf<CapturedSegment>()
        var visitedCount = 0

        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || visitedCount >= MAX_NODES || depth > MAX_DEPTH) return
            visitedCount++

            try {
                // Ignore passwords and invisible nodes
                if (node.isPassword || !node.isVisibleToUser) {
                    return
                }

                val text = node.text?.toString() ?: ""
                val contentDesc = node.contentDescription?.toString() ?: ""
                val fullText = when {
                    text.isNotBlank() -> text
                    contentDesc.isNotBlank() -> contentDesc
                    else -> ""
                }

                if (fullText.isNotBlank()) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)

                    val lines = fullText.split('\n')
                    rawList.add(
                        CapturedSegment(
                            text = fullText,
                            bounds = bounds,
                            className = node.className?.toString() ?: "",
                            viewId = node.viewIdResourceName ?: "",
                            lines = lines,
                            sourcePkg = sourcePkg
                        )
                    )
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    traverse(child, depth + 1)
                }
            } catch (e: Exception) {
                // Ignore transient node recycling errors
            }
        }

        traverse(rootNode, 0)

        // Sort by visual top-to-bottom position, then left-to-right
        return rawList.sortedWith(
            compareBy<CapturedSegment> { it.bounds.top }
                .thenBy { it.bounds.left }
        )
    }

    /**
     * إعداد تقرير تشخيصي متكامل للنافذة النشطة لتغذية شاشة التشخيص
     */
    fun diagnose(
        rootNode: AccessibilityNodeInfo?,
        sourcePkg: String
    ): CaptureBus.DiagnosticReport {
        if (rootNode == null) {
            return CaptureBus.DiagnosticReport(packageName = sourcePkg)
        }

        var nodeCount = 0
        var maxDepthFound = 0
        var longestText = ""
        val sampleList = mutableListOf<CaptureBus.NodeDetail>()

        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || nodeCount >= 1000) return
            nodeCount++
            if (depth > maxDepthFound) maxDepthFound = depth

            try {
                val text = node.text?.toString() ?: ""
                if (text.length > longestText.length) {
                    longestText = text
                }

                if (text.isNotBlank() && sampleList.size < 20) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    val lines = text.split('\n')
                    val hasIndent = lines.any { it.startsWith("  ") || it.startsWith("\t") }

                    sampleList.add(
                        CaptureBus.NodeDetail(
                            className = node.className?.toString() ?: "",
                            viewId = node.viewIdResourceName ?: "",
                            bounds = "(${bounds.left},${bounds.top} - ${bounds.right},${bounds.bottom})",
                            isScrollable = node.isScrollable,
                            childCount = node.childCount,
                            lineCount = lines.size,
                            preservesIndentation = hasIndent,
                            textPreview = text.take(120).replace("\n", " ")
                        )
                    )
                }

                for (i in 0 until node.childCount) {
                    traverse(node.getChild(i), depth + 1)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        traverse(rootNode, 0)

        return CaptureBus.DiagnosticReport(
            packageName = sourcePkg,
            nodeCount = nodeCount,
            maxDepth = maxDepthFound,
            longestTextPreview = longestText.take(200),
            sampleNodes = sampleList,
            timestamp = System.currentTimeMillis()
        )
    }
}
