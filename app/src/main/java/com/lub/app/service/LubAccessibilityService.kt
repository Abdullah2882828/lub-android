package com.lub.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.lub.app.App
import com.lub.app.MainActivity
import com.lub.app.QuickPushActivity
import com.lub.app.capture.AutoScroller
import com.lub.app.capture.ClipActivity
import com.lub.app.capture.DenyList
import com.lub.app.capture.ScreenReader
import com.lub.app.data.BasketItemEntity
import com.lub.app.essence.EssenceExtractor
import com.lub.app.overlay.BubbleOverlay
import com.lub.app.overlay.CaptureBadge
import com.lub.app.overlay.CapturePanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * خدمة إمكانية الوصول المركزية لتطبيق "لُب"
 * تراقب فقط التطبيق الأمامي لإظهار الفقاعة أو إخفائها طبقاً للقائمة الممنوعة
 * وتنفذ الالتقاط عند طلب المستخدم الصريح فقط
 */
class LubAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: LubAccessibilityService? = null

        fun isServiceRunning(): Boolean = instance != null
        fun getInstance(): LubAccessibilityService? = instance
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var bubbleOverlay: BubbleOverlay? = null
    private var capturePanel: CapturePanel? = null
    private var captureBadge: CaptureBadge? = null
    private var autoScroller: AutoScroller? = null

    private var currentPkg: String = ""
    private var isAppInDenyList = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        CaptureBus.isServiceConnected.value = true

        autoScroller = AutoScroller(this)
        captureBadge = CaptureBadge(this)

        capturePanel = CapturePanel(this) { action ->
            handlePanelAction(action)
        }

        bubbleOverlay = BubbleOverlay(this) {
            serviceScope.launch {
                val basketCount = App.instance.database.basketDao().getAllBasketItemsList().size
                capturePanel?.show(basketCount)
            }
        }

        serviceScope.launch {
            val isEnabled = App.instance.settingsStore.bubbleEnabledFlow.first()
            if (isEnabled && !isAppInDenyList) {
                bubbleOverlay?.show()
            }
        }

        // Listen for internal capture triggers from Tile or Bus
        serviceScope.launch {
            CaptureBus.events.collect { event ->
                when (event) {
                    is CaptureBus.CaptureEvent.TriggerScreenCapture -> executeScreenCapture(false)
                    is CaptureBus.CaptureEvent.TriggerFullChatCapture -> executeFullChatCapture(false)
                    is CaptureBus.CaptureEvent.TriggerClipboardCapture -> launchClipActivity(false)
                    is CaptureBus.CaptureEvent.CancelCapture -> autoScroller?.cancel()
                    is CaptureBus.CaptureEvent.PushQuickCapture -> executeQuickPush(event.source)
                }
            }
        }

        // Listen for badge updates
        serviceScope.launch {
            CaptureBus.badgeState.collect { state ->
                if (state.isVisible) {
                    captureBadge?.show(state.message) {
                        autoScroller?.cancel()
                    }
                } else {
                    captureBadge?.hide()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: ""
            if (pkg.isNotEmpty() && pkg != packageName) {
                currentPkg = pkg
                CaptureBus.currentForegroundPackage.value = pkg

                serviceScope.launch {
                    val isDenied = DenyList.isDenied(App.instance.settingsStore, pkg)
                    isAppInDenyList = isDenied
                    val isBubbleSettingEnabled = App.instance.settingsStore.bubbleEnabledFlow.first()

                    if (isDenied || !isBubbleSettingEnabled) {
                        bubbleOverlay?.hide()
                        capturePanel?.dismiss()
                    } else {
                        bubbleOverlay?.show()
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        // Accessibility service interrupted by system
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        CaptureBus.isServiceConnected.value = false
        bubbleOverlay?.hide()
        capturePanel?.dismiss()
        captureBadge?.hide()
    }

    private fun handlePanelAction(action: CapturePanel.Action) {
        when (action) {
            CapturePanel.Action.QUICK_PUSH -> {
                serviceScope.launch {
                    val defaultMode = App.instance.settingsStore.defaultCaptureModeFlow.first()
                    if (defaultMode == "SCREEN") {
                        executeScreenCapture(isForQuickPush = true)
                    } else {
                        executeFullChatCapture(isForQuickPush = true)
                    }
                }
            }
            CapturePanel.Action.SCREEN_CAPTURE -> executeScreenCapture(isForQuickPush = false)
            CapturePanel.Action.FULL_CHAT_CAPTURE -> executeFullChatCapture(isForQuickPush = false)
            CapturePanel.Action.CLIPBOARD_CAPTURE -> launchClipActivity(isForQuickPush = false)
            CapturePanel.Action.OPEN_BASKET -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("START_TAB", "BASKET")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
            }
            CapturePanel.Action.OPEN_APP -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
            }
            CapturePanel.Action.DISMISS -> {
                bubbleOverlay?.setTemporarilyHidden(true)
                Toast.makeText(this, "تم إخفاء الفقاعة مؤقتاً. افتح لُب لإعادتها", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun executeScreenCapture(isForQuickPush: Boolean) {
        serviceScope.launch {
            val root = rootInActiveWindow
            val segments = ScreenReader.readActiveWindow(root, currentPkg)
            if (segments.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LubAccessibilityService, "لم يتم العثور على نصوص قابلة للقراءة", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Extract essence
            val extractionResult = EssenceExtractor.extract(segments)

            if (extractionResult.extractedFiles.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@LubAccessibilityService,
                        "لم يُكتشف أي كود في الشاشة. جرب الحافظة أو سحب المحادثة كاملة",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            if (isForQuickPush) {
                // Launch QuickPush directly
                val intent = Intent(this@LubAccessibilityService, QuickPushActivity::class.java).apply {
                    putExtra(QuickPushActivity.EXTRA_SOURCE, "SCREEN")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                // Save files in temporary basket or session
                saveExtractionToBasket(extractionResult)
                startActivity(intent)
            } else {
                saveExtractionToBasket(extractionResult)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@LubAccessibilityService,
                        "تم استخلاص ${extractionResult.extractedFiles.size} ملفات وحفظها في السلة 🧺",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun executeFullChatCapture(isForQuickPush: Boolean) {
        serviceScope.launch {
            val limit = App.instance.settingsStore.autoScrollLimitFlow.first()
            val segments = autoScroller?.captureFullChat(
                maxPages = limit,
                sourcePkg = currentPkg
            ) { _, _ -> } ?: emptyList()

            if (segments.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LubAccessibilityService, "لم يتم العثور على محتوى للتمرير", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val extractionResult = EssenceExtractor.extract(segments)
            if (extractionResult.extractedFiles.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@LubAccessibilityService,
                        "انتهى التمرير ولم يُكتشف كود مشروع. جرّب نسخ المحادثة للحافظة",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            saveExtractionToBasket(extractionResult)

            if (isForQuickPush) {
                val intent = Intent(this@LubAccessibilityService, QuickPushActivity::class.java).apply {
                    putExtra(QuickPushActivity.EXTRA_SOURCE, "FULL_CHAT")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@LubAccessibilityService,
                        "تم استخلاص ${extractionResult.extractedFiles.size} ملفات من المحادثة كاملة 🧺",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun launchClipActivity(isForQuickPush: Boolean) {
        val intent = Intent(this, ClipActivity::class.java).apply {
            putExtra(ClipActivity.EXTRA_FAST_PUSH, isForQuickPush)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun executeQuickPush(source: String) {
        serviceScope.launch {
            val intent = Intent(this@LubAccessibilityService, QuickPushActivity::class.java).apply {
                putExtra(QuickPushActivity.EXTRA_SOURCE, source)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
    }

    private suspend fun saveExtractionToBasket(result: EssenceExtractor.ExtractionResult) {
        val items = result.extractedFiles.map { file ->
            BasketItemEntity(
                id = UUID.randomUUID().toString(),
                source = "EXTRACTION",
                capturedAt = System.currentTimeMillis(),
                header = file.filePath,
                code = file.content,
                language = file.language,
                targetPath = file.filePath,
                confidence = file.confidence,
                isSelected = true
            )
        }
        App.instance.database.basketDao().insertItems(items)
    }
}
