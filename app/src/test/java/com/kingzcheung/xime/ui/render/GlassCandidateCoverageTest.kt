package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.keyboard.ToolbarAction
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.theme.*
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-rCN-w440dp-h700dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassCandidateCoverageTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun exposedCandidatePixelsUseMaterialOpacityWhileSharedImageIsOffset() {
        val app = rule.activity.application
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        val vm = KeyboardViewModel(app)
        val fallback = Color(0xff302d3a)
        val bitmapColor = 0xff4c405c.toInt()
        val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(bitmapColor) }.asImageBitmap()
        var measured by mutableStateOf(false)
        var composing by mutableStateOf(false)
        var opacity by mutableFloatStateOf(1f)
        val callbacks = KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {})
        rule.setContent {
            val root = LocalView.current.rootView
            var origin by remember { mutableStateOf(Offset.Zero) }
            var size by remember { mutableStateOf(IntSize.Zero) }
            val glass = FrostedGlassConfig.defaults(true, enabled = true).copy(backgroundOpacity = opacity)
            val frame = keyboardBackdropFrame(origin, size, size.height - 44, origin.y.takeIf { measured })
            val shared = SharedKeyboardBackdrop(frame, image, fallback, opacity, root)
            Column(Modifier.width(388.dp).background(Color.White).testTag("sample")) {
                Text("聊天输入框", Modifier.height(44.dp).padding(8.dp), color = Color.DarkGray)
                CompositionLocalProvider(LocalSharedKeyboardBackdrop provides shared,
                    LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = glass)) {
                    Column(Modifier.fillMaxWidth().height(280.dp).testTag("card").onGloballyPositioned {
                        origin = it.positionInWindow(); size = it.size
                    }.keyboardBackground(null, true, fallback, glass)) {
                        CandidateBar(if (composing) CandidateBarState.ChineseCandidates(
                            listOf("你看", "现在", "这样"), inputText = "nikan") else CandidateBarState.Idle,
                            toolbarActions = ToolbarButton.DEFAULT_VISIBLE.map { ToolbarAction(ToolbarButtonItem.Builtin(it)) {} },
                            visuals = CandidateBarVisuals(Color.Transparent, Color(0xffddd4ff), Color.Gray, isDarkTheme = true),
                            callbacks = CandidateBarCallbacks(onCandidateSelect = {}), showPreeditPreview = false,
                            modifier = Modifier.fillMaxWidth().height(44.dp))
                        KeyboardLayout({}, vm, callbacks, KeyboardUiState(currentSchemaId = "rime_ice",
                            themeId = "pure_black", isDarkTheme = true), false, Modifier.fillMaxSize())
                    }
                }
            }
        }
        fun read(label: String, expected: Int) {
            rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            val card = rule.onNodeWithTag("card").fetchSemanticsNode().boundsInWindow
            val x = card.left.toInt() + 2
            fun red(y: Int) = android.graphics.Color.red(bitmap.getPixel(x, card.top.toInt() + y))
            assertTrue("$label host ${red(-2)}", red(-2) == 255)
            assertTrue("$label candidate ${red(2)} expected $expected", abs(red(2) - expected) <= 2)
            System.getenv("CYIME_RENDER_OUT")?.let { out ->
                val b = rule.onNodeWithTag("sample").fetchSemanticsNode().boundsInWindow
                File(out, "glass-$label.png").outputStream().use {
                    Bitmap.createBitmap(bitmap, b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt())
                        .compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        read("idle", 0x30)
        rule.runOnIdle { composing = true }
        read("candidates", 0x30)
        rule.runOnIdle { opacity = 0.5f }
        read("half-offset", (255 + 0x30) / 2)
        rule.runOnIdle { measured = true }
        read("half-aligned", (255 + 0x4c) / 2)
        rule.runOnIdle { opacity = 1f }
        read("aligned", 0x4c)
    }
}
