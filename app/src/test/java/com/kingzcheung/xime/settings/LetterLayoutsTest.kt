package com.kingzcheung.xime.settings

import android.content.Context
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * One rule for letter layouts: every letter layout Chinese pinyin offers is offered the same way for
 * English direct input and Japanese romaji, as the same [CustomKeyboardLayout] (rows, name, swipes).
 */
class LetterLayoutsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val chinese = listOf("rime_ice", "t9_pinyin", "pinyin_14jian", Cyletix10Layout.ID, ColemakLayout.ID)
    private val entries = (chinese + listOf("japanese", "japanese_kana")).map { SchemaInfo(it, it, "", "", "") } + InputModes.english
    private fun entry(id: String) = entries.first { it.schemaId == id }

    @After fun clear() {
        LetterLayouts.setForTest(InputLanguage.ENGLISH, null)
        LetterLayouts.setForTest(InputLanguage.JAPANESE, null)
        LetterLayouts.setForTest(InputLanguage.CHINESE, null)
    }

    /** 双拼（小鹤、自然码、自定义）和英文、日语罗马字一样，能用中文拼音提供的字母布局；14 键版本照旧只有 14 键。 */
    @Test fun doublePinyinTypesOnTheChineseLetterLayouts() {
        val all = entries + listOf("double_pinyin_flypy", "double_pinyin_flypy_14jian", DoublePinyinScheme.ZIRANMA_ID)
            .map { SchemaInfo(it, if (it == DoublePinyinScheme.ZIRANMA_ID) "自然码" else "小鹤双拼", "", "", "") }
        fun e(id: String) = all.first { it.schemaId == id }
        for (id in listOf("double_pinyin_flypy", DoublePinyinScheme.ZIRANMA_ID))
            assertEquals(listOf(InputLayout.QWERTY.id, Cyletix10Layout.ID, ColemakLayout.ID),
                InputProfileSelection.layoutsOf(e(id), all).map { it.id })
        assertEquals(listOf(InputLayout.MERGED14.id), InputProfileSelection.layoutsOf(e("double_pinyin_flypy_14jian"), all).map { it.id })
        // 点 Colemak 时保持当前双拼方案，只换按键排列。
        assertEquals(InputProfileSelection.LayoutSwitch(e(DoublePinyinScheme.ZIRANMA_ID), true),
            InputProfileSelection.switchLayout(all, e(DoublePinyinScheme.ZIRANMA_ID), ColemakLayout.ID))
        LetterLayouts.setForTest(InputLanguage.CHINESE, ColemakLayout.ID)
        val profile = InputProfiles.current(DoublePinyinScheme.ZIRANMA_ID)
        assertEquals(InputScheme.DOUBLE_PINYIN, profile.scheme)
        assertEquals(EngineProfile.Rime(DoublePinyinScheme.ZIRANMA_ID), profile.engineProfile)
        assertEquals(ColemakLayout.ID, profile.layout.id)
        assertEquals(ColemakLayout.preset.typingRows(), KeysConfigHelper.letterLayout("double_pinyin_flypy", false)?.typingRows())
        // 拼音的布局在方案里，英文有自己的排列，都不受影响。
        assertNull(KeysConfigHelper.letterLayout("rime_ice", false))
        assertEquals(InputLayout.QWERTY.id, InputProfiles.current("rime_ice", asciiMode = true).layout.id)
        // 五笔和导入的中文码表（音形等）用同一个选择；没标语言的导入方案不跟中文走。
        assertEquals(ColemakLayout.ID, InputProfiles.current("wubi86").layout.id)
        assertEquals(ColemakLayout.ID, InputProfiles.arranged(InputProfiles.describe("my_yinxing",
            InputLanguage.CHINESE, InputScheme.EXTERNAL)).layout.id)
        assertEquals(InputLayout.QWERTY.id, InputProfiles.arranged(InputProfiles.describe("my_yinxing")).layout.id)
        // 回到 26 键：不再用 Colemak。
        assertEquals(InputProfileSelection.LayoutSwitch(e(DoublePinyinScheme.ZIRANMA_ID), true),
            InputProfileSelection.switchLayout(all, e(DoublePinyinScheme.ZIRANMA_ID), InputLayout.QWERTY.id))
    }

    @Test fun colemakIsABuiltinLetterLayoutLikeCyletix10() {
        val colemak = ColemakLayout.preset
        assertEquals(listOf("qwfpgjluy;", "arstdhneio", "zxcvbkm"), colemak.rows.map { it.joinToString("") })
        assertTrue(colemak.valid())
        assertEquals("Colemak", colemak.name)
        assertEquals("Cyletix10（实验）", Cyletix10Layout.preset.name)
        assertEquals(listOf(QwjrtkLayout.ID, Cyletix10Layout.ID, ColemakLayout.ID, TypewiseLayout.ID),
            BuiltinLetterLayouts.ids.toList())
        for (id in BuiltinLetterLayouts.ids) {
            assertTrue(id in ChineseSchemas.ids)
            assertTrue(CustomKeyboardLayouts.isCustom(id))
            assertSame(BuiltinLetterLayouts.find(id), CustomKeyboardLayouts.find(id))
            assertEquals(InputScheme.PINYIN, InputProfiles.describe(id).scheme)
        }
        assertEquals("Colemak · 内置", SchemaInfo(ColemakLayout.ID, "Colemak", "", "", "").resourceLabel)
    }

    @Test fun englishAndJapaneseOfferExactlyTheChineseLetterLayouts() {
        val chineseLetterLayouts = InputProfileSelection.layouts(entries, entry("rime_ice"))
            .filter { CustomKeyboardLayouts.find(it.id)?.let(LetterLayouts::typable) == true }
        assertEquals(listOf(Cyletix10Layout.ID, ColemakLayout.ID), chineseLetterLayouts.map { it.id })
        for (id in listOf(InputModes.ENGLISH, "japanese")) {
            val offered = InputProfileSelection.layouts(entries, entry(id))
            assertEquals(entry(id).baseProfile.layout, offered.first())
            // Same ids and the user's own names, never copies with new labels.
            assertEquals(chineseLetterLayouts, offered.drop(1))
        }
        // Kana and Chinese entries offer only their own layout (which one depends on loaded bindings).
        for (id in listOf("japanese_kana", "pinyin_14jian", "rime_ice"))
            assertEquals(listOf(entry(id).profile.layout), InputProfileSelection.layoutsOf(entry(id), entries))
    }

    @Test fun anArrangementChangesKeysNotLanguageSchemeOrEngine() {
        LetterLayouts.setForTest(InputLanguage.ENGLISH, Cyletix10Layout.ID)
        LetterLayouts.setForTest(InputLanguage.JAPANESE, ColemakLayout.ID)
        val english = InputProfiles.current("rime_ice", asciiMode = true)
        assertEquals(InputLanguage.ENGLISH, english.language)
        assertEquals(InputScheme.DIRECT, english.scheme)
        assertEquals(EngineProfile.Direct, english.engineProfile)
        assertEquals(Cyletix10Layout.ID, english.layout.id)
        assertEquals(Cyletix10Layout.preset.typingRows(), KeysConfigHelper.letterLayout("rime_ice", true)?.typingRows())

        val japanese = InputProfiles.current("japanese")
        assertEquals(InputScheme.ROMAJI, japanese.scheme)
        assertEquals(EngineProfile.Rime("japanese"), japanese.engineProfile)
        assertEquals(ColemakLayout.ID, japanese.layout.id)
        assertEquals(ColemakLayout.ID, entry("japanese").profile.layout.id)
        assertEquals(ColemakLayout.preset.typingRows(), KeysConfigHelper.letterLayout("japanese", false)?.typingRows())

        // Chinese keeps its layout in the schema; kana keeps its keypad.
        assertNull(KeysConfigHelper.letterLayout("rime_ice", false))
        assertEquals(Cyletix10Layout.ID, KeysConfigHelper.letterLayout(Cyletix10Layout.ID, false)?.id)
        assertNull(KeysConfigHelper.letterLayout("japanese_kana", false))

        // The picker selects the same entry; only the stored arrangement changes.
        assertEquals(InputModes.ENGLISH, InputProfileSelection.changeLayout(entries, entry(InputModes.ENGLISH), ColemakLayout.ID)?.schemaId)
        assertEquals("japanese", InputProfileSelection.changeLayout(entries, entry("japanese"),
            entry("japanese").baseProfile.layout.id)?.schemaId)

        LetterLayouts.setForTest(InputLanguage.ENGLISH, null)
        assertEquals(InputLayout.QWERTY.id, InputProfiles.current("rime_ice", asciiMode = true).layout.id)
        assertNull(KeysConfigHelper.letterLayout("rime_ice", true))
    }

    @Test fun mergedKeysAreNotOfferedAndAStaleChoiceFallsBack() {
        val directory = temporary.newFolder()
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(directory)
        val file = File(directory, "custom-keyboard-layouts.json")
        val merged = "custom_pinyin_" + "a".repeat(32)
        try {
            file.writeText("""[{"formatVersion":2,"id":"$merged","name":"我的合并","rows":"qw,e,r,t,y,u,i,o,p/a,s,d,f,g,h,j,k,l/z,x,c,v,b,n,m"}]""")
            CustomKeyboardLayouts.load(context)
            val all = entries + SchemaInfo(merged, "我的合并", "", "", "")
            assertTrue(InputProfileSelection.layouts(all, all.first { it.schemaId == "rime_ice" }).any { it.id == merged })
            assertFalse(InputProfileSelection.layouts(all, entry(InputModes.ENGLISH)).any { it.id == merged })
            LetterLayouts.setForTest(InputLanguage.ENGLISH, merged)
            assertEquals(InputLayout.QWERTY.id, InputProfiles.current("rime_ice", asciiMode = true).layout.id)
            LetterLayouts.setForTest(InputLanguage.ENGLISH, "custom_pinyin_" + "b".repeat(32))
            assertEquals(InputLayout.QWERTY.id, InputProfiles.current("rime_ice", asciiMode = true).layout.id)
        } finally {
            file.writeText("[]")
            CustomKeyboardLayouts.load(context)
        }
    }

    /** Typewise: four staggered rows, offered like any other letter layout, swipes stay with their letters. */
    @Test fun typewiseIsAHoneycombLetterLayout() {
        val typewise = TypewiseLayout.preset
        assertEquals(listOf("wetyio", "qsrgukp", "adfhjl", "zxcvbnm"), typewise.rows.map { it.joinToString("") })
        assertTrue(typewise.honeycomb)
        assertTrue(typewise.valid())
        assertFalse(Cyletix10Layout.preset.honeycomb)
        assertEquals("Typewise", typewise.name)
        assertSame(typewise, typewise.withSpareSlot())
        assertTrue(typewise.movedLetters().isEmpty())
        assertTrue(LetterLayouts.typable(typewise))
        assertFalse(typewise.copy(rows = typewise.rows.mapIndexed { i, r -> if (i == 3) r.dropLast(1) else r }).valid())
        val base = mapOf("q" to KeyGestureConfig(swipeUp = GestureDef(label = "1", value = "1")),
            "w" to KeyGestureConfig(swipeUp = GestureDef(label = "2", value = "2")))
        assertEquals("1", typewise.gestures(base)["q"]?.swipeUp?.label)
        assertEquals("2", typewise.gestures(base)["w"]?.swipeUp?.label)
        val all = entries + SchemaInfo(TypewiseLayout.ID, "Typewise", "", "", "")
        for (id in listOf(InputModes.ENGLISH, "japanese"))
            assertTrue(InputProfileSelection.layouts(all, all.first { it.schemaId == id }).any { it.id == TypewiseLayout.ID })
        LetterLayouts.setForTest(InputLanguage.ENGLISH, TypewiseLayout.ID)
        assertEquals(typewise.typingRows(), KeysConfigHelper.letterLayout("rime_ice", true)?.typingRows())
    }

    /** Romaji needs only letters: each arrangement has all 26, and swipes stay where the finger expects them. */
    @Test fun romajiKeepsEveryLetterAndPositionalSwipes() {
        val base = mapOf(
            "m" to KeyGestureConfig(swipeUp = GestureDef(label = "ー", value = "ー")),
            "l" to KeyGestureConfig(swipeUp = GestureDef(label = "-", value = "-")),
            "q" to KeyGestureConfig(swipeUp = GestureDef(label = "1", value = "1")),
        )
        for (layout in BuiltinLetterLayouts.all) {
            assertEquals(('a'..'z').toSet(), layout.typingRows().flatten().joinToString("").filter(Char::isLetter).toSet())
            val gestures = layout.gestures(base)
            for (key in layout.typingRows().flatten()) assertEquals(key, gestures[key]?.tap?.value)
            if (layout.honeycomb) continue
            val bottom = layout.typingRows()[2]
            assertEquals("ー", gestures[bottom[6]]?.swipeUp?.label)
            assertEquals("1", gestures[layout.typingRows()[0][0]]?.swipeUp?.label)
        }
        // Colemak puts i where QWERTY has l: the swipe follows the position.
        assertEquals("-", ColemakLayout.preset.gestures(base)["i"]?.swipeUp?.label)
    }
}
