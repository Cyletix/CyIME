package com.kingzcheung.xime.keyboard

import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kingzcheung.xime.settings.InputModes
import com.kingzcheung.xime.settings.InputProfile
import com.kingzcheung.xime.settings.InputProfiles
import com.kingzcheung.xime.settings.InputScheme
import com.kingzcheung.xime.settings.LayoutKind

/**
 * 设置页「键盘尺寸与位置」「工具栏与菜单排列」打开的是键盘菜单里同一个调节／排列面板：
 * 页面登记一次请求，由同一页面的只读预览连接取走；收起或离页时取消请求。
 */
object KeyboardPanelLaunch {
    enum class Panel { RESIZE, ARRANGE }

    private const val EXPIRE_MS = 5_000L

    internal const val PREVIEW_EDITOR = "com.cyletix.cyime.settings.preview"
    internal enum class Example(val option: String) {
        Keyboard(PREVIEW_EDITOR), Candidates("$PREVIEW_EDITOR/candidates"), Associations("$PREVIEW_EDITOR/associations"), Hardware("$PREVIEW_EDITOR/hardware"),
        Letters("$PREVIEW_EDITOR/letters"), Keypad("$PREVIEW_EDITOR/keypad"), DoublePinyin("$PREVIEW_EDITOR/double-pinyin");

        /** 只选择显示的键帽，输入引擎继续使用原有语言和方案。 */
        fun sampleLayout(current: InputProfile, currentId: String): String? {
            if (this != Letters && this != Keypad && this != DoublePinyin) return null
            fun matches(profile: InputProfile) = when (this) {
                Letters -> profile.layout.kind == LayoutKind.ALPHABETIC
                Keypad -> profile.layout.kind in setOf(LayoutKind.T9, LayoutKind.MERGED, LayoutKind.KANA_KEYPAD)
                DoublePinyin -> profile.scheme == InputScheme.DOUBLE_PINYIN && profile.layout.kind in setOf(LayoutKind.ALPHABETIC, LayoutKind.MERGED)
                else -> false
            }
            if (currentId.isNotEmpty() && matches(current)) return currentId
            return InputModes.defaultModeOrder.firstOrNull { matches(InputProfiles.current(it)) }
        }
    }
    internal var previewVisible by mutableStateOf(false)
    /** 只读预览键盘被点了：设置页据此打开试打框（真实输入框），键盘随即能打字（11-01「根本点不了」）。 */
    internal var typingRequested by mutableStateOf(false)
    internal fun editorOption(panel: Panel?): String = if (panel == null) PREVIEW_EDITOR else "$PREVIEW_EDITOR/${panel.name}"
    internal fun editorPanel(info: EditorInfo?): Panel? = Panel.entries.firstOrNull { info?.privateImeOptions == editorOption(it) }
    internal fun isPreview(info: EditorInfo?, packageName: String): Boolean =
        info?.packageName == packageName && (Example.entries.any { it.option == info.privateImeOptions } || editorPanel(info) != null)

    private data class Request(val panel: Panel, val time: Long, val owner: Any?)
    private var pending by mutableStateOf<Request?>(null)
    internal val pendingPanel get() = pending?.panel

    @JvmOverloads fun request(panel: Panel, owner: Any? = null) { pending = Request(panel, SystemClock.uptimeMillis(), owner) }

    @JvmOverloads fun cancel(owner: Any? = null) { if (owner == null || pending?.owner === owner) pending = null }

    @JvmOverloads fun take(expected: Panel? = null): Panel? {
        val p = pending ?: return null
        if (expected != null && p.panel != expected) return null
        pending = null
        // 设置页的请求跟随具体预览 View，收起或离页会注销，冷启动也不因超时丢面板。
        return p.panel.takeIf { p.owner != null || SystemClock.uptimeMillis() - p.time < EXPIRE_MS }
    }
}
