package com.lub.app.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.lub.app.service.CaptureBus

/**
 * شارة شفافة تظهر أعلى الشاشة أثناء التمرير الآلي لعرض التقدم وزر إيقاف فوري للشفافية
 */
class CaptureBadge(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var badgeView: View? = null
    private var isAttached = false

    fun show(message: String, onCancel: () -> Unit) {
        if (isAttached && badgeView != null) {
            badgeView?.findViewById<TextView>(101)?.text = message
            return
        }

        val layout = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 16, 32, 16)

            val bg = GradientDrawable().apply {
                setColor(0xDD151515.toInt())
                cornerRadius = 32f
                setStroke(2, 0xFF444444.toInt())
            }
            background = bg

            val textView = TextView(service).apply {
                id = 101
                text = message
                setTextColor(0xFFF2F2F2.toInt())
                textSize = 13f
                gravity = Gravity.RIGHT
            }
            addView(textView)

            val space = View(service).apply {
                layoutParams = LinearLayout.LayoutParams(24, 1)
            }
            addView(space)

            val stopBtn = Button(service).apply {
                text = "إيقاف 🛑"
                setTextColor(0xFFFF5252.toInt())
                textSize = 12f
                setBackgroundColor(0x00000000)
                setOnClickListener {
                    onCancel()
                    hide()
                }
            }
            addView(stopBtn)
        }

        val layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        try {
            windowManager.addView(layout, params)
            badgeView = layout
            isAttached = true
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun hide() {
        if (isAttached && badgeView != null) {
            try {
                windowManager.removeView(badgeView)
            } catch (e: Exception) {
                // Ignore
            }
            badgeView = null
            isAttached = false
        }
    }
}
