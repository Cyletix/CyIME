package com.kingzcheung.xime.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.provider.Settings
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
import com.kingzcheung.xime.settings.SettingsPreferences
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LandscapeImeWorkflowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun landscapeDefaultsAndResizeStayAttachedToTheActualFloatingCard(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val saved = prefs.all.toMap()
        val previousIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val engine = RimeEngine.getInstance()
        lateinit var editor: EditText
        try {
            instrumentation.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90)
            rule.waitUntil(5000) { context.resources.configuration.screenWidthDp > context.resources.configuration.screenHeightDp }
            val (user, shared) = RimeConfigHelper.initializeRimeDataAsync(context)
            engine.initialize(user, shared); assertTrue(RimeConfigHelper.ensureDeployment(context)); assertTrue(engine.ensureSession())
            assertTrue(context.resources.configuration.screenWidthDp > context.resources.configuration.screenHeightDp)
            assertTrue(engine.switchSchema("t9_pinyin")); engine.setOption("ascii_mode", false)
            engine.setUserConfigBool("var/option/ascii_mode", false)
            SettingsPreferences.setCurrentSchema(context, "t9_pinyin")
            prefs.edit().remove("landscape_layout_v2").remove("keyboard_opacity_landscape")
                .putBoolean("floating_mode_landscape", false).putInt("keyboard_height_dp_landscape", 60).commit()
            assertTrue(SettingsPreferences.isFloatingMode(context, true))
            assertEquals(0.5f, SettingsPreferences.getKeyboardOpacity(context), 0.001f)
            assertTrue(SettingsPreferences.getKeyboardHeightDp(context, true) >= 228)
            shell("ime enable ${context.packageName}/com.kingzcheung.xime.service.XimeInputMethodService")
            shell("ime set ${context.packageName}/com.kingzcheung.xime.service.XimeInputMethodService")
            rule.setContent { AndroidView(factory = { EditText(it).also { editor = it; it.hint = "横屏悬浮输入回归" } }, modifier = Modifier.fillMaxWidth().height(60.dp)) }
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            rule.runOnUiThread { editor.requestFocus(); imm.restartInput(editor); imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
            rule.waitUntil(30000) { rule.onAllNodesWithTag("floating-drag-bar").fetchSemanticsNodes().isNotEmpty() }
            val density = context.resources.displayMetrics.density
            fun cardWidth(): Float = rule.onNodeWithTag("floating-keyboard-card").fetchSemanticsNode().size.width.toFloat()
            val nineWidth = cardWidth()
            val symbol = rule.onNodeWithTag("t9-symbol-key").fetchSemanticsNode().boundsInRoot
            assertTrue("横屏最低行高保持可用: $symbol density=$density", symbol.height >= 40 * density)
            screenshot("landscape-t9")
            rule.onNodeWithTag("toolbar-leading").performClick()
            rule.waitForIdle()
            if (rule.onAllNodesWithTag("menu-item:键盘调节").fetchSemanticsNodes().isEmpty()) {
                rule.onNodeWithTag("menu-pages", useUnmergedTree = true).performTouchInput { swipeLeft() }
            }
            rule.onNodeWithTag("menu-item:键盘调节").performClick()
            rule.onNodeWithTag("keyboard-opacity-slider").assertIsDisplayed()
            val before = rule.onNodeWithContentDescription("确认").fetchSemanticsNode().positionOnScreen
            rule.onNodeWithTag("floating-drag-bar").performTouchInput { down(center); moveBy(Offset(-65f, -30f) * density); up() }
            val after = rule.onNodeWithContentDescription("确认").fetchSemanticsNode().positionOnScreen
            assertTrue("调节面板必须随键盘左右移动", kotlin.math.abs(after.x - before.x) > 10 * density)
            val confirm = rule.onNodeWithContentDescription("确认").fetchSemanticsNode()
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val nav = editor.rootWindowInsets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            assertTrue("确认不被导航栏挡住", confirm.positionOnScreen.y + confirm.size.height < bitmap.height - nav)
            bitmap.recycle()
            screenshot("landscape-resize-moved")
            rule.onNodeWithContentDescription("悬浮键盘").performClick()
            rule.onNodeWithTag("floating-drag-bar").assertDoesNotExist()
            rule.onNodeWithTag("keyboard-opacity-slider").assertIsDisplayed()
            rule.onNodeWithContentDescription("悬浮键盘").performClick()
            rule.onNodeWithTag("floating-drag-bar").assertIsDisplayed()
            rule.onNodeWithContentDescription("确认").performClick()
            assertTrue(engine.switchSchema("japanese"))
            SettingsPreferences.setCurrentSchema(context, "japanese")
            rule.runOnUiThread { imm.restartInput(editor); imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
            rule.waitUntil(10000) { rule.onAllNodesWithText("Q").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("q").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("26键横屏宽约为九键1.2倍", 1.2f, cardWidth() / nineWidth, 0.06f)
            screenshot("landscape-qwerty")
        } catch (t: Throwable) { screenshot("landscape-failure"); throw t }
        finally {
            engine.clearComposition()
            prefs.edit().clear().also { edit -> saved.forEach { (k,v) -> when(v) { is String -> edit.putString(k,v); is Int -> edit.putInt(k,v); is Float -> edit.putFloat(k,v); is Long -> edit.putLong(k,v); is Boolean -> edit.putBoolean(k,v); is Set<*> -> edit.putStringSet(k,v.filterIsInstance<String>().toSet()) } } }.commit()
            if (!previousIme.isNullOrBlank()) shell("ime set $previousIme")
        }
    }
    private fun shell(cmd: String): String = ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)).bufferedReader().use { it.readText() }
    private fun screenshot(name: String) {
        val i = InstrumentationRegistry.getInstrumentation()
        val dir = File(i.targetContext.getExternalFilesDir(null), "round4").apply { mkdirs() }
        i.uiAutomation.takeScreenshot().also { bitmap -> File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
    }
}
