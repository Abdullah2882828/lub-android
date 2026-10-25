package com.lub.app.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.lub.app.service.CaptureBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * محرك التمرير الآلي لسحب المحادثة الكاملة
 * يحدد أكبر عنصر قابل للتمرير، يمرر ويدمج المقاطع الملتقطة ويكتشف التكرار ونهاية المحتوى
 */
class AutoScroller(
    private val service: AccessibilityService
) {

    @Volatile
    private var isCancelled = false

    fun cancel() {
        isCancelled = true
        CaptureBus.badgeState.value = CaptureBus.BadgeState(isVisible = false)
    }

    suspend fun captureFullChat(
        maxPages: Int = 50,
        sourcePkg: String,
        onProgress: (page: Int, segmentCount: Int) -> Unit
    ): List<CapturedSegment> = withContext(Dispatchers.Main) {
        isCancelled = false
        val allSegments = mutableListOf<CapturedSegment>()
        var consecutiveDuplicateCount = 0
        var lastPageTextHash = 0

        CaptureBus.badgeState.value = CaptureBus.BadgeState(
            isVisible = true,
            message = "جاري سحب المحادثة...",
            pageCount = 1
        )

        val startTime = System.currentTimeMillis()
        val maxDurationMs = 90_000L // 90 seconds limit

        try {
            var currentPage = 1
            while (currentPage <= maxPages && !isCancelled) {
                if (System.currentTimeMillis() - startTime > maxDurationMs) {
                    break
                }

                val root = service.rootInActiveWindow
                val pageSegments = ScreenReader.readActiveWindow(root, sourcePkg)

                // Calculate hash of current page to detect stagnation
                val currentHash = pageSegments.fold(0) { acc, seg -> acc * 31 + seg.text.hashCode() }
                if (currentHash == lastPageTextHash && pageSegments.isNotEmpty()) {
                    consecutiveDuplicateCount++
                    if (consecutiveDuplicateCount >= 2) {
                        // Content reached end
                        break
                    }
                } else {
                    consecutiveDuplicateCount = 0
                    lastPageTextHash = currentHash
                }

                // Merge deduplicating overlapping segments
                mergeSegments(allSegments, pageSegments)

                onProgress(currentPage, allSegments.size)
                CaptureBus.badgeState.value = CaptureBus.BadgeState(
                    isVisible = true,
                    message = "الصفحة $currentPage - ${allSegments.size} مقطعاً",
                    pageCount = currentPage
                )

                // Find scrollable node
                val scrollableNode = findLargestScrollableNode(root)
                if (scrollableNode == null) {
                    break
                }

                // Scroll forward
                val scrolled = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                if (!scrolled) {
                    // Try parent scroll or stop
                    break
                }

                // Short delay to let UI settle after scroll
                delay(650)
                currentPage++
            }
        } catch (e: CancellationException) {
            // Cancelled cleanly
        } finally {
            CaptureBus.badgeState.value = CaptureBus.BadgeState(isVisible = false)
        }

        allSegments
    }

    private fun mergeSegments(
        target: MutableList<CapturedSegment>,
        incoming: List<CapturedSegment>
    ) {
        if (target.isEmpty()) {
            target.addAll(incoming)
            return
        }

        // Fast overlap check: find first item in incoming that is not already at tail of target
        val targetLastTexts = target.takeLast(10).map { it.text.trim() }.toSet()
        for (seg in incoming) {
            val trimmed = seg.text.trim()
            if (trimmed !in targetLastTexts) {
                target.add(seg)
            }
        }
    }

    private fun findLargestScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        var largest: AccessibilityNodeInfo? = null
        var maxArea = 0

        fun search(node: AccessibilityNodeInfo?) {
            if (node == null) return
            try {
                if (node.isScrollable) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    val area = rect.width() * rect.height()
                    if (area > maxArea) {
                        maxArea = area
                        largest = node
                    }
                }
                for (i in 0 until node.childCount) {
                    search(node.getChild(i))
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        search(root)
        return largest
    }
}
