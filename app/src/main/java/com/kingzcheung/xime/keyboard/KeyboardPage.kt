package com.kingzcheung.xime.keyboard

const val HANDWRITING_SCHEMA_ID = "handwriting"

sealed interface KeyboardPage {
    data class Main(val type: MainType) : KeyboardPage
    data class Panel(val type: PanelType, val returnTo: MainType) : KeyboardPage
    data class Overlay(
        val route: OverlayRoute,
        val backStack: List<OverlayRoute>,
        val behind: KeyboardPage,
        // Symbols return to text even if another tool is opened before the overlay closes.
        // Keep that destination separate from the surface drawn beneath the reveal.
        val returnToTextOnClose: Boolean = false,
    ) : KeyboardPage {
        val isLeaf: Boolean get() = backStack.isEmpty()
    }
}

enum class MainType {
    FULL,
    HANDWRITING,
    STROKE,
    VOICE,
}

enum class PanelType {
    NUMBER,
    COMMON_SYMBOL,
}

sealed interface OverlayRoute {
    data object Menu : OverlayRoute
    data object SchemaList : OverlayRoute
    data class Clipboard(val tab: Int = 0) : OverlayRoute
    data object ToolbarCustomize : OverlayRoute
    data class SplitWords(val text: String) : OverlayRoute
    data object Symbol : OverlayRoute
    data object Emoji : OverlayRoute
    data object Edit : OverlayRoute
    data object ToolPanel : OverlayRoute
}
