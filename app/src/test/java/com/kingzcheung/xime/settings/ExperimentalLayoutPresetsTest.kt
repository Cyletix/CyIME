package com.kingzcheung.xime.settings

import android.content.Context
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ExperimentalLayoutPresetsTest {
    @get:Rule val temporary = TemporaryFolder()

    /** Every symbol stays on its QWERTY key: only the 26 letters move, in the phone's 10/9/7 rows. */
    @Test fun cyletix10MovesOnlyLettersAndKeepsSymbolsHome() {
        val layout = Cyletix10Layout.preset
        assertEquals(listOf("qwbrtydfpu", "aseoghjin", "zxckvlm"),
            layout.rows.map { it.joinToString("") })
        assertEquals(listOf(10, 9, 7), layout.rows.map { it.size })
        assertEquals(('a'..'z').toList(), layout.rows.flatten().joinToString("").toList().sorted())
        assertFalse(layout.hasSemicolon())
        assertTrue(layout.valid())
        assertEquals("Cyletix10（实验）", layout.name)
        assertTrue(CustomKeyboardLayouts.isCustom(Cyletix10Layout.ID))
    }

    @Test fun builtinCandidatesDoNotSeedUserLayoutsAndSavedQwjrtkWins() {
        val directory = temporary.newFolder()
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(directory)
        val userFile = File(directory, "custom-keyboard-layouts.json")
        try {
            assertTrue(CustomKeyboardLayouts.load(context).isEmpty())
            assertEquals("[]", userFile.readText())
            assertNotNull(CustomKeyboardLayouts.find(QwjrtkLayout.ID))
            assertNotNull(CustomKeyboardLayouts.find(Cyletix10Layout.ID))
            userFile.writeText("""[{"id":"pinyin_qwjrtk","name":"个人双指","rows":"q,w,j,r,t,k,u,i,o,p/a,s,d,f,g,h,e,n,l/z,x,c,y,b,v,m"}]""")
            CustomKeyboardLayouts.load(context)
            assertEquals("个人双指", CustomKeyboardLayouts.find(QwjrtkLayout.ID)?.name)
        } finally {
            userFile.writeText("[]")
            CustomKeyboardLayouts.load(context)
        }
    }
}
