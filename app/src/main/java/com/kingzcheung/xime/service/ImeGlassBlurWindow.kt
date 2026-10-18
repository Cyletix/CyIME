package com.kingzcheung.xime.service

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import com.kingzcheung.xime.util.FileLogger
import com.kingzcheung.xime.ui.theme.SamsungWindowBlur
import java.util.function.Consumer

/**
 * A non-interactive blur surface behind the IME, bounded by the visible keyboard card.
 *
 * Window background blur uses the whole DecorView bounds; a smaller drawable outline only
 * changes its corner radius. A sized TYPE_APPLICATION_MEDIA child supplies the correct bounds
 * without changing the full-screen IME host, its insets, or its touch region. Android composes
 * this child below its parent, so the keyboard's text and keys never enter the blur.
 *
 * Android and optional One UI window blur operate on the live background. No app screenshot,
 * frozen bitmap, hidden-API exemption or global setting is used.
 */
@MainThread
internal class ImeGlassBlurWindow(
    private val context: Context,
    private val role: String = "keyboard",
    private val parentDecor: () -> View?,
) {
    private var enabled = false
    private var visible = false
    private var disposed = false
    private var blurRadiusPx = 0
    private var cornerRadiusPx = 0f
    private var region: Rect? = null
    private var dialog: Dialog? = null
    private var background: GradientDrawable? = null
    private var boundToken: IBinder? = null
    private var failedToken: IBinder? = null
    private var appliedRegion: Rect? = null
    private var appliedBlurRadius = -1
    private var appliedCornerRadius = -1f
    private var appliedSamsungBlur = false
    private var opacity = 1f
    private var appliedOpacity = -1f
    private var observedParent: View? = null
    private var availabilityManager: WindowManager? = null
    private var availabilityListener: Consumer<Boolean>? = null
    private var availabilityGeneration = 0
    private var registeringAvailability = false
    private var availabilityRegistrationFailed = false
    private var blurAvailable = false

    private val parentAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = refresh()
        override fun onViewDetachedFromWindow(view: View) {
            removeWindow()
            failedToken = null
        }
    }
    private val parentLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        refresh()
    }

    /** The rectangle is in the parent IME window's coordinates, as returned by positionInWindow. */
    fun setRegion(bounds: Rect?) {
        val next = bounds?.takeUnless { it.isEmpty }?.let(::Rect)
        if (region == next) return
        region = next
        refresh()
    }

    /** Radius values are physical pixels; the window API supports a uniform corner radius. */
    fun configure(enabled: Boolean, blurRadiusPx: Int, cornerRadiusPx: Float = 0f) {
        val radius = blurRadiusPx.coerceAtLeast(0)
        val corner = cornerRadiusPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        if (this.enabled == enabled && this.blurRadiusPx == radius && this.cornerRadiusPx == corner) return
        if (this.enabled != enabled) failedToken = null
        this.enabled = enabled
        this.blurRadiusPx = radius
        this.cornerRadiusPx = corner
        refresh()
    }

    /** Whole-keyboard opacity fades the blur with the keys, so lowering it reveals sharp host text. */
    fun setOpacity(alpha: Float) {
        val next = alpha.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        if (opacity == next) return
        opacity = next
        refresh()
    }

    fun setVisible(visible: Boolean) {
        if (this.visible != visible) failedToken = null
        this.visible = visible
        refresh()
    }

    /** Call after a parent-window replacement or show; repeated unchanged calls do no relayout. */
    fun refresh() {
        if (disposed || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !enabled || !visible || blurRadiusPx == 0) {
            stopObservingAvailability()
            observeParent(null)
            removeWindow()
            return
        }
        observeAvailability()
        if (!blurAvailable && !SamsungWindowBlur.isAvailable) {
            observeParent(null)
            removeWindow()
            return
        }
        val parent = parentDecor()
        observeParent(parent)
        val token = parent?.windowToken
        val bounds = region
        if (parent == null || !parent.isAttachedToWindow || token == null || bounds == null) {
            removeWindow()
            return
        }
        if (token == failedToken) return
        if (dialog != null && boundToken != token) removeWindow()

        // A media child is positioned in its attached parent's frame. Compose already reports
        // that window-local space; adding the parent's screen origin here applies it twice.
        try {
            val current = dialog ?: createWindow(parent, token).also {
                dialog = it
                boundToken = token
            }
            updateWindow(current.window ?: return, bounds)
            if (!current.isShowing) {
                current.show()
                FileLogger.i(TAG, "Keyboard blur child shown: role=$role, parentBounds=${bounds.toShortString()}, radius=$blurRadiusPx")
            }
        } catch (error: RuntimeException) {
            failedToken = token
            removeWindow()
            FileLogger.w(TAG, "Keyboard blur unavailable; retaining live transparency", error)
        }
    }

    fun dispose() {
        disposed = true
        visible = false
        stopObservingAvailability()
        observeParent(null)
        removeWindow()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun observeAvailability() {
        if (availabilityListener != null || availabilityRegistrationFailed) return
        registeringAvailability = true
        try {
            val manager = checkNotNull(context.getSystemService(WindowManager::class.java))
            val generation = ++availabilityGeneration
            val listener = Consumer<Boolean> { available ->
                // A queued callback from a removed listener must not recreate a hidden window.
                if (generation != availabilityGeneration || disposed) return@Consumer
                val changed = blurAvailable != available
                blurAvailable = available
                if (available && changed) failedToken = null
                // Registration can invoke the initial callback synchronously. The outer refresh
                // consumes that value; another refresh here would register/create recursively.
                if (changed && !registeringAvailability) refresh()
            }
            availabilityManager = manager
            availabilityListener = listener
            // Query once per visible/enabled lifecycle; pointer updates use the cached value.
            blurAvailable = manager.isCrossWindowBlurEnabled
            manager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
        } catch (error: RuntimeException) {
            stopObservingAvailability()
            // Avoid retrying a rejected registration on every position/layout update.
            availabilityRegistrationFailed = true
            FileLogger.w(TAG, "Unable to observe keyboard blur; retaining live transparency", error)
        } finally {
            registeringAvailability = false
        }
    }

    private fun stopObservingAvailability() {
        val manager = availabilityManager
        val listener = availabilityListener
        availabilityManager = null
        availabilityListener = null
        availabilityGeneration++
        blurAvailable = false
        availabilityRegistrationFailed = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager != null && listener != null) {
            try {
                manager.removeCrossWindowBlurEnabledListener(listener)
            } catch (error: RuntimeException) {
                FileLogger.w(TAG, "Unable to remove keyboard blur availability listener", error)
            }
        }
    }
    private fun observeParent(parent: View?) {
        if (observedParent === parent) return
        observedParent?.removeOnAttachStateChangeListener(parentAttachListener)
        observedParent?.removeOnLayoutChangeListener(parentLayoutListener)
        observedParent = parent
        parent?.addOnAttachStateChangeListener(parentAttachListener)
        parent?.addOnLayoutChangeListener(parentLayoutListener)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createWindow(parent: View, token: IBinder): Dialog {
        // This public platform theme is floating, translucent, titleless and does not dim.
        val windowContext = parent.display?.let(context::createDisplayContext) ?: context
        val child = Dialog(windowContext, android.R.style.Theme_InputMethod)
        child.setCancelable(false)
        child.setCanceledOnTouchOutside(false)
        val window = checkNotNull(child.window)
        window.requestFeature(Window.FEATURE_NO_TITLE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
        }
        window.setBackgroundDrawable(background)
        window.setDecorFitsSystemWindows(false)
        window.setDimAmount(0f)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        window.attributes = window.attributes.apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA
            this.token = token
            gravity = Gravity.TOP or Gravity.LEFT
            flags = (flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED) and
                (WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN).inv()
            format = PixelFormat.TRANSLUCENT
            windowAnimations = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
            setTitle("CyIME keyboard blur: $role")
        }
        window.decorView.setPadding(0, 0, 0, 0)
        window.decorView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        window.setContentView(View(windowContext))
        // Decor inflation can restore the theme animation when the prior value was zero.
        window.setWindowAnimations(0)
        return child
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun updateWindow(window: Window, bounds: Rect) {
        if (appliedRegion != bounds) {
            window.attributes = window.attributes.apply {
                x = bounds.left
                y = bounds.top
                width = bounds.width()
                height = bounds.height()
            }
            appliedRegion = Rect(bounds)
        }
        if (appliedOpacity != opacity) {
            window.attributes = window.attributes.apply { alpha = opacity }
            appliedOpacity = opacity
        }
        val corner = cornerRadiusPx.coerceAtMost(minOf(bounds.width(), bounds.height()) / 2f)
        val cornerChanged = appliedCornerRadius != corner
        if (cornerChanged) {
            background?.cornerRadius = corner
            window.decorView.invalidateOutline()
            appliedCornerRadius = corner
        }
        val useSamsung = !blurAvailable && SamsungWindowBlur.isAvailable
        if (appliedBlurRadius != blurRadiusPx || appliedSamsungBlur != useSamsung ||
            (useSamsung && cornerChanged)) {
            // DecorView itself listens for cross-window blur availability changes and switches
            // to radius zero when the device disables blur, preserving the transparent fallback.
            if (useSamsung) {
                window.setBackgroundBlurRadius(0)
                check(SamsungWindowBlur.apply(window.decorView, blurRadiusPx, corner)) { "One UI window blur rejected" }
            } else {
                if (appliedSamsungBlur) SamsungWindowBlur.clear(window.decorView)
                window.setBackgroundBlurRadius(blurRadiusPx)
            }
            appliedSamsungBlur = useSamsung
            appliedBlurRadius = blurRadiusPx
        }
    }

    private fun removeWindow() {
        val previous = dialog
        dialog = null
        background = null
        boundToken = null
        appliedRegion = null
        appliedBlurRadius = -1
        appliedCornerRadius = -1f
        appliedOpacity = -1f
        if (previous != null) {
            if (appliedSamsungBlur) previous.window?.decorView?.let(SamsungWindowBlur::clear)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) previous.window?.setBackgroundBlurRadius(0)
            } catch (error: RuntimeException) {
                FileLogger.w(TAG, "Unable to clear detached keyboard blur", error)
            }
            try {
                previous.dismiss()
            } catch (error: RuntimeException) {
                FileLogger.w(TAG, "Unable to dismiss detached keyboard blur", error)
            }
        }
        appliedSamsungBlur = false
    }

    private companion object {
        const val TAG = "ImeGlassBlurWindow"
    }
}
