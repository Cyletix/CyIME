package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp

/** 设置内容的最大宽高比：平板横屏 2560×1600 时约为屏宽的 80%（2026-10-12 用户要求）。 */
internal const val SETTINGS_MAX_ASPECT = 1.28f

/** 内容区宽度：不超过窗口高度 × [SETTINGS_MAX_ASPECT]；竖屏和本来就不宽的窗口不变。 */
internal fun settingsContentWidth(maxWidth: Dp, maxHeight: Dp): Dp = minOf(maxWidth, maxHeight * SETTINGS_MAX_ASPECT)

/** 横屏时把设置界面限制在居中的内容区里，两侧用页面底色留空。 */
@Composable
fun SettingsWidthLimit(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxHeight().width(settingsContentWidth(maxWidth, maxHeight)).testTag("settings-content")) {
            content()
        }
    }
}
