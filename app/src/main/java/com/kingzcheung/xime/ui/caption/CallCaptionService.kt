package com.kingzcheung.xime.ui.caption

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kingzcheung.xime.util.FileLogger
import com.kingzcheung.xime.util.PermissionHelper
import java.util.concurrent.Executor

/**
 * Captions during calls (phone, WeChat voice/video). While a call is active Android gives the
 * microphone to the call and ordinary apps record silence; an accessibility service whose window is
 * on top may capture alongside it ("sharing audio input", developer.android.com). So the bar lives
 * here: it appears by itself when the system enters a call, sits at the bottom like a subtitle, and
 * goes away on hang-up. It reads no window content; it only watches the audio mode and route.
 * Only the other party is captioned: it listens only on speakerphone and hides the holder's own voice.
 */
class CallCaptionService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private var controller: CaptionController? = null
    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var inCall = false
    private var dismissedThisCall = false
    private var modeListener: Any? = null
    private var lastLevelLog = 0L
    private var lastStartAt = 0L
    private var route by mutableStateOf(CallRoute.Earpiece)
    // The caption page's captions floating after it was left (see [CaptionFloat]).
    private var floatController: CaptionController? = null
    private var floatOverlay: View? = null
    /** A call paused the floating captions; they go on after hang-up. */
    private var floatResumeAfterCall = false

    // A ComposeView outside an activity needs its own lifecycle and saved-state owners.
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val poll = object : Runnable {
        override fun run() {
            onAudioMode(audio.mode)
            if (inCall) followRoute()
            logLevel()
            main.postDelayed(this, 1500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
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
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        CaptionFloat.service = this
        FileLogger.i(TAG, "connected sdk=${Build.VERSION.SDK_INT}")
    }

    private fun onAudioMode(mode: Int) {
        val call = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        if (call == inCall) return
        inCall = call
        FileLogger.i(TAG, "call=$call mode=$mode")
        if (call) {
            // One bar at a time: the call bar takes the microphone and the screen; the floating page
            // captions pause and hide until hang-up.
            floatController?.let { f ->
                val active = f.status == CaptionController.Status.Listening || f.status == CaptionController.Status.Starting ||
                    f.status == CaptionController.Status.Held
                floatResumeAfterCall = floatResumeAfterCall || active
                if (active) { f.resumeOnForeground = true; f.pause() }
                removeFloatOverlay()
            }
            dismissedThisCall = false; startCaptions()
        } else {
            stopCaptions()
            floatController?.let { f ->
                addFloatOverlay(f)
                if (floatResumeAfterCall) { f.resumeOnForeground = false; f.start() }
                floatResumeAfterCall = false
            }
        }
    }

    /** The caption page left with 「后台浮窗」 on: its captions go on in a bar here. */
    fun showFloat(c: CaptionController) {
        main.post {
            floatController = c
            if (inCall) {
                floatResumeAfterCall = true
                c.resumeOnForeground = true; c.pause()
            } else addFloatOverlay(c)
        }
    }

    fun hideFloat() {
        main.post {
            removeFloatOverlay()
            floatController = null
            floatResumeAfterCall = false
        }
    }

    private fun addFloatOverlay(c: CaptionController) {
        if (floatOverlay != null) return
        val added = addBar(c, floating = true, onClose = {
            removeFloatOverlay(); floatController = null; floatResumeAfterCall = false
            CaptionFloat.closed()
        }) ?: return
        floatOverlay = added.first
    }

    private fun removeFloatOverlay() {
        val view = floatOverlay ?: return
        runCatching { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view) }
        floatOverlay = null
    }

    /**
     * Ordinary apps cannot record call audio (only system apps hold CAPTURE_AUDIO_OUTPUT; playback
     * capture excludes USAGE_VOICE_COMMUNICATION), so the bar hears the other party through the
     * loudspeaker. It records only while the call is on speakerphone; on the earpiece or a headset
     * the microphone would hear nothing but the person holding the phone.
     */
    private fun currentRoute(): CallRoute {
        val type = if (Build.VERSION.SDK_INT >= 31) audio.communicationDevice?.type else null
        return when (type) {
            // Some phones report the loudspeaker as SPEAKER_SAFE; only real headsets block listening.
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> CallRoute.Speaker
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> CallRoute.Earpiece
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID -> CallRoute.Headset
            // No or an unfamiliar device: trust the speakerphone flag rather than silently never listening.
            else -> @Suppress("DEPRECATION") if (audio.isSpeakerphoneOn || type != null) CallRoute.Speaker else CallRoute.Earpiece
        }
    }

    private fun followRoute() {
        val c = controller ?: return
        val next = currentRoute()
        if (next != route) FileLogger.i(TAG, "route=$next")
        route = next
        // Held: waiting for keyboard dictation to end; the service restarts it, so no start or retry here.
        val listening = c.status == CaptionController.Status.Listening || c.status == CaptionController.Status.Starting ||
            c.status == CaptionController.Status.Held
        val now = android.os.SystemClock.elapsedRealtime()
        // A failed start (speech service busy, model still loading) is retried every 10 s during the
        // call instead of leaving the bar dead until hang-up.
        val retry = c.status == CaptionController.Status.Error && now - lastStartAt > 10_000
        if (next == CallRoute.Speaker && !listening && (c.status != CaptionController.Status.Error || retry)) {
            lastStartAt = now
            if (retry) FileLogger.i(TAG, "retry after error")
            c.start()
        } else if (next != CallRoute.Speaker && listening) c.pause()
    }

    private fun startCaptions() {
        if (dismissedThisCall || overlay != null) return
        if (!PermissionHelper.hasRecordAudioPermission(this)) { FileLogger.w(TAG, "no microphone permission"); return }
        controller = controller ?: CaptionController(this)
        showOverlay()
        followRoute()
    }

    private fun stopCaptions() {
        controller?.release()
        controller = null
        removeOverlay()
    }

    private fun showOverlay() {
        val c = controller ?: return
        val added = addBar(c, floating = false, onClose = { dismissedThisCall = true; stopCaptions() }) ?: return
        overlay = added.first
        params = added.second
    }

    /** The subtitle bar at the bottom of the screen, for call captions or the floating caption page. */
    private fun addBar(c: CaptionController, floating: Boolean, onClose: () -> Unit): Pair<View, WindowManager.LayoutParams>? {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM
            y = dp(if (floating) 80f else 120f) // above the call buttons / the navigation bar
            horizontalMargin = 0.03f
        }
        lateinit var view: ComposeView
        view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@CallCaptionService)
            setViewTreeSavedStateRegistryOwner(this@CallCaptionService)
            setContent {
                CallCaptionBar(c, if (floating) CallRoute.Speaker else route,
                    // Drag up or down to move it off the call buttons.
                    onDrag = { dy ->
                        layout.y = (layout.y - dy).toInt().coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(view, layout) }
                    },
                    onClose = onClose,
                    floating = floating,
                    onOpen = if (floating) ({ CaptionActivity.open(this@CallCaptionService) }) else null)
            }
        }
        runCatching { wm.addView(view, layout) }.onFailure { FileLogger.e(TAG, "overlay failed", it); return null }
        return view to layout
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        runCatching { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view) }
        overlay = null
        params = null
    }

    private fun logLevel() {
        val c = controller ?: return
        val now = System.currentTimeMillis()
        if (now - lastLevelLog < 5000) return
        lastLevelLog = now
        FileLogger.i(TAG, "status=${c.status} route=$route level=${"%.3f".format(c.amplitude)} lines=${c.segments.size}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* audio mode drives everything */ }
    override fun onInterrupt() {}

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        main.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= 31) (modeListener as? AudioManager.OnModeChangedListener)?.let { audio.removeOnModeChangedListener(it) }
        stopCaptions()
        // Switched off while floating: the captions stop with the bar.
        if (CaptionFloat.service === this) CaptionFloat.service = null
        removeFloatOverlay(); floatController = null
        CaptionFloat.closed()
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
