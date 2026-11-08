package com.kingzcheung.xime.audit

import android.app.Application
import android.provider.Settings
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.descendants
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.keyboard.KeyboardPanelLaunch
import com.kingzcheung.xime.ui.settings.ThemeSettingsContent
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "sw390dp-w390dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeTrialAuditTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun reset() {
        KeyboardPanelLaunch.previewVisible = false
        KeyboardPanelLaunch.typingRequested = false
        KeyboardPanelLaunch.cancel()
    }
    @Test fun closingTrialRetainsScrolledThemeRowAndFocus() {
        val app = rule.activity.application
        Settings.Secure.putString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD,
            app.packageName + "/com.kingzcheung.xime.service.XimeInputMethodService")
        rule.setContent { XimeTheme { ThemeSettingsContent(onBack = {}) } }
        val list = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        list.performScrollToIndex(2)
        fun offset() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val before = offset()
        val focusBefore = rule.activity.currentFocus
        rule.runOnIdle {
            KeyboardPanelLaunch.previewVisible = true
            KeyboardPanelLaunch.typingRequested = true
        }
        rule.onNodeWithTag("typing-trial").assertExists()
        rule.runOnIdle {
            val root = rule.activity.window.decorView as ViewGroup
            root.descendants.filterIsInstance<EditText>().single { it.tag == "typing-trial-field" }.setText("测试")
            rule.activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.onNodeWithTag("typing-trial").assertDoesNotExist()
        assertEquals("List scroll position", before, offset(), 0.01f)
        assertEquals("Previously focused view", focusBefore, rule.activity.currentFocus)
    }
}
