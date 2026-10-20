package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.CandidateDropTargets
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Shift 拖到候选：拖线在候选栏里的那段、指着的候选的高亮，以及从第 1 个候选移到第 2 个时高亮滑过去的关键帧。
 * 键盘那段拖线由字母区自己画，这里用一块空白代替字母区。只在设了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w480dp-h300dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShiftDragRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderShiftDrag() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val theme = System.getenv("CYIME_RENDER_THEME") ?: "soft_blue"
        val dark = System.getenv("CYIME_RENDER_DARK") == "1"
        val glass = System.getenv("CYIME_RENDER_GLASS") == "1"
        val palette = resolveKeyboardPalette(theme, dark)
        val config = FrostedGlassConfig.defaults(dark, enabled = glass)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.width(460.dp).height(200.dp).background(Color(0xFF3A6EA5))
                .keyboardBackground(KeyboardThemes.getRenderingScheme(theme).keyboardBackground, dark, palette.background, config)) {
                CompositionLocalProvider(LocalKeyboardPalette provides palette,
                    LocalKeyboardInputPreferences provides KeyboardInputPreferences(frostedGlass = config)) {
                    Column {
                        CandidateBar(
                            state = CandidateBarState.ChineseCandidates(listOf("这到底", "是什么", "原理", "这到底是什么原理"),
                                inputText = "zhedaodi", hasMore = true),
                            visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText,
                                if (dark) Color(0xFF3C4043) else Color(0xFFDADCE0), palette.accent,
                                palette.selectedText, dark, palette.key),
                            callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onShowMoreCandidates = {}),
                            showPreeditPreview = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(Modifier.fillMaxWidth().height(156.dp)) // 字母区占位
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        val c0 = rule.onNodeWithTag("bar-candidate:0").fetchSemanticsNode().boundsInRoot
        val c1 = rule.onNodeWithTag("bar-candidate:1").fetchSemanticsNode().boundsInRoot
        // Shift 在左下角：拖线从那里伸到手指。
        val shift = Offset(60f, c0.bottom + 120f * rule.density.density)
        val drops = CandidateDropTargets.shared
        File(out!!).mkdirs()
        fun shot(name: String) {
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        rule.runOnIdle { drops.dragLine = shift to c0.center; drops.hoveredBounds = c0; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeBy(300)
        shot("drag-on-first")
        rule.runOnIdle { drops.dragLine = shift to c1.center; drops.hoveredBounds = c1; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        for (t in listOf(30L, 60L, 120L)) {
            rule.mainClock.advanceTimeBy(if (t == 30L) 30 else if (t == 60L) 30 else 60)
            rule.mainClock.advanceTimeByFrame()
            shot("drag-to-second-${t}ms")
        }
        rule.runOnIdle { drops.dragLine = null; drops.hoveredBounds = null }
    }
}
