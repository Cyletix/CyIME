package com.kingzcheung.xime.ui.keyboard

/** Surface arrangement, independent from language, encoding scheme and letter layout. */
enum class KeyboardLayoutStyle(val title: String, val description: String) {
    DEFAULT("默认布局", "沿用 CyIME 原有键位和空格手势"),
    BAIDU_TRADITIONAL("百度传统布局", "传统九键、26 键底排和数字布局；工具栏、配色和符号页不变");
}
