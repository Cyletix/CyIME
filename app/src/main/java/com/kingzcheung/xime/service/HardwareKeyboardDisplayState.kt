package com.kingzcheung.xime.service

/** Connection and the user's visible keyboard choice have independent lifetimes. */
internal data class HardwareKeyboardDisplayState(
    val connected: Boolean = false,
    val screenKeyboardRequested: Boolean = false,
) {
    val compact: Boolean get() = connected && !screenKeyboardRequested

    /** Focus/configuration refreshes preserve an explicit expansion; a new connection does not. */
    fun withConnection(value: Boolean): HardwareKeyboardDisplayState = copy(
        connected = value,
        screenKeyboardRequested = value && connected && screenKeyboardRequested,
    )

    fun showOnScreen(): HardwareKeyboardDisplayState =
        if (connected) copy(screenKeyboardRequested = true) else this

    fun collapseToToolbar(): HardwareKeyboardDisplayState =
        if (connected) copy(screenKeyboardRequested = false) else this
}
