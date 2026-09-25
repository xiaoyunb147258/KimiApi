package dev.kimi2api

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * 可拖动悬浮窗，显示网关 IP:端口。位移超过阈值才判定为拖动，避免误触。
 */
class FloatingWindow(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: LinearLayout? = null
    private var titleView: TextView? = null
    private var addrView: TextView? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show(text: String) {
        if (container != null) {
            addrView?.text = text
            return
        }

        val c = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6000000"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#55FFFFFF"))
            }
        }
        val title = TextView(context).apply {
            this.text = "Kimi API"
            setTextColor(Color.parseColor("#FFEB3B"))
            textSize = 12f
        }
        val addr = TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        c.addView(title)
        c.addView(addr)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(140)
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        c.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        try { wm.updateViewLayout(c, lp) } catch (_: Exception) {}
                    }
                }
            }
            true
        }

        try {
            wm.addView(c, lp)
            container = c
            titleView = title
            addrView = addr
        } catch (_: Exception) {
            container = null
        }
    }

    /** 悬浮窗已显示时更新地址文字 */
    fun updateText(text: String) {
        addrView?.text = text
    }

    fun isShown(): Boolean = container != null

    fun hide() {
        container?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        container = null
        titleView = null
        addrView = null
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}
