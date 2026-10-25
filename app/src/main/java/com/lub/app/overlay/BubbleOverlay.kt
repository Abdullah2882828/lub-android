package com.lub.app.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.lub.app.R
import kotlin.math.abs

/**
 * الفقاعة العائمة بحجم 44dp، قابلة للسحب وتلتصق بالحافة
 * مبنية بـ TYPE_ACCESSIBILITY_OVERLAY وViews عادية
 */
class BubbleOverlay(
    private val service: AccessibilityService,
    private val onClick: () -> Unit
) {

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubbleView: View? = null
    private var isAttached = false
    private var isTemporarilyHidden = false

    private val bubbleSizePx: Int = (44 * service.resources.displayMetrics.density).toInt()

    private val layoutParams = WindowManager.LayoutParams(
        bubbleSizePx,
        bubbleSizePx,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 20
        y = 350
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isAttached || isTemporarilyHidden) return

        val frame = FrameLayout(service).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEE1E1E1E.toInt())
                setStroke(3, 0xFF4A4A4A.toInt())
            }
            background = bg
            alpha = 0.88f

            val icon = TextView(service).apply {
                text = "ل"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 20f
                gravity = Gravity.CENTER
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            addView(icon, lp)
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        frame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    frame.alpha = 1.0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isClick = false
                    }
                    layoutParams.x = initialX + dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    if (isAttached) {
                        try {
                            windowManager.updateViewLayout(frame, layoutParams)
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    frame.alpha = 0.88f
                    if (isClick) {
                        onClick()
                    } else {
                        snapToNearestEdge()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(frame, layoutParams)
            bubbleView = frame
            isAttached = true
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun snapToNearestEdge() {
        if (!isAttached || bubbleView == null) return
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        val currentX = layoutParams.x
        layoutParams.x = if (currentX + bubbleSizePx / 2 < screenWidth / 2) {
            10 // Snap left
        } else {
            screenWidth - bubbleSizePx - 10 // Snap right
        }

        try {
            windowManager.updateViewLayout(bubbleView, layoutParams)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun hide() {
        if (isAttached && bubbleView != null) {
            try {
                windowManager.removeView(bubbleView)
            } catch (e: Exception) {
                // Ignore
            }
            bubbleView = null
            isAttached = false
        }
    }

    fun setTemporarilyHidden(hidden: Boolean) {
        isTemporarilyHidden = hidden
        if (hidden) {
            hide()
        } else {
            show()
        }
    }
}
