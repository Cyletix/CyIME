package com.kingzcheung.xime.service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 输入法窗口带 FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS，状态栏底色不设透明时是系统主题的深色：
 * 应用切全屏隐藏状态栏的那一下，窗口顶上会画出一条状态栏高的黑条（11-05 微信点图片预览）。
 */
class ImeStatusBarStripTest {
    private val source = File("src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt").readText()

    @Test fun windowBackgroundClearsStatusBarColor() {
        val body = source.substringAfter("internal fun applyWindowBackground()").substringBefore("private fun applyCompactMode()")
        assertTrue("applyWindowBackground 没把状态栏底色设成透明", body.contains("win.statusBarColor = android.graphics.Color.TRANSPARENT"))
        assertTrue("状态栏对比度遮罩没关", body.contains("win.isStatusBarContrastEnforced = false"))
    }
}
