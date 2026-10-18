package com.kingzcheung.xime.ui.caption

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.kingzcheung.xime.util.FileLogger
import com.kingzcheung.xime.util.PermissionHelper
import java.util.concurrent.Executor

/**
 * Captions during calls (phone, WeChat voice/video). While a call is active Android gives the
 * microphone to the call and ordinary apps record silence; an accessibility service whose window is
 * on top may capture alongside it ("sharing audio input", developer.android.com). So the bar lives
 * here: it appears by itself when the system enters a call, sits at the bottom like a subtitle, and
 * goes away on hang-up. It reads no window content; it only watches the audio mode.
 */
class CallCaptionService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private var controller: CaptionController? = null
    private var overlay: FrameLayout? = null
    private var textView: TextView? = null
    private var inCall = false
    private var dismissedThisCall = false
    private var modeListener: Any? = null
    private var lastLevelLog = 0L

    private val poll = object : Runnable {
        override fun run() {
            onAudioMode(audio.mode)
            main.postDelayed(this, 1500)
        }
    }
    private val render = object : Runnable {
        override fun run() {
            renderCaptions()
            main.postDelayed(this, 250)
        }
    }

    override fun onServiceConnected() {
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= 31) {
            val listener = AudioManager.OnModeChangedListener { mode -> main.post { onAudioMode(mode) } }
            audio.addOnModeChangedListener(Executor { it.run() }, listener)
            modeListener = listener
        }
        // Mode callbacks are not delivered by every OEM; a slow poll keeps entry automatic everywhere.
        main.post(poll)
        FileLogger.i(TAG, "connected sdk=${Build.VERSION.SDK_INT}")
    }

    private fun onAudioMode(mode: Int) {
        val call = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        if (call == inCall) return
        inCall = call
        FileLogger.i(TAG, "call=$call mode=$mode")
        if (call) { dismissedThisCall = false; startCaptions() } else stopCaptions()
    }

    private fun startCaptions() {
        if (dismissedThisCall || overlay != null) return
        if (!PermissionHelper.hasRecordAudioPermission(this)) { FileLogger.w(TAG, "no microphone permission"); return }
        showOverlay()
        val c = controller ?: CaptionController(this).also { controller = it }
        c.start()
        main.post(render)
    }

    private fun stopCaptions() {
        main.removeCallbacks(render)
        controller?.release()
        controller = null
        removeOverlay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
        val text = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setLineSpacing(0f, 1.15f)
            maxLines = 3
            gravity = Gravity.START or Gravity.BOTTOM
            setPadding(dp(16f), dp(10f), dp(44f), dp(10f))
            text = "通话字幕已开启，开免提或外放后对方说的话会显示在这里"
        }
        val close = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            this.text = "×"
            setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
            contentDescription = "关闭本次通话字幕"
            setOnClickListener { dismissedThisCall = true; stopCaptions() }
        }
        val frame = FrameLayout(this).apply {
            background = GradientDrawable().apply { setColor(Color.argb(170, 0, 0, 0)); cornerRadius = dp(14f).toFloat() }
            addView(text, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            addView(close, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM
            x = 0
            y = dp(120f) // above the call buttons
            horizontalMargin = 0.03f
        }
        // Drag up or down to move it off the call buttons.
        var startY = 0f
        var startOffset = 0
        frame.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = e.rawY; startOffset = params.y; true }
                MotionEvent.ACTION_MOVE -> {
                    params.y = (startOffset - (e.rawY - startY)).toInt().coerceAtLeast(0)
                    runCatching { wm.updateViewLayout(frame, params) }
                    true
                }
                else -> false
            }
        }
        runCatching { wm.addView(frame, params) }.onFailure { FileLogger.e(TAG, "overlay failed", it); return }
        overlay = frame
        textView = text
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        runCatching { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view) }
        overlay = null
        textView = null
    }

    /** The last few lines, each prefixed with the speaker's name in their colour. */
    private fun renderCaptions() {
        val c = controller ?: return
        val view = textView ?: return
        val now = System.currentTimeMillis()
        if (now - lastLevelLog > 5000) {
            lastLevelLog = now
            FileLogger.i(TAG, "status=${c.status} level=${"%.3f".format(c.amplitude)} lines=${c.segments.size}")
        }
        if (c.status == CaptionController.Status.Error) { view.text = c.message; return }
        val lines = c.segments.filter { it.text.isNotEmpty() }.takeLast(3)
        if (lines.isEmpty()) return
        val out = SpannableStringBuilder()
        for ((i, line) in lines.withIndex()) {
            if (i > 0) out.append('\n')
            val speaker = c.speakers.firstOrNull { it.id == line.speakerId }
            if (speaker != null) {
                val start = out.length
                out.append(speaker.name).append("：")
                out.setSpan(ForegroundColorSpan(CaptionController.COLORS[speaker.colorIndex.coerceIn(0, 7)].toInt()), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            out.append(line.text)
            if (!line.confirmed) out.append("…")
        }
        view.text = out
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* audio mode drives everything */ }
    override fun onInterrupt() {}

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= 31) (modeListener as? AudioManager.OnModeChangedListener)?.let { audio.removeOnModeChangedListener(it) }
        stopCaptions()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallCaption"

        fun isEnabled(context: Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { it.endsWith(CallCaptionService::class.java.name) }
        }
    }
}
