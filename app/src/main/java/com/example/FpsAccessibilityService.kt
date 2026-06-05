package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Locale

class FpsAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var floatingView: FrameLayout? = null
    private var isViewAdded = false

    private val fpsTracker = FpsTracker()
    private var isTracking = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isTracking) return
            
            fpsTracker.recordFrame(frameTimeNanos)
            updateFpsText(fpsTracker.currentFps)
            
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private lateinit var fpsTextView: TextView

    companion object {
        var isServiceRunning = false
            private set
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used, but must be implemented
    }

    override fun onInterrupt() {
        // Not used, but must be implemented
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupFloatingView()
        startTracking()
    }

    private fun setupFloatingView() {
        val density = resources.displayMetrics.density
        val dpToPx = { dp: Int -> (dp * density).toInt() }

        // Container with visual identity: Glassmorphic cybercard with toxic neon drop border
        val container = FrameLayout(this).apply {
            val backgroundDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#EE0A0E17")) // Ultra dark cyberpunk navy background
                cornerRadius = dpToPx(12).toFloat()
                setStroke(2, Color.parseColor("#00FFCC")) // Aqua cyan neon stroke
            }
            background = backgroundDrawable
            setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5))
        }

        // Precise high-contrast neon text
        fpsTextView = TextView(this).apply {
            text = "FPS ---"
            setTextColor(Color.parseColor("#00FFCC"))
            textSize = 13f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        container.addView(fpsTextView)
        floatingView = container

        // Use TYPE_ACCESSIBILITY_OVERLAY! This allows overlay window drawing completely bypassing SYSTEM_ALERT_WINDOW permission
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END // Upper-right corner of the screen initially!
            x = dpToPx(16)
            y = dpToPx(100)
        }

        // Handle draggable movement
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // For Gravity.END, moving left INCREASES params.x, so subtract instead of add
                        params.x = initialX - (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()

                        // Prevent the widget from going out of visible space
                        if (params.y < 0) params.y = 0

                        try {
                            if (isViewAdded) {
                                windowManager.updateViewLayout(floatingView, params)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(floatingView, params)
            isViewAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startTracking() {
        if (!isTracking) {
            isTracking = true
            fpsTracker.reset()
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    private fun stopTracking() {
        isTracking = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun updateFpsText(fps: Float) {
        val colorHex = when {
            fps >= 55f -> "#00FFCC" // Aqua Cyan Neon (Smooth 60+)
            fps >= 40f -> "#FFFF00" // Neon Yellow Warning
            else -> "#FF007F"       // Cyber Neon Magenta-Red (Major stutter)
        }

        try {
            val colorVal = Color.parseColor(colorHex)
            fpsTextView.setTextColor(colorVal)
            (floatingView?.background as? GradientDrawable)?.setStroke(2, colorVal)
        } catch (e: Exception) {
            // Safe fallback
        }

        fpsTextView.text = String.format(Locale.US, "%.1f FPS", fps)
    }

    override fun onDestroy() {
        isServiceRunning = false
        stopTracking()
        if (isViewAdded && floatingView != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isViewAdded = false
        }
        super.onDestroy()
    }
}
