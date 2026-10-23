package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.ui.settings.LayoutDisplaySettingsContent
import com.kingzcheung.xime.ui.settings.SettingsSearchFocus
import com.kingzcheung.xime.ui.settings.SettingsSearchIndex
import com.kingzcheung.xime.ui.settings.ThemeSettingsContent
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 设置搜索点开结果后的定位：手机大小的窗口里，目标项原本在屏幕外，要翻下去、滚到可见处并高亮。
 * [jumpScrollsDeepItemIntoView] 是普通单测；[renderHighlight] 只在设置了 CYIME_RENDER_OUT=<目录> 时出图，
 * 每页每种玻璃一张，横排浅色、深色、纯白、纯黑四个主题，截在高亮最亮的时候。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsSearchJumpRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val themes = listOf(Triple("soft_blue", false, "浅色"), Triple("soft_blue", true, "深色"),
        Triple("pure_black", false, "纯白"), Triple("pure_black", true, "纯黑"))

    /** 页面 → 要定位的搜索结果标题。 */
    private val pages: List<Pair<String, String>> = listOf(
        "theme" to "按键字号",
        "gestures" to "长按语言键弹出列表",
        "candidates" to "每页候选词数",
    )

    @Composable
    private fun Page(page: String) = when (page) {
        "theme" -> ThemeSettingsContent(onBack = {})
        "gestures" -> LayoutDisplaySettingsContent(onBack = {}, initialPage = "gestures")
        else -> LayoutDisplaySettingsContent(onBack = {}, initialPage = "candidates")
    }

    private fun open(title: String) = SettingsSearchFocus.open(SettingsSearchIndex.first { it.title == title })

    @Test fun jumpScrollsDeepItemIntoView() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        rule.mainClock.autoAdvance = false
        open("按键字号")
        rule.setContent { XimeTheme(darkTheme = false, themeId = "soft_blue") { Page("theme") } }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        rule.onNodeWithTag("settings-search-anchor:按键字号").assertIsDisplayed()
        assertNull("定位后请求要清掉，只亮一次", SettingsSearchFocus.request)
    }

    @Test fun renderHighlight() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(context)
        File(out!!).mkdirs()
        // 同一时刻只画一个主题：四份页面同时在场时请求只会被其中一份认领。
        val frames = pages.flatMap { (page, title) -> listOf(false, true).flatMap { glass -> themes.map { Triple(page to title, glass, it) } } }
        val shown = mutableIntStateOf(-1)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val index = shown.intValue
            if (index >= 0) {
                val (pageAndTitle, _, theme) = frames[index]
                Box(Modifier.size(360.dp, 760.dp).background(Color.Gray)) {
                    androidx.compose.runtime.key(index) {
                        XimeTheme(darkTheme = theme.second, themeId = theme.first) { Page(pageAndTitle.first) }
                    }
                }
            }
        }
        val root = rule.activity.window.decorView
        var sheet: Bitmap? = null
        frames.forEachIndexed { index, (pageAndTitle, glass, _) ->
            val column = index % themes.size
            rule.runOnIdle {
                FrostedGlassPreferences.saveProfiles(context, FrostedGlassPreferences.readProfiles(context).withEnabled(glass))
                open(pageAndTitle.second)
                shown.intValue = index
            }
            // 360 ms 等切页动画，再翻页、滚准，220 ms 亮起；截在停留阶段。
            rule.mainClock.advanceTimeBy(1_300)
            rule.waitForIdle()
            val frame = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(frame)) }
            if (column == 0) sheet = Bitmap.createBitmap(frame.width * themes.size + 8 * (themes.size - 1), frame.height,
                Bitmap.Config.ARGB_8888).also { it.eraseColor(android.graphics.Color.DKGRAY) }
            android.graphics.Canvas(sheet!!).drawBitmap(frame, (column * (frame.width + 8)).toFloat(), 0f, null)
            frame.recycle()
            if (column == themes.size - 1) {
                File(out, "search-jump-${pageAndTitle.first}-${if (glass) "glass" else "solid"}.png").outputStream()
                    .use { sheet!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                sheet!!.recycle()
            }
        }
    }
}
