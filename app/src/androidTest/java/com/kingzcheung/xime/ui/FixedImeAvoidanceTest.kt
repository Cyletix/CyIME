package com.kingzcheung.xime.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.rime.*
import com.kingzcheung.xime.settings.SettingsPreferences
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses an isolated app ID: the user's installed IME and dictionaries are untouched. */
class FixedImeAvoidanceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun fixedPanelReservesToolbarAndBottomSendRowAtEveryWidth() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeTrue(context.packageName.endsWith(".freshcheck"))
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val previousIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val engine = RimeEngine.getInstance()
        lateinit var editor: EditText
        try {
            val (user, shared) = RimeConfigHelper.initializeRimeData(context)
            engine.initialize(user, shared)
            assertTrue(RimeConfigHelper.ensureDeployment(context))
            assertTrue(engine.ensureSession(180_000L))
            assertTrue(engine.switchSchema("rime_ice"))
            engine.setOption("ascii_mode", false)
            SettingsPreferences.setCurrentSchema(context, "rime_ice")
            prefs.edit().putBoolean("landscape_layout_v2", true)
                .putBoolean("floating_mode", false).putBoolean("floating_mode_landscape", false)
                .putInt("keyboard_height_dp",300).putInt("keyboard_height_dp_landscape",300)
                .putInt("fixed_width_dp",400).putInt("fixed_width_dp_landscape",400)
                .putInt("keyboard_bottom_padding_dp",0).putInt("keyboard_bottom_padding_dp_landscape",0)
                .putFloat("keyboard_opacity",1f).putFloat("keyboard_opacity_landscape",1f).commit()
            shell("ime enable ${context.packageName}/com.kingzcheung.xime.service.XimeInputMethodService")
            shell("ime set ${context.packageName}/com.kingzcheung.xime.service.XimeInputMethodService")
            rule.runOnUiThread { rule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
            rule.setContent {
                Column(Modifier.fillMaxSize().background(Color(0xFFECECEC))) {
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth().height(56.dp).background(Color.White).testTag("host-send-row")) {
                        AndroidView(factory = { EditText(it).also { editor = it; it.hint = "输入框与发送按钮应在工具栏上方" } }, modifier = Modifier.weight(1f).fillMaxHeight())
                        Text("发送", modifier = Modifier.padding(12.dp))
                    }
                }
            }
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            for (width in listOf(400,0)) {
                prefs.edit().putInt("fixed_width_dp",width).putInt("fixed_width_dp_landscape",width)
                    .putInt("keyboard_height_dp", if (width == 0) 340 else 300)
                    .putInt("keyboard_height_dp_landscape", if (width == 0) 340 else 300).commit()
                rule.runOnUiThread { editor.requestFocus(); imm.restartInput(editor); imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
                rule.waitUntil(30000) { rule.onAllNodesWithTag("fixed-keyboard-card").fetchSemanticsNodes().isNotEmpty() }
                rule.waitUntil(10000) {
                    val card = rule.onNodeWithTag("fixed-keyboard-card").fetchSemanticsNode()
                    val row = rule.onNodeWithTag("host-send-row").fetchSemanticsNode()
                    val correctWidth = if (width > 0) kotlin.math.abs(card.size.width / context.resources.displayMetrics.density - width) < 2
                        else card.size.width > 600 * context.resources.displayMetrics.density
                    correctWidth && row.positionOnScreen.y + row.size.height <= card.positionOnScreen.y + 2f
                }
                val card = rule.onNodeWithTag("fixed-keyboard-card").fetchSemanticsNode()
                val toolbar = rule.onNodeWithTag("toolbar-leading").fetchSemanticsNode()
                assertTrue("Toolbar must remain inside reserved panel", toolbar.positionOnScreen.y >= card.positionOnScreen.y)
                assertTrue("Toolbar must be at panel top", toolbar.positionOnScreen.y < card.positionOnScreen.y + 44 * context.resources.displayMetrics.density)
                assertTrue("System must reserve whole panel", editor.rootWindowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom >= card.size.height)
                val file = File(context.getExternalFilesDir(null), "fixed-ime-avoidance-$width.png")
                file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
            }
        } finally {
            shell("ime set $previousIme")
            shell("ime disable ${context.packageName}/com.kingzcheung.xime.service.XimeInputMethodService")
        }
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
}
