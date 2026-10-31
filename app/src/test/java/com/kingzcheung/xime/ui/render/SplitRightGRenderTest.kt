package com.kingzcheung.xime.ui.render

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayout
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardExplicitWidth
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 「右半边也放 G 键」（别人提的需求，默认关，用户 10-31：「千万别影响其他功能」）。
 * - 不分体的 26 键、英文、日语罗马字、14 键、注音，固定和悬浮、手机和平板：开关开着和关着逐像素相同。
 * - 分体 14 键、注音（不能分体）：同样逐像素相同。九键、手写等别的键盘不经过这段代码，也不读这个开关。
 * - 分体 26 键：右半多一个 G，g h j k l 在 y u i o p 正下方、l 右边到行尾；左半各键和上下两排不动，中缝不变。
 * 设了 CYIME_RENDER_OUT=<目录> 时另存分体开关前后的对照图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1000dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SplitRightGRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private data class Case(val schema: String, val ascii: Boolean = false, val split: Boolean = false,
        val tablet: Boolean = false, val floating: Boolean = false, val voice: Boolean = false, val theme: String = "soft_blue")

    private var case by mutableStateOf(Case("rime_ice"))
    private var rightG by mutableStateOf(false)
    private lateinit var vm: KeyboardViewModel

    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        vm = KeyboardViewModel(app)
        rule.setContent {
            val c = case
            val dark = c.theme == "pure_black"
            val palette = resolveKeyboardPalette(c.theme, dark)
            val glass = FrostedGlassConfig.defaults(dark, enabled = false)
            val width = if (c.tablet) 900.dp else 400.dp
            val height = if (c.tablet) 340.dp else 280.dp
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = if (c.tablet) 900 else 400; screenHeightDp = if (c.tablet) 1280 else 860
                smallestScreenWidthDp = screenWidthDp
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalKeyboardExplicitWidth provides true,
                LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = glass,
                    splitKeyboardEnabled = c.split, splitRightG = rightG)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(width, height).testTag("layout-body")
                        .keyboardBackground(KeyboardThemes.getRenderingScheme(c.theme).keyboardBackground, dark,
                            palette.background, glass)) {
                        KeyboardLayout({}, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            KeyboardUiState(currentSchemaId = c.schema, themeId = c.theme, isDarkTheme = dark,
                                isAsciiMode = c.ascii, isFloatingMode = c.floating, isVoiceMode = c.voice),
                            c.ascii, Modifier.requiredSize(width, height))
                    }
                }
            }
        }
    }

    private fun show(c: Case, g: Boolean) {
        KeysConfigHelper.setActiveKeyboardSchema(c.schema)
        rule.runOnIdle { vm.resetKeyboard(c.ascii, c.schema); case = c; rightG = g }
        rule.waitForIdle()
    }

    private fun shot(): Bitmap {
        val body = rule.onNodeWithTag("layout-body", true).fetchSemanticsNode().boundsInRoot
        val root = rule.activity.window.decorView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
        return Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
            body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
    private fun count(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size

    private fun save(name: String, bitmap: Bitmap) {
        val out = System.getenv("CYIME_RENDER_OUT")?.takeIf { it.isNotEmpty() } ?: return
        File(out).mkdirs()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun name(c: Case) = listOf(c.schema + if (c.ascii) "-en" else "", if (c.split) "split" else "full",
        if (c.tablet) "tablet" else "phone", if (c.floating) "floating" else "fixed", if (c.voice) "voice" else "", c.theme)
        .filter { it.isNotEmpty() }.joinToString("-")

    /** 开关只在分体 26 键起作用：其余每个组合打开前后逐像素相同，也不多出右半 G。 */
    @Test fun everyOtherKeyboardIsPixelIdentical() {
        val cases = buildList {
            for (tablet in listOf(false, true)) for (floating in listOf(false, true)) {
                add(Case("rime_ice", tablet = tablet, floating = floating))
                add(Case("rime_ice", ascii = true, tablet = tablet, floating = floating))
                add(Case("pinyin_14jian", tablet = tablet, floating = floating))
                add(Case("cyime_zhuyin", tablet = tablet, floating = floating))
                add(Case("japanese", tablet = tablet, floating = floating))
                // 分体开着也不变：14 键拆开的 g/h 不补，注音不能分体。
                add(Case("pinyin_14jian", split = true, tablet = tablet, floating = floating))
                add(Case("cyime_zhuyin", split = true, tablet = tablet, floating = floating))
            }
            add(Case("rime_ice", voice = true))
            add(Case("rime_ice", theme = "pure_black"))
        }
        val changed = cases.filter { c ->
            show(c, false); val off = shot()
            show(c, true); val on = shot()
            assertEquals(name(c), 0, count("qwerty-key-right:g"))
            !off.sameAs(on)
        }.map(::name)
        assertEquals("打开「右半边也放 G 键」后变了样的键盘：$changed", emptyList<String>(), changed)
    }

    /** 分体 26 键：右半 g h j k l 对齐 y u i o p，左半和上下两排、中缝都不动。 */
    @Test fun splitQwertyGetsARightGUnderY() {
        val px = 1.5f
        for (c in listOf(Case("rime_ice", split = true), Case("rime_ice", split = true, tablet = true),
            Case("rime_ice", ascii = true, split = true), Case("rime_ice", split = true, floating = true),
            Case("rime_ice", split = true, tablet = true, theme = "pure_black"))) {
            show(c, false)
            val offShot = shot()
            val off = listOf("g", "a", "y", "p", "z", "m").associateWith { bounds("qwerty-key:$it") }
            val offGap = listOf("split-keyboard-gap", "split-keyboard-gap:1").associateWith { bounds(it) }
            assertEquals(0, count("qwerty-key-right:g"))
            show(c, true)
            val onShot = shot()
            save("${name(c)}-0-off", offShot)
            save("${name(c)}-1-on", onShot)
            assertFalse(name(c), offShot.sameAs(onShot))
            assertEquals(name(c), 1, count("qwerty-key-right:g"))
            assertEquals(1, count("qwerty-key:g"))
            // 左半的 g、a 和上下两排的键原地不动。
            off.forEach { (key, rect) ->
                val now = bounds("qwerty-key:$key")
                assertEquals("${name(c)} $key", rect.left, now.left, px); assertEquals(rect.right, now.right, px)
            }
            val rightG = bounds("qwerty-key-right:g")
            val y = bounds("qwerty-key:y")
            val h = bounds("qwerty-key:h")
            val l = bounds("qwerty-key:l")
            val p = bounds("qwerty-key:p")
            assertEquals("${name(c)} 右半 G 在 Y 下面", y.left, rightG.left, px)
            assertEquals(y.right, rightG.right, px)
            assertEquals(y.width, h.width, px)
            assertEquals("${name(c)} l 右边对齐 p", p.right, l.right, px)
            assertTrue(rightG.right <= h.left + px)
            // 中排的缝左边不变，右边与第一排一致：两半面板（各行缝的公共部分）不变。
            val gap1 = bounds("split-keyboard-gap:1")
            assertEquals(offGap.getValue("split-keyboard-gap:1").left, gap1.left, px)
            assertEquals(offGap.getValue("split-keyboard-gap").right, gap1.right, px)
            assertEquals(offGap.getValue("split-keyboard-gap"), bounds("split-keyboard-gap"))
        }
    }

    /** 语音时的占位键盘与正常键盘同一排法：中排的缝一样宽、一样位置。 */
    @Test fun voicePlaceholderMatchesTheTypingRow() {
        val typing = Case("rime_ice", split = true)
        show(typing, true)
        val gap = bounds("split-keyboard-gap:1")
        show(typing.copy(voice = true), true)
        val voiceGap = bounds("split-keyboard-gap:1")
        assertEquals(gap.left, voiceGap.left, 1.5f)
        assertEquals(gap.right, voiceGap.right, 1.5f)
    }
}
