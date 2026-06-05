package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Locale

class FpsOverlayService : Service() {

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupFloatingView()
        startTracking()
    }

    private fun setupFloatingView() {
        val density = resources.displayMetrics.density
        val dpToPx = { dp: Int -> (dp * density).toInt() }

        // Create container layout
        val container = FrameLayout(this).apply {
            // Apply a beautiful rounded dark card background with green neon stroke
            val backgroundDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#E00C0F14")) // Elegant dark overlay 
                cornerRadius = dpToPx(16).toFloat()
                setStroke(2, Color.parseColor("#00FFCC")) // Cyan neon border
            }
            background = backgroundDrawable
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
        }

        // Create TextView for FPS text
        fpsTextView = TextView(this).apply {
            text = "--- FPS"
            setTextColor(Color.parseColor("#00FFCC")) // Toxic cyan neon
            textSize = 14f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        container.addView(fpsTextView)
        floatingView = container

        // Define WindowManager Layout Parameters
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(50)
            y = dpToPx(150)
        }

        // Handle dragging
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
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        
                        // Bounds checking to make sure it doesn't get completely lost
                        if (params.x < 0) params.x = 0
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
            fps >= 55f -> "#00FFCC" // Aqua neon
            fps >= 30f -> "#FFFF00" // Warning Neon yellow
            else -> "#FF3366"       // Alert Neon pink-red
        }
        
        try {
            fpsTextView.setTextColor(Color.parseColor(colorHex))
            (floatingView?.background as? GradientDrawable)?.setStroke(2, Color.parseColor(colorHex))
        } catch (e: Exception) {
            // Safe fallback
        }
        
        fpsTextView.text = String.format(Locale.US, "%.1f FPS", fps)
    }

    override fun onDestroy() {
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
