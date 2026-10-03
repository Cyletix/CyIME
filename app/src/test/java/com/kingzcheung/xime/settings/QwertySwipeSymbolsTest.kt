package com.kingzcheung.xime.settings

import com.kingzcheung.xime.keyboard.GestureAction
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class QwertySwipeSymbolsTest {
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

    @Test fun presetMatchesEveryRequestedKeyExactly() {
        val keys = "qwertyuiopasdfghjklzxcvbnm"
        assertEquals(26, QwertySwipeSymbols.up.size)
        assertEquals("1234567890@-*_[]()\\'\":;!?/", keys.map { QwertySwipeSymbols.up.getValue(it.toString()) }.joinToString(""))
        assertEquals("!@#$%^&*()`~·—…°{}|=+-×÷<>", keys.map { QwertySwipeSymbols.down.getValue(it.toString()) }.joinToString(""))
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
            assertEquals(QwertySwipeSymbols.up[key], KeysConfigHelper.getSwipeUpCommitValue(key, ascii))
            assertEquals(QwertySwipeSymbols.up[key], KeysConfigHelper.getSwipeUpLabel(key, ascii))
            assertEquals(QwertySwipeSymbols.down[key], KeysConfigHelper.getKeyGesture(key, ascii)!!.swipeDown!!.value)
            assertEquals(GestureAction.COMMIT, KeysConfigHelper.getSwipeDownAction(key, ascii))
        }
    }
}
