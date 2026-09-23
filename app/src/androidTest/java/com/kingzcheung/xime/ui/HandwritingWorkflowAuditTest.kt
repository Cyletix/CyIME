package com.kingzcheung.xime.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.settings.SettingsPreferences
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 在真实 IME 窗口中覆盖手写文本归属及会话重启；只用于隔离模拟器。 */
class HandwritingWorkflowAuditTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun separatorsSelectionAndInputRestartKeepHandwritingConsistent(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val keys = listOf(SettingsPreferences.KEY_SHOW_CANDIDATE_CANCEL_BUTTON, "floating_mode", "floating_offset_x", "floating_offset_y",
            "keyboard_height_dp", "keyboard_bottom_padding_dp", "keyboard_opacity",
            "current_schema", "current_schema_dual")
        val saved = keys.associateWith { prefs.all[it] }
        val previousIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val ime = "${context.packageName}/.service.XimeInputMethodService"
        val enabledBefore = shell("ime list -s").lineSequence().any { it == ime }
        val engine = RimeEngine.getInstance()
        var previousSchema = ""
        var previousAscii = false
        var previousSavedAscii = false
        lateinit var editor: EditText
        try {
            SettingsPreferences.setShowCandidateCancelButton(context, true)
            val (userDir, sharedDir) = RimeConfigHelper.initializeRimeDataAsync(context)
            engine.initialize(userDir, sharedDir)
            assertTrue(RimeConfigHelper.ensureDeployment(context))
            assertTrue(engine.ensureSession())
            previousSchema = engine.getCurrentSchema()
            previousAscii = engine.isAsciiMode()
            previousSavedAscii = engine.getUserConfigBool("var/option/ascii_mode")
            SettingsPreferences.setKeyboardHeightDp(context, 300)
            SettingsPreferences.setKeyboardBottomPaddingDp(context, 0)
            SettingsPreferences.setFloatingMode(context, false)
            SettingsPreferences.setCurrentSchema(context, "pinyin_simp")
            assertTrue(engine.switchSchema("pinyin_simp"))
            engine.setOption("ascii_mode", false)
            engine.setUserConfigBool("var/option/ascii_mode", false)
            shell("ime enable $ime")
            shell("ime set $ime")
            rule.setContent {
                AndroidView(factory = { EditText(it).also { view -> editor = view; view.hint = "真实输入法回归输入框" } },
                    modifier = Modifier.fillMaxWidth().height(80.dp))
            }
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            rule.runOnUiThread { editor.requestFocus(); imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
            rule.waitUntil(30_000) { rule.onAllNodesWithContentDescription("手写").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("手写").performClick()
            fun text(): String { var value = ""; rule.runOnUiThread { value = editor.text.toString() }; return value }
            fun key(action: String) { rule.onNodeWithTag("handwriting-key:$action").performTouchInput { down(center); up() } }
            fun stroke() {
                rule.onNodeWithTag("handwriting-canvas").performTouchInput {
                    down(androidx.compose.ui.geometry.Offset(60f, 100f))
                    moveTo(androidx.compose.ui.geometry.Offset(120f, 100f))
                    moveTo(androidx.compose.ui.geometry.Offset(240f, 100f)); up()
                }
            }
            fun recognize() {
                stroke()
                rule.waitUntil(20_000) { rule.onAllNodesWithContentDescription("取消输入").fetchSemanticsNodes().isNotEmpty() }
            }
            // A separator must finish ownership of the preceding handwritten character.
            for (separator in listOf("space", "，", "。", "enter")) {
                recognize()
                key(separator)
                rule.waitForIdle()
                val prefix = text()
                recognize()
                assertTrue("手写在 $separator 后的下一字必须追加，prefix=$prefix actual=${text()}",
                    text().startsWith(prefix) && text().length > prefix.length)
                rule.onNodeWithContentDescription("取消输入").performClick()
                assertEquals("取消仅删除分隔符后的当前字", prefix, text())
            }
            // Cursor movement/selection invalidates candidate replacement, without deleting unrelated text.
            recognize()
            val written = text()
            rule.runOnUiThread { editor.setSelection(0, editor.text.length) }
            rule.onNodeWithContentDescription("取消输入").performClick()
            assertEquals("选区不能被旧候选取消删除", written, text())
            rule.runOnUiThread { editor.setSelection(editor.text.length) }
            recognize()
            val beforeDelete = text()
            rule.runOnUiThread { editor.setSelection(0) }
            key("delete")
            rule.waitForIdle()
            assertEquals("光标在开头删除不能回删手写尾部", beforeDelete, text())
            rule.runOnUiThread { editor.setSelection(editor.text.length) }
            val prefixBefore = text()
            recognize()
            val glyph = text().removePrefix(prefixBefore)
            val duplicate = text() + "中间内容" + glyph
            // 修改原 Editable，避免 setText 更换缓冲区触发 restartInput 而提前清掉候选。
            rule.runOnUiThread { editor.text.append("中间内容").append(glyph); editor.setSelection(editor.text.length) }
            rule.onNodeWithContentDescription("取消输入").performClick()
            assertEquals("相同文字的新位置不属于旧手写候选", duplicate, text())
            // A new input session must cancel its pending recognition even while the panel stays handwriting.
            rule.runOnUiThread { editor.setSelection(editor.text.length) }
            stroke()
            rule.runOnUiThread { editor.setText("新输入框"); editor.setSelection(editor.text.length); imm.restartInput(editor) }
            Thread.sleep(1800)
            rule.waitForIdle()
            assertEquals("旧会话识别不能写入重启后的输入框", "新输入框", text())
            rule.onAllNodesWithContentDescription("取消输入").assertCountEquals(0)
            screenshot("handwriting-audit")
        } catch (failure: Throwable) {
            screenshot("failure")
            throw failure
        } finally {
            if (previousSchema.isNotEmpty()) {
                engine.switchSchema(previousSchema)
                engine.setOption("ascii_mode", previousAscii)
                engine.setUserConfigBool("var/option/ascii_mode", previousSavedAscii)
            }
            prefs.edit().also { edit ->
                saved.forEach { (key, value) ->
                    when (value) {
                        null -> edit.remove(key)
                        is Boolean -> edit.putBoolean(key, value)
                        is Int -> edit.putInt(key, value)
                        is Float -> edit.putFloat(key, value)
                        is String -> edit.putString(key, value)
                    }
                }
            }.commit()
            if (!previousIme.isNullOrBlank()) shell("ime set $previousIme")
            if (!enabledBefore) shell("ime disable $ime")
        }
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ime-regression").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
