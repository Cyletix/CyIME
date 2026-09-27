package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class T9SidebarScaleTest {
    @get:Rule val rule = createComposeRule()
    @Test fun lettersAndSyllablesKeepSameGeometryOnPhoneAndTablet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        KeysConfigHelper.loadConfig(context)
        var wide by mutableStateOf(false)
        var many by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f),
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(
                    fixedSymbols = if (many) "jie\njin\njing\nju\njuan\njue\njun\nshuang" else "j\nk\nl")) {
                MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                    T9KeyboardLayout({}, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                        KeyboardUiState(currentSchemaId = "t9_pinyin"), remember { T9InputController() },
                        Color(0xFF38333F), Color.White, Color(0xFF58576D),
                        modifier = Modifier.size(if (wide) 1000.dp else 360.dp, if (wide) 400.dp else 240.dp).testTag("sidebar-preview"))
                }
            }
        }
        val fonts = mutableListOf<Float>()
        for (tablet in listOf(false, true)) {
            rule.runOnIdle { wide = tablet; many = false }
            val before = rule.onNodeWithTag("t9-pinyin-option:0").fetchSemanticsNode().boundsInRoot
            fun layout(label: String): TextLayoutResult {
                val layouts = mutableListOf<TextLayoutResult>()
                rule.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                return layouts.single()
            }
            val single = layout("j")
            rule.runOnIdle { many = true }
            val after = rule.onNodeWithTag("t9-pinyin-option:0").fetchSemanticsNode().boundsInRoot
            val syllable = layout("jie")
            assertEquals(before.width, after.width, 0.1f)
            assertEquals(before.height, after.height, 0.1f)
            assertEquals(single.layoutInput.style.fontSize, syllable.layoutInput.style.fontSize)
            fonts += syllable.layoutInput.style.fontSize.value
            val file = File(context.getExternalFilesDir(null), "t9-sidebar-$tablet.png")
            file.outputStream().use { rule.onNodeWithTag("sidebar-preview").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            fun assertGlyphsFit(result: TextLayoutResult) {
                assertFalse(result.didOverflowHeight)
                for (line in 0 until result.lineCount) {
                    assertTrue("Glyphs clipped at tablet=$tablet: $result",
                        result.getLineRight(line) - result.getLineLeft(line) <= result.size.width + 1f)
                }
            }
            assertGlyphsFit(syllable)
            rule.onNodeWithTag("t9-pinyin-options").performScrollToIndex(7)
            assertGlyphsFit(layout("shuang"))
        }
        assertTrue("Tablet syllables must scale with actual keys: $fonts", fonts[1] > fonts[0] * 1.3f)
    }
}
