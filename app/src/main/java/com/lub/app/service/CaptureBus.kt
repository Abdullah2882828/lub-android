package com.lub.app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * حافلة الأحداث والتواصل بين خدمة إمكانية الوصول وواجهات التطبيق والفقاعة
 */
object CaptureBus {

    // Service connection state
    val isServiceConnected = MutableStateFlow(false)
    val currentForegroundPackage = MutableStateFlow("")

    // Badge indicator state during auto-scrolling
    data class BadgeState(
        val isVisible: Boolean = false,
        val message: String = "",
        val pageCount: Int = 0
    )
    val badgeState = MutableStateFlow(BadgeState())

    // Capture triggers
    sealed class CaptureEvent {
        object TriggerScreenCapture : CaptureEvent()
        object TriggerFullChatCapture : CaptureEvent()
        object TriggerClipboardCapture : CaptureEvent()
        object CancelCapture : CaptureEvent()
        data class PushQuickCapture(val source: String) : CaptureEvent()
    }

    private val _events = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 10)
    val events = _events.asSharedFlow()

    fun emitEvent(event: CaptureEvent) {
        _events.tryEmit(event)
    }

    // Diagnostics data holder
    data class DiagnosticReport(
        val packageName: String = "",
        val nodeCount: Int = 0,
        val maxDepth: Int = 0,
        val longestTextPreview: String = "",
        val sampleNodes: List<NodeDetail> = emptyList(),
        val timestamp: Long = System.currentTimeMillis()
    )

    data class NodeDetail(
        val className: String,
        val viewId: String,
        val bounds: String,
        val isScrollable: Boolean,
        val childCount: Int,
        val lineCount: Int,
        val preservesIndentation: Boolean,
        val textPreview: String
    )

    val latestDiagnostic = MutableStateFlow<DiagnosticReport?>(null)
}
