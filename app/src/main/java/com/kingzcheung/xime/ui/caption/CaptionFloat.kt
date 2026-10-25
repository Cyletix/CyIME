package com.kingzcheung.xime.ui.caption

import android.content.Context

/**
 * Captions that keep going over other apps after leaving the caption page (user 2026-10-25,
 * setting 「后台浮窗」). The bar is the call caption bar, shown by [CallCaptionService]: its
 * accessibility window is what lets it keep the microphone in the background, so the float needs
 * that service switched on. The caption page hands its own controller over, so the lines and
 * people carry on unchanged, and takes it back when it is opened again.
 *
 * One bar at a time: when a call starts, the call bar takes over and this one pauses and hides;
 * after hang-up it resumes (see [CallCaptionService]).
 */
internal object CaptionFloat {
    private const val PREFS = "caption_mode"
    private const val KEY = "background_float"

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    /** The caption page exists (it may be in the background); set by [CaptionActivity]. */
    @Volatile var pageAlive = false

    /** The connected call caption service, the only place the float can be drawn. */
    @Volatile var service: CallCaptionService? = null

    /** The caption page's controller while it floats (also while a call has paused it). */
    var controller: CaptionController? = null
        private set

    /** The page is leaving; true when its captions now float (so the page must not pause or release them). */
    fun takeOver(context: Context, page: CaptionController): Boolean {
        val host = service ?: return false
        if (!isOn(context)) return false
        controller = page
        host.showFloat(page)
        return true
    }

    /** The page is back (or a new page opened): returns the floating controller, if any, and hides the bar. */
    fun giveBack(): CaptionController? {
        val c = controller ?: return null
        controller = null
        service?.hideFloat()
        return c
    }

    /** Closed from the bar: captions stop; the page continues them when it is opened again. */
    fun closed() {
        val c = controller ?: return
        controller = null
        if (pageAlive) { c.resumeOnForeground = true; c.pause() } else c.release()
    }
}
