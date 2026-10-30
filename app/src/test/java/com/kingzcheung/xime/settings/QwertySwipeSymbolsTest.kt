package com.kingzcheung.xime.settings

import com.kingzcheung.xime.keyboard.GestureAction
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class QwertySwipeSymbolsTest {
    @Test fun savedSymbolsAreReadBackIncludingExplicitlyEmptySlots() {
        val prefs = mock<android.content.SharedPreferences>()
        val context = mock<android.content.Context>()
        whenever(context.getSharedPreferences("kime_settings", android.content.Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.contains("qwerty_symbols_zh_up.q")).thenReturn(true)
        whenever(prefs.getString("qwerty_symbols_zh_up.q", "")).thenReturn("★")
        whenever(prefs.contains("qwerty_symbols_zh_down.q")).thenReturn(true)
        whenever(prefs.getString("qwerty_symbols_zh_down.q", "")).thenReturn("")
        assertEquals(mapOf("up.q" to "★", "down.q" to ""), QwertySwipeSymbols.load(context, false))
        assertEquals(emptyMap<String, String>(), QwertySwipeSymbols.load(context, true))
    }
    @get:org.junit.Rule val temp = org.junit.rules.TemporaryFolder()
    private val defaults get() = requireNotNull(javaClass.classLoader?.getResource("xime.yaml")).readText()
    @org.junit.Before fun loadActualLayoutBindings() {
        val assets = mock<android.content.res.AssetManager>()
        whenever(assets.open("xime.yaml")).thenAnswer { defaults.byteInputStream() }
        whenever(assets.open("xime.custom.yaml")).thenThrow(java.io.IOException("No custom override"))
        val context = mock<android.content.Context>()
        whenever(context.assets).thenReturn(assets)
        whenever(context.filesDir).thenReturn(temp.root)
        whenever(context.getSharedPreferences("kime_settings", android.content.Context.MODE_PRIVATE))
            .thenReturn(mock<android.content.SharedPreferences>())
        KeysConfigHelper.loadConfig(context)
    }
    @org.junit.After fun resetActiveProfile() { KeysConfigHelper.setActiveKeyboardSchema("") }

    @Test fun japaneseAndNineKeyDoNotReceiveAlphabeticPreset() {
        val original = KeysConfigHelper.parseKeyboardYamlSection(defaults, "qwerty")!!["a"]
        KeysConfigHelper.setActiveKeyboardSchema("japanese")
        assertEquals(KeysConfigHelper.parseKeyboardYamlSection(defaults, "qwerty_japanese")!!["a"],
            KeysConfigHelper.getKeyGesture("a", false))
        KeysConfigHelper.setActiveKeyboardSchema("t9")
        assertEquals(LayoutKind.T9, InputProfiles.current("t9").layout.kind)
        assertEquals(original, KeysConfigHelper.getKeyGesture("a", false))
    }

    @Test fun fourteenKeyLettersKeepTheirOwn26KeySymbols() {
        // 14 键：双字母键左半边是第一个字母的角标，右半边按第二个字母单独查；落单的 l、m 和拆开的单字母同理。
        for (schema in listOf("pinyin_14jian", "double_pinyin_flypy_14jian")) {
            KeysConfigHelper.setActiveKeyboardSchema(schema)
            assertEquals(InputLayout.MERGED14, InputProfiles.current(schema).layout)
            for (key in listOf("qw", "er", "ty", "ui", "op", "as", "df", "gh", "jk", "l", "zx", "cv", "bn", "m")) {
                for (letter in key.map(Char::toString)) {
                    val gesture = KeysConfigHelper.getKeyGesture(if (letter == key.take(1)) key else letter, false)!!
                    assertEquals("$schema $key $letter 上", QwertySwipeSymbols.up.getValue(letter), gesture.swipeUp!!.value)
                    assertEquals("$schema $key $letter 下", QwertySwipeSymbols.down.getValue(letter), gesture.swipeDown!!.value)
                }
                // 点按与长按仍是 14 键自己的配置。
                assertEquals(key, KeysConfigHelper.getKeyGesture(key, false)!!.tap!!.label)
                assertNotNull(KeysConfigHelper.getKeyGesture(key, false)!!.longPress)
            }
            // 空格旁的逗号键不是字母，保持 14 键配置。
            assertEquals("。", KeysConfigHelper.getKeyGesture("'", false)!!.swipeUp!!.label)
        }
    }

    @Test fun presetMatchesEveryRequestedKeyExactly() {
        val keys = "qwertyuiopasdfghjklzxcvbnm"
        assertEquals(26, QwertySwipeSymbols.up.size)
        // 中文：上方数字和常用全角符号，下方半角符号。英文上角标同位置换半角，下角标放补充符号（10-19）。
        assertEquals("1234567890～—…·¥、【】！（）：；《》？", keys.map { QwertySwipeSymbols.up.getValue(it.toString()) }.joinToString(""))
        assertEquals("!@#$%^&*()~-_'=\\[]/\"`:;<>?", keys.map { QwertySwipeSymbols.down.getValue(it.toString()) }.joinToString(""))
        assertEquals("1234567890~-…·¥\\[]!():;<>?", keys.map { QwertySwipeSymbols.englishUp.getValue(it.toString()) }.joinToString(""))
        assertEquals("!@#$%^&*()≈+_'=|{}/\"`×÷≤≥°", keys.map { QwertySwipeSymbols.englishDown.getValue(it.toString()) }.joinToString(""))
    }
    @Test fun editorOverridesYamlAndPreservesTapAndLongPress() {
        val original = KeyGestureConfig(tap = GestureDef(value = "a"), longPress = LongPressConfig())
        val yaml = KeyGestureConfig(swipeUp = GestureDef(value = "existing"))
        assertEquals("existing", QwertySwipeSymbols.apply("a", original, emptyMap(), yaml)!!.swipeUp!!.value)
        val edited = QwertySwipeSymbols.apply("a", original, mapOf("up.a" to "★", "down.a" to ""), yaml)!!
        assertEquals("★", edited.swipeUp!!.value)
        assertEquals(GestureAction.NONE, edited.swipeDown!!.action)
        assertEquals(original.tap, edited.tap)
        assertEquals(original.longPress, edited.longPress)
        assertNull(QwertySwipeSymbols.apply("space", null, emptyMap(), null))
    }
    @Test fun productionAccessorsAgreeOnChineseAndEnglishGestures() {
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
        for (ascii in listOf(false, true)) for (key in QwertySwipeSymbols.keys.map(Char::toString)) {
            assertEquals(QwertySwipeSymbols.up(ascii)[key], KeysConfigHelper.getSwipeUpCommitValue(key, ascii))
            assertEquals(QwertySwipeSymbols.up(ascii)[key], KeysConfigHelper.getSwipeUpLabel(key, ascii))
            assertEquals(QwertySwipeSymbols.down(ascii)[key], KeysConfigHelper.getKeyGesture(key, ascii)!!.swipeDown!!.value)
            assertEquals(GestureAction.COMMIT, KeysConfigHelper.getSwipeDownAction(key, ascii))
        }
    }
}
