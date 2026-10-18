package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VoicePillBackspaceTest {
    @Test fun `tap and long-press repeat both delete with key feedback like the keyboard delete key`() {
        val events = mutableListOf<String>()
        val actions = voicePillBackspaceActions(
            onDelete = { events += "delete" }, onPress = { events += "down" }, onRelease = { events += "up" },
            onPressedChange = { events += "pressed:$it" })
        // 长按连删依赖 onRepeat：键盘删除键也只靠它连续删除。
        assertNotNull(actions.onRepeat)
        actions.onPress(); actions.onTap(); actions.onRelease()
        actions.onPress(); actions.onRepeat!!.invoke(); actions.onPress(); actions.onRepeat!!.invoke(); actions.onRelease()
        assertEquals(listOf("pressed:true", "down", "delete", "pressed:false", "up",
            "pressed:true", "down", "delete", "pressed:true", "down", "delete", "pressed:false", "up"), events)
    }
}
