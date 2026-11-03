package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
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
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 日语 26 键长音键的两种放法：A 在 L 键右侧（默认），B 代替 Shift（点按 ー，按住是 Shift）。
 * 点按都送 -（进罗马字转换）；B 按住没打别的键松手是 Shift 点按。设了 CYIME_RENDER_OUT 时另出手机、平板两种宽度的图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1000dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JapaneseChoonpuKeyRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun choonpuKeyBesideLOrOnShift() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        com.kingzcheung.xime.ui.keyboard.AppFonts.initialize(app)
        KeysConfigHelper.setActiveKeyboardSchema("japanese")
        val vm = KeyboardViewModel(app)
        val pressed = mutableListOf<String>()
        var onShift by mutableStateOf(false)
        var tablet by mutableStateOf(false)
        rule.setContent {
            val palette = resolveKeyboardPalette("soft_blue", false)
            val config = FrostedGlassConfig.defaults(false, enabled = false)
            val width = if (tablet) 900.dp else 400.dp
            val height = if (tablet) 340.dp else 280.dp
            CompositionLocalProvider(LocalKeyboardExplicitWidth provides true,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config, japaneseChoonpuOnShift = onShift)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(width, height).testTag("layout-body")
                        .keyboardBackground(KeyboardThemes.getRenderingScheme("soft_blue").keyboardBackground, false,
                            palette.background, config)) {
                        KeyboardLayout({ pressed += it }, vm, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            KeyboardUiState(currentSchemaId = "japanese", themeId = "soft_blue", isDarkTheme = false),
                            false, Modifier.requiredSize(width, height))
                    }
                }
            }
        }
        rule.waitForIdle()

        // A：ー 在 L 右侧，Shift 照旧。
        rule.onAllNodesWithTag("qwerty-key:ー", true).assertCountEquals(1)
        rule.onAllNodesWithTag("shift-key-tap", true).assertCountEquals(0)
        rule.onNodeWithTag("qwerty-key:ー", true).performTouchInput { down(center); up() }
        rule.waitForIdle()
        assertEquals(listOf("-"), pressed)
        val l = rule.onNodeWithTag("qwerty-key:l", true).fetchSemanticsNode().boundsInRoot
        val choonpu = rule.onNodeWithTag("qwerty-key:ー", true).fetchSemanticsNode().boundsInRoot
        val p = rule.onNodeWithTag("qwerty-key:p", true).fetchSemanticsNode().boundsInRoot
        // ー 紧接 L 右边，不超出上排 P 的右边。
        assertEquals(l.right, choonpu.left, 1.5f)
        assertTrue(choonpu.right <= p.right + 1.5f)
        shot("choonpu-A-l-right-phone")

        // B：中排回到 9 键，Shift 键点按送 -，按住满时限松手是 Shift 点按。
        pressed.clear()
        rule.runOnIdle { onShift = true }
        rule.waitForIdle()
        rule.onAllNodesWithTag("qwerty-key:ー", true).assertCountEquals(0)
        rule.onAllNodesWithTag("shift-key-tap", true).assertCountEquals(1)
        rule.onNodeWithTag("shift-key", true).performTouchInput { down(center); advanceEventTime(80); up() }
        rule.waitForIdle()
        assertEquals(listOf("-"), pressed)
        pressed.clear()
        rule.onNodeWithTag("shift-key", true).performTouchInput { down(center); advanceEventTime(600); up() }
        rule.waitForIdle()
        assertEquals(listOf("shift_single"), pressed)
        shot("choonpu-B-on-shift-phone")

        rule.runOnIdle { tablet = true; onShift = false }
        rule.waitForIdle()
        shot("choonpu-A-l-right-tablet")
        rule.runOnIdle { onShift = true }
        rule.waitForIdle()
        shot("choonpu-B-on-shift-tablet")
    }

    private fun shot(name: String) {
        val out = System.getenv("CYIME_RENDER_OUT")
        if (out.isNullOrEmpty()) return
        File(out).mkdirs()
        val body = rule.onNodeWithTag("layout-body", true).fetchSemanticsNode().boundsInRoot
        val root = rule.activity.window.decorView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(full)) }
        val crop = Bitmap.createBitmap(full, body.left.toInt().coerceAtLeast(0), body.top.toInt().coerceAtLeast(0),
            body.width.toInt().coerceAtMost(full.width), body.height.toInt().coerceAtMost(full.height))
        File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
