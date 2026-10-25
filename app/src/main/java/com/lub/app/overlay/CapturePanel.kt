package com.lub.app.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * لوحة الأوامر السريعة المنبثقة من الفقاعة
 * مبنية بـ Views عادية لضمان خفة الأداء والعمل فوق أي تطبيق
 */
class CapturePanel(
    private val service: AccessibilityService,
    private val onAction: (Action) -> Unit
) {

    enum class Action {
        QUICK_PUSH,
        SCREEN_CAPTURE,
        FULL_CHAT_CAPTURE,
        CLIPBOARD_CAPTURE,
        OPEN_BASKET,
        OPEN_APP,
        DISMISS
    }

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var panelView: View? = null
    var isShowing = false
        private set

    fun show(basketCount: Int = 0) {
        if (isShowing) return

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 28, 28, 28)

            val bg = GradientDrawable().apply {
                setColor(0xFA181818.toInt())
                cornerRadius = 36f
                setStroke(2, 0xFF383838.toInt())
            }
            background = bg
        }

        // Title Header
        val header = TextView(service).apply {
            text = "لُب — خيارات الالتقاط"
            setTextColor(0xFFF2F2F2.toInt())
            textSize = 15f
            setPadding(0, 0, 0, 20)
            gravity = Gravity.CENTER
        }
        container.addView(header)

        // 1. Primary Action: ⚡ التقط وارفع (Larger)
        val btnQuick = Button(service).apply {
            text = "⚡ التقط وارفع الآن"
            textSize = 15f
            setTextColor(0xFF0B0B0B.toInt())
            val btnBg = GradientDrawable().apply {
                setColor(0xFFF2F2F2.toInt())
                cornerRadius = 24f
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                115
            ).apply { setMargins(0, 0, 0, 16) }
            setOnClickListener {
                dismiss()
                onAction(Action.QUICK_PUSH)
            }
        }
        container.addView(btnQuick)

        fun createSecondaryButton(title: String, action: Action, textColor: Int = 0xFFE0E0E0.toInt()): Button {
            return Button(service).apply {
                text = title
                textSize = 13f
                setTextColor(textColor)
                val btnBg = GradientDrawable().apply {
                    setColor(0xFF262626.toInt())
                    cornerRadius = 20f
                }
                background = btnBg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    98
                ).apply { setMargins(0, 0, 0, 12) }
                setOnClickListener {
                    dismiss()
                    onAction(action)
                }
            }
        }

        // 2. 📸 التقاط الشاشة
        container.addView(createSecondaryButton("📸 التقاط الشاشة الحالية", Action.SCREEN_CAPTURE))

        // 3. 📜 سحب المحادثة كاملة
        container.addView(createSecondaryButton("📜 سحب المحادثة كاملة (تمرير)", Action.FULL_CHAT_CAPTURE))

        // 4. 📋 من الحافظة
        container.addView(createSecondaryButton("📋 قراءة من الحافظة", Action.CLIPBOARD_CAPTURE))

        // 5. 🧺 السلة (العدد)
        val basketLabel = if (basketCount > 0) "🧺 السلة ($basketCount كتل)" else "🧺 السلة (فارغة)"
        container.addView(createSecondaryButton(basketLabel, Action.OPEN_BASKET))

        // Bottom row: Open Lub / Dismiss
        val bottomRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 0) }
        }

        val btnOpenApp = Button(service).apply {
            text = "🚀 فتح لُب"
            textSize = 12f
            setTextColor(0xFFB0B0B0.toInt())
            setBackgroundColor(0x00000000)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                dismiss()
                onAction(Action.OPEN_APP)
            }
        }

        val btnDismiss = Button(service).apply {
            text = "✖ إخفاء"
            textSize = 12f
            setTextColor(0xFFFF6B6B.toInt())
            setBackgroundColor(0x00000000)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                dismiss()
                onAction(Action.DISMISS)
            }
        }

        bottomRow.addView(btnOpenApp)
        bottomRow.addView(btnDismiss)
        container.addView(bottomRow)

        val params = WindowManager.LayoutParams(
            620,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        container.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                dismiss()
                true
            } else {
                false
            }
        }

        try {
            windowManager.addView(container, params)
            panelView = container
            isShowing = true
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun dismiss() {
        if (isShowing && panelView != null) {
            try {
                windowManager.removeView(panelView)
            } catch (e: Exception) {
                // Ignore
            }
            panelView = null
            isShowing = false
        }
    }
}
