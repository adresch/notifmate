package com.notifmate.helper

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.notifmate.R
import com.notifmate.model.NotificationItem

class OverlayHelper(private val context: Context) {
    private var windowManager: WindowManager? = null
    private val edgeViews = mutableListOf<View>()
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var countdownHandler = Handler(Looper.getMainLooper())

    fun showOverlay(currentNoti: NotificationItem, autoCloseTime: Long = 15000) {
        if (edgeViews.isNotEmpty() || overlayView != null) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(context)

        overlayView = inflater.inflate(R.layout.overlay, null)

        val closeButton = overlayView?.findViewById<Button>(R.id.overlay_delete_button)
        closeButton?.text = "Hide (${autoCloseTime / 1000}s)"
        closeButton?.setOnClickListener { removeOverlay() }

        startCountdown(closeButton, autoCloseTime)

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager?.addView(overlayView, overlayParams)

        val layoutParamsType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

        val edgeThickness = 20
        val baseColor = 0xFFFF0000.toInt()

        val (screenWidth, screenHeight) = getRealScreenSize()

        val edges = listOf(
            WindowManager.LayoutParams(
                screenWidth - (2 * edgeThickness), edgeThickness,
                layoutParamsType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL },

            WindowManager.LayoutParams(
                screenWidth - (2 * edgeThickness), edgeThickness,
                layoutParamsType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL },

            WindowManager.LayoutParams(
                edgeThickness, screenHeight,
                layoutParamsType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL },

            WindowManager.LayoutParams(
                edgeThickness, screenHeight,
                layoutParamsType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        )

        edges.forEach { params ->
            val edgeView = LinearLayout(context).apply {
                setBackgroundColor(baseColor)
                alpha = 0f
            }
            windowManager?.addView(edgeView, params)
            edgeViews.add(edgeView)
        }

        overlayView?.findViewById<TextView>(R.id.overlay_header_text)?.text = currentNoti.appName
        overlayView?.findViewById<TextView>(R.id.overlay_title_text)?.text = currentNoti.title
        overlayView?.findViewById<TextView>(R.id.overlay_description_text)?.text = currentNoti.text

        startFadingAnimation()
        handler.postDelayed({ removeOverlay() }, autoCloseTime)
    }

    /**
     * Returns Pair(screenWidth, screenHeight)
     */
    private fun getRealScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = context.getSystemService(WindowManager::class.java)
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay
                .getRealMetrics(displayMetrics)
            displayMetrics.widthPixels to displayMetrics.heightPixels
        }
    }

    private fun startCountdown(button: Button?, autoCloseTime: Long) {
        var secondsRemaining = (autoCloseTime / 1000).toInt()

        val countdownRunnable = object : Runnable {
            override fun run() {
                if (secondsRemaining > 0) {
                    button?.text = "Hide (${secondsRemaining}s)"
                    secondsRemaining--
                    countdownHandler.postDelayed(this, 1000)
                }
            }
        }

        countdownHandler.post(countdownRunnable)
    }

    private fun startFadingAnimation() {
        val fadeAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val alphaValue = animator.animatedValue as Float
                edgeViews.forEach { it.alpha = alphaValue }
            }
        }
        fadeAnimator.start()
    }

    fun removeOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }

        edgeViews.forEach { windowManager?.removeView(it) }
        edgeViews.clear()

        handler.removeCallbacksAndMessages(null)
        countdownHandler.removeCallbacksAndMessages(null)
    }
}
