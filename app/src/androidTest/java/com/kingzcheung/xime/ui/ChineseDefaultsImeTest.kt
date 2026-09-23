package com.kingzcheung.xime.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.rime.*
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 在清空过应用数据的专用模拟器运行，不能依赖开发者个人启用列表。 */
class ChineseDefaultsImeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun installedDefaultsOffer26And14KeysAndCommitChinese(): Unit = runBlocking {
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        val engine = RimeEngine.getInstance()
        lateinit var editor: EditText
        assertEquals("dynamic", SettingsPreferences.getKeyboardTheme(context))
        assertEquals(1, SettingsPreferences.getDarkMode(context))
        assertTrue(KeyboardThemes.getThemeById("dynamic").isDynamic)
        val (user, shared) = RimeConfigHelper.initializeRimeDataAsync(context)
        engine.initialize(user, shared)
        assertTrue(RimeConfigHelper.ensureDeployment(context))
        assertTrue(engine.ensureSession())
        assertTrue(SchemaManager.getEnabledSchemas(context).containsAll(ChineseSchemas.ids))
        val discovered = SchemaManager.discoverSchemas(context).associateBy { it.schemaId }
        assertEquals("中文26键", discovered["pinyin_simp"]?.name)
        assertEquals("中文14键", discovered["pinyin_14jian"]?.name)
        ChineseSchemas.ids.forEach { assertTrue("$it 已编译", SchemaManager.isSchemaCompiled(context, it)) }
        shell("ime enable ${context.packageName}/.service.XimeInputMethodService")
        shell("ime set ${context.packageName}/.service.XimeInputMethodService")
        rule.setContent { AndroidView(factory = { EditText(it).also { editor = it; it.hint = "全新安装中文输入验证" } }, modifier = Modifier.fillMaxWidth().height(120.dp)) }
        rule.runOnUiThread { editor.requestFocus(); (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("language-key-control", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        fun chooseMode(id: String) {
            rule.waitForIdle()
            val globe = rule.onAllNodesWithTag("language-key-control", useUnmergedTree = true).onLast()
            globe.performTouchInput { down(center) }
            rule.waitUntil(3000) { rule.onAllNodesWithTag("language-schema:$id").fetchSemanticsNodes().isNotEmpty() }
            val choice = rule.onNodeWithTag("language-schema:$id")
            choice.performScrollTo()
            val target = choice.fetchSemanticsNode()
            val key = globe.fetchSemanticsNode()
            globe.performTouchInput { moveTo(target.positionOnScreen + Offset(target.size.width / 2f, target.size.height / 2f) - key.positionOnScreen); up() }
            rule.waitUntil(10_000) { engine.getCurrentSchema() == id && !engine.isAsciiMode() }
        }
        fun typeAndCommit(labels: List<String>, screenshotName: String) {
            labels.forEach { rule.onNodeWithText(it, ignoreCase = true).performTouchInput { down(center); up() } }
            rule.waitUntil(10_000) { rule.onAllNodesWithText("你好").fetchSemanticsNodes().isNotEmpty() }
            val dir = File(context.getExternalFilesDir(null), "defaults").apply { mkdirs() }
            i.uiAutomation.takeScreenshot().also { bitmap -> File(dir, "$screenshotName.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
            rule.onNodeWithText("你好").performClick()
            rule.waitUntil(5000) { var text = ""; rule.runOnUiThread { text = editor.text.toString() }; text == "你好" }
            rule.runOnUiThread { editor.setText("") }
        }
        try {
            chooseMode("pinyin_simp")
            rule.waitUntil(5000) { rule.onAllNodesWithText("n", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(26, KeysConfigHelper.getKeyRows(false).flatten().count { it.length == 1 && it[0].isLetter() })
            typeAndCommit(listOf("n","i","h","a","o"), "chinese-26-default")
            chooseMode("pinyin_14jian")
            rule.waitUntil(5000) { rule.onAllNodesWithText("bn", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
            assertEquals("qwerty_14", KeysConfigHelper.mergedSectionForSchema("pinyin_14jian"))
            typeAndCommit(listOf("bn","ui","gh","as","op"), "chinese-14-default")
        } finally { engine.clearQueuedComposition() }
    }
    private fun shell(cmd: String) = ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)).bufferedReader().use { it.readText() }
}
