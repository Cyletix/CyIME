package com.kingzcheung.xime.keyboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Assignment
import androidx.compose.material.icons.automirrored.twotone.LastPage
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material.icons.twotone.EmojiEmotions
import androidx.compose.material.icons.twotone.FirstPage
import androidx.compose.material.icons.twotone.KeyboardAlt
import androidx.compose.material.icons.twotone.LastPage
import androidx.compose.material.icons.twotone.Paid
import androidx.compose.material.icons.twotone.SelectAll
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.twotone.OpenWith
import androidx.compose.material.icons.twotone.Mic
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Subtitles
import androidx.compose.material.icons.twotone.VolumeUp
import androidx.compose.material.icons.twotone.PictureInPictureAlt
import androidx.compose.material.icons.twotone.BlurOn
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material.icons.twotone.WidthFull
import androidx.compose.material.icons.twotone.Padding
import androidx.compose.material.icons.twotone.Explore
import androidx.compose.material.icons.twotone.SwipeRight
import androidx.compose.material.icons.twotone.Translate
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolbarButton(
    val id: String,
    val label: String,
    val icon: ImageVector
) {
    EMOJI("emoji", "表情", Icons.TwoTone.EmojiEmotions),
    CLIPBOARD("clipboard", "剪贴板", Icons.AutoMirrored.TwoTone.Assignment),
    OCR("ocr", "拍照识别", Icons.Default.DocumentScanner),
    SCHEMA("schema", "输入方式", Icons.TwoTone.KeyboardAlt),
    SYMBOL("symbol", "符号", Icons.TwoTone.Paid),
    // 保留已保存的旧 ID，悬浮模式统一在键盘调节中设置。
    FLOAT("float", "键盘调节", Icons.TwoTone.Tune),
    SELECT_ALL("select_all", "全选", Icons.TwoTone.SelectAll),
    COPY("copy", "复制", Icons.TwoTone.ContentCopy),
    PASTE("paste", "粘贴", Icons.TwoTone.ContentPaste),
    HOME("home", "段首", Icons.TwoTone.FirstPage),
    END("end", "段尾", Icons.AutoMirrored.TwoTone.LastPage),
    HANDWRITING_LOOKUP("handwriting_lookup", "手写", Icons.Outlined.Gesture),
    EDIT("edit", "编辑", Icons.TwoTone.OpenWith),
    VOICE("voice", "语音", Icons.TwoTone.Mic),
    // 以下原为菜单专属；工具栏与菜单共用一组入口，可放在任一处或不使用。
    SETTINGS("settings", "设置", Icons.TwoTone.Settings),
    // 整屏实时字幕（给听力不好的人看周围人说话），打开独立页面。
    CAPTION("caption", "字幕", Icons.TwoTone.Subtitles),
    SOUND("sound", "声音振动", Icons.TwoTone.VolumeUp),
    FLOATING("floating", "悬浮", Icons.TwoTone.PictureInPictureAlt),
    GLASS("glass", "玻璃", Icons.TwoTone.BlurOn),
    INCOGNITO("incognito", "无痕", Icons.TwoTone.VisibilityOff),
    // 图标随状态切换（半角月牙 / 全角满圆），见 WidthShapeIcons.kt。
    // 名称用于自定义列表；键盘上按当前状态显示“全角”或“半角”（见 [stateLabel]）。
    FULL_SHAPE("full_shape", "全角/半角", HalfWidthIcon),
    // 布局手势开关：只在对应布局里出现（见 LayoutGestureMenuItems.kt），其他布局自动隐藏。
    GLIDE("glide", "滑行输入", Icons.Outlined.Gesture),
    T9_FLICK("t9_flick", "九键拂动", Icons.TwoTone.SwipeRight),
    FOURTEEN_SWIPE("fourteen_swipe", "14键滑选", Icons.TwoTone.SwipeRight),
    KANA_HINTS("kana_hints", "假名提示", Icons.TwoTone.Translate),
    FLICK_DISC("flick_disc", "拂动圆盘", Icons.TwoTone.Explore),
    ARRANGE("arrange", "排列", Icons.TwoTone.Padding);

    companion object {
        // 仅在用户还没有保存工具栏偏好时使用，保留已有选择及顺序。
        val DEFAULT_VISIBLE = linkedSetOf(SCHEMA, EMOJI, EDIT, CLIPBOARD, HANDWRITING_LOOKUP, VOICE)

        /** 菜单默认一页 8 项（另加末尾的“排列”）：只放打字时会临时调整的入口，其余可在“排列”中加入。 */
        // 字幕不在这份一页 8 项的清单里；新装用户由 QuickActionLayout.defaultMenu 加入，已保存菜单由 SettingsPreferences 插入一次。
        val DEFAULT_MENU = listOf(SETTINGS, FLOAT, OCR, SOUND, FLOATING, GLASS, INCOGNITO, FULL_SHAPE, ARRANGE)
        /** 布局手势开关：默认排在菜单的“排列”之前（见 QuickActionLayout.defaultMenu），每个布局只显示适用的一两个。 */
        val LAYOUT_GESTURES = listOf(GLIDE, T9_FLICK, FOURTEEN_SWIPE, KANA_HINTS, FLICK_DISC)
        /** 开关类入口：菜单中显示开/关状态，工具栏中开启时高亮。 */
        val TOGGLES = setOf(SOUND, FLOATING, GLASS, INCOGNITO, FULL_SHAPE) + LAYOUT_GESTURES

        val CUSTOMIZABLE = entries.toList()

        /** 已移除的旧版“固定内容”（quick_phrase）并入剪贴板。 */
        private const val LEGACY_QUICK_PHRASE = "quick_phrase"

        fun normalizeIds(ids: List<String>): List<String> =
            ids.map { if (it == LEGACY_QUICK_PHRASE) CLIPBOARD.id else it }.distinct()

        fun fromId(id: String): ToolbarButton? =
            entries.find { it.id == id }
    }
}

data class ToolbarAction(
    val item: ToolbarButtonItem,
    val active: Boolean = false,
    /** 菜单里长按：打开这一项在设置里的对应页；没有对应设置时为 null。 */
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit
)
