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

    @Test fun cyletix10RetainsResearchLettersAndSemicolonInMobileRows() {
        val layout = Cyletix10Layout.preset
        assertEquals(listOf("qwdrf;jkyp", "asetghliou", "zxcvbnm"),
            layout.rows.map { it.joinToString("") })
        assertEquals(('a'..'z').toSet(), layout.rows.flatten().joinToString("").filter { it.isLetter() }.toSet())
        assertEquals(1, layout.rows.flatten().count { it == ";" })
        assertTrue(layout.valid())
        assertEquals(";", layout.gestures(emptyMap())[";"]?.tap?.value)
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
