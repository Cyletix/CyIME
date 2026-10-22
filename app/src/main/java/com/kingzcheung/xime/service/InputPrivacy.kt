package com.kingzcheung.xime.service

import android.view.inputmethod.EditorInfo
import com.kingzcheung.xime.settings.IncognitoState

/**
 * The service's one writer of [IncognitoState]. Each source (the manual switch, the current field,
 * clipboard search) changes only here, and every change re-applies the engine's learning switch,
 * so no source can change without the engine following it. Readers keep reading [IncognitoState].
 */
internal class InputPrivacy(private val applyEngine: (active: Boolean) -> Unit) {
    private var applied: Boolean? = null

    /** The saved switch at start-up; the first field applies it to the engine. */
    fun loadManual(on: Boolean) { IncognitoState.manual = on }

    fun setManual(on: Boolean) { IncognitoState.manual = on; apply() }

    /** A new field: password fields and fields asking for no personalized learning stay private. */
    fun beginEditor(attribute: EditorInfo?) {
        IncognitoState.sensitiveEditor = IncognitoState.isSensitiveEditor(attribute)
        apply()
    }

    /** Its own source: a field change while search is open must not overwrite it, nor be restored by it. */
    fun setClipboardSearch(open: Boolean) { IncognitoState.clipboardSearch = open; apply() }

    private fun apply() {
        val active = IncognitoState.active
        if (applied == active) return
        applied = active
        applyEngine(active)
    }
}
