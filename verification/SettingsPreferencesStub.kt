package com.kingzcheung.xime.settings
import android.content.Context
import com.kingzcheung.xime.ui.keyboard.keyboardHeightBounds

// Minimal test adapter for the SettingsPreferences methods read from the repository.
object SettingsPreferences {
    fun getPrefsPublic(c: Context) = c.prefs
    private fun suffix(l: Boolean) = if(l) "_landscape" else ""
    fun getKeyboardHeightDp(c: Context, l: Boolean): Int {
        val stored = c.prefs.getInt("keyboard_height_dp"+suffix(l), -1)
        if(stored > 0) return if(l) stored.coerceIn(keyboardHeightBounds(c.resources.configuration.screenHeightDp,true)) else stored
        val alternate = c.prefs.getInt("keyboard_height_dp"+suffix(!l), -1)
        if(l && alternate > 0) return alternate.coerceIn(keyboardHeightBounds(c.resources.configuration.screenHeightDp,true))
        return getDefaultKeyboardHeightDp(c,l)
    }
    fun getDefaultKeyboardHeightDp(c: Context,l: Boolean): Int {
        val cfg=c.resources.configuration
        val d=maxOf(cfg.screenWidthDp,cfg.screenHeightDp)*(if(l) 25 else 35)/100
        return if(l) d.coerceIn(keyboardHeightBounds(cfg.screenHeightDp,true)) else d
    }
    fun setKeyboardHeightDp(c: Context,h: Int,l: Boolean) { c.prefs.edit().putInt("keyboard_height_dp"+suffix(l),h).apply() }
    fun setFloatingWidthDp(c: Context,h: Int,l: Boolean) { c.prefs.edit().putInt("floating_width_dp"+suffix(l),h).apply() }
    fun setFloatingOffsetX(c: Context,h: Int,l: Boolean) { c.prefs.edit().putInt("floating_offset_x"+suffix(l),h).apply() }
    fun setFloatingOffsetY(c: Context,h: Int,l: Boolean) { c.prefs.edit().putInt("floating_offset_y"+suffix(l),h).apply() }
    fun getKeyboardBottomPaddingDp(c: Context) = c.prefs.getInt("keyboard_bottom_padding_dp",0)
    fun setKeyboardBottomPaddingDp(c: Context,h: Int) { c.prefs.edit().putInt("keyboard_bottom_padding_dp",h).apply() }
    fun setKeyboardOpacity(c: Context,o: Float) { c.prefs.edit().putFloat("keyboard_opacity",o).apply() }
    fun setFloatingMode(c: Context,v: Boolean,l: Boolean) { c.prefs.edit().putBoolean("floating_mode"+suffix(l),v).apply() }
}
