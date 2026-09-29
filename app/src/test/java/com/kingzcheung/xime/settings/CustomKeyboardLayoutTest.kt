package com.kingzcheung.xime.settings

import android.content.Context
import com.charleskorn.kaml.*
import com.kingzcheung.xime.rime.merged14Preedit
import com.kingzcheung.xime.rime.PinyinEditDisplay
import com.kingzcheung.xime.ui.keyboard.*
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class CustomKeyboardLayoutTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun swapsMergesAndSplitsPreserveAllLettersAndRowWidths() {
        val base = CustomKeyboardLayout.fresh()
        assertTrue(base.valid())
        val swapped = base.swap("e", "j")
        assertEquals(setOf('e', 'j'), swapped.movedLetters())
        val merged = swapped.merge("q")
        assertTrue(merged.valid())
        assertEquals(25, merged.rows.flatten().size)
        assertEquals("qw", merged.rows.first().first())
        assertEquals(swapped, merged.split("qw"))
        assertEquals(merged, merged.swap("qw", "a"))
        assertTrue(merged.swap("qw", "j").valid())
        assertEquals(base, base.merge("p"))
        assertFalse(base.copy(rows = listOf(listOf("a"))).valid())
        assertFalse(base.copy(id = "../bad").valid())
    }
    @Test fun arbitraryMergedCodingAndPreviewAgree() {
        val layout = CustomKeyboardLayout.fresh().merge("q").merge("e")
        assertEquals("qen", layout.encode("wen"))
        assertEquals("qen", layout.encode("qen"))
        val groups = layout.rows.flatten()
        assertEquals("wen", merged14Preedit("qen", "qen", "wen", groups))
        assertEquals("qen", merged14Preedit("qen", "qen", "men", groups))
        val display = PinyinEditDisplay("qen", "wen", groups = groups)
        assertEquals("wen", display.text)
        assertEquals(2, display.rawOffset(2))
    }
    @Test fun generatedSchemaHasIndependentPrismAndFinalTransliteration() {
        val layout = CustomKeyboardLayout.fresh().copy(name = "测试：\"引号\"").merge("q").merge("e")
        val base = File("build/generated/chinese-assets/rime_ice/rime_ice.schema.yaml").readText()
        val node = Yaml.default.parseToYamlNode(layout.schema(base)) as YamlMap
        fun entry(map: YamlMap, key: String) = map.entries.entries.first { it.key.content == key }.value
        val translator = entry(node, "translator") as YamlMap
        assertEquals(layout.id, (entry(translator, "prism") as YamlScalar).content)
        assertEquals("rime_ice", (entry(translator, "dictionary") as YamlScalar).content)
        val algebra = entry(entry(node, "speller") as YamlMap, "algebra") as YamlList
        assertEquals("xlit/abcdefghijklmnopqrstuvwxyz/${layout.encode("abcdefghijklmnopqrstuvwxyz")}/", (algebra.items.last() as YamlScalar).content)
        assertEquals(layout.name, (entry(entry(node, "schema") as YamlMap, "name") as YamlScalar).content)
    }
    @Test fun spareSlotMovesWithoutCreatingPunctuationAndCanBeFilledExplicitly() {
        val base = CustomKeyboardLayout.fresh().withSpareSlot()
        assertTrue(base.valid())
        assertEquals("asdfghjkl_", base.rows[1].joinToString(""))
        assertEquals(26, base.typingRows().flatten().size)
        val moved = base.swap("e", CustomKeyboardLayout.EMPTY_SLOT)
        assertTrue(moved.valid())
        assertEquals("e", moved.rows[1].last())
        assertEquals(26, moved.typingRows().flatten().size)
        assertFalse(";" in moved.typingRows().flatten())
        assertFalse("_" in moved.typingRows().flatten())
        assertEquals("wen", moved.encode("wen"))
        assertEquals(setOf('e'), moved.movedLetters())
        assertEquals(base, moved.swap("e", CustomKeyboardLayout.EMPTY_SLOT))
        val explicit = moved.setSemicolonEnabled(true)
        assertTrue(explicit.valid())
        assertEquals(27, explicit.typingRows().flatten().size)
        assertEquals(";", explicit.rows[0][2])
        assertEquals(moved, explicit.setSemicolonEnabled(false))
        assertEquals(27, base.setSemicolonEnabled(true).typingRows().flatten().size)
        assertEquals(base, base.merge("l"))
        assertTrue(base.merge("q").valid())
        assertFalse(base.copy(rows = base.rows.map { it + "_" }).valid())
        assertFalse("_" in moved.gestures(emptyMap()))
    }
    @Test fun legacyStationarySemicolonMigratesButMovedOrExplicitSemicolonSurvives() {
        val dir = tmp.newFolder()
        val context = mock<Context>(); whenever(context.filesDir).thenReturn(dir)
        val file = File(dir, "custom-keyboard-layouts.json")
        fun load(rows: String, version: Int) : CustomKeyboardLayout {
            file.writeText("""[{"formatVersion":$version,"id":"pinyin_qwjrtk","name":"既有方案","rows":"$rows"}]""")
            return CustomKeyboardLayouts.load(context).single()
        }
        val original = "q,w,e,r,t,y,u,i,o,p/a,s,d,f,g,h,j,k,l,;/z,x,c,v,b,n,m"
        assertEquals(26, load(original, 1).typingRows().flatten().size)
        assertEquals("_", load(original, 1).rows[1].last())
        assertEquals(27, load(original, 2).typingRows().flatten().size)
        assertEquals(27, load("q,w,;,r,t,y,u,i,o,p/a,s,d,f,g,h,j,k,l,e/z,x,c,v,b,n,m", 1).typingRows().flatten().size)
        assertEquals(26, load(original.replace("l,;", "l,_"), 2).typingRows().flatten().size)
    }
    @Test fun movedVowelsAndMergedLabelsKeepIndependentRedSpans() {
        val layout = CustomKeyboardLayout.fresh().swap("e", "j").merge("j")
            .copy(redVowels = true, redMoved = true)
        assertTrue(layout.isRed('E'))
        assertTrue(layout.isRed('J'))
        assertTrue(layout.isRed('A'))
        assertFalse(layout.isRed('R'))
        val text = customLayoutLabel(layout, "JR", Color.White, Color(0xFF6750A4), Color.Black)
        assertEquals(1, text.spanStyles.size)
        assertEquals(0, text.spanStyles.single().start)
        assertEquals(1, text.spanStyles.single().end)
        assertFalse(layout.copy(redVowels = false, redMoved = false).isRed('E'))
    }
    @Test fun oldLayoutsLoadWithMovedRedEnabledAndCanKeepVowelPreference() {
        val dir = tmp.newFolder()
        val context = mock<Context>(); whenever(context.filesDir).thenReturn(dir)
        File(dir, "custom-keyboard-layouts.json").writeText("""[{"id":"pinyin_qwjrtk","name":"QWJRTK","rows":"q,w,j,r,t,k,u,i,o,p/a,s,d,f,g,h,e,n,l/z,x,c,y,b,v,m","redVowels":false}]""")
        val layout = CustomKeyboardLayouts.load(context).single()
        assertTrue(layout.redMoved)
        assertFalse(layout.redVowels)
        assertTrue(layout.isRed('e'))
    }
    @Test fun deletingSeedDoesNotRecreateItOnReload() {
        val dir = tmp.newFolder()
        val context = mock<Context>(); whenever(context.filesDir).thenReturn(dir)
        assertEquals(QwjrtkLayout.ID, CustomKeyboardLayouts.load(context).single().id)
        File(dir, "custom-keyboard-layouts.json").writeText("[]")
        assertTrue(CustomKeyboardLayouts.load(context).isEmpty())
        assertTrue(CustomKeyboardLayouts.load(context).isEmpty())
    }
    @Test fun lightDarkAndColoredThemesHaveReadableVowelsAndMovedKeys() {
        val layout = CustomKeyboardLayout.fresh().swap("e", "j").copy(redVowels = true)
        for (bg in listOf(Color.White, Color(0xFF302D38), Color(0xFFDDEBDD), Color(0xFF463049))) {
            for (accent in listOf(Color(0xFF6750A4), Color(0xFF146C2E), Color(0xFFD35645), Color(0xFF9ECBFF))) {
                val (fill, text) = customLayoutKeyColors(layout, "e", bg, Color.White, accent)
                assertNotEquals(bg, fill)
                assertTrue(layoutContrast(text, fill) >= 4.5f)
                val red = vowelColor(fill, accent, Color.White)
                assertTrue(layoutContrast(red, fill) >= 4.5f)
                assertNotEquals(vowelColor(fill, Color(0xFF146C2E), Color.White), vowelColor(fill, Color(0xFF6750A4), Color.White))
            }
        }
    }
}
