package com.kingzcheung.xime.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kingzcheung.xime.ui.settings.SetupStatus
import com.kingzcheung.xime.ui.settings.SetupWizardContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SetupWizardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun allStepsStayOnOnePageAndCompletionTracksRealState() {
        val state = mutableStateOf(SetupStatus())
        var finished = 0
        rule.setContent { MaterialTheme {
            SetupWizardContent(state.value, {}, {}, {}, {}, { finished++ })
        } }
        listOf("启用 CyIME", "准备输入方案", "切换到 CyIME").forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithText("选择 CyIME").assertIsNotEnabled()
        rule.onNodeWithText("完成三项后即可开始使用").assertIsNotEnabled()
        rule.runOnIdle { state.value = SetupStatus(imeEnabled = true, preparing = false, schemas = listOf("中文26键", "中文九键")) }
        rule.onNodeWithText("已启用").assertExists()
        rule.onNodeWithText("已就绪").assertExists()
        rule.onNodeWithText("选择 CyIME").assertIsEnabled()
        rule.onNodeWithText("✓ 设置成功，可以开始输入了").assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(imeSelected = true) }
        rule.onNodeWithText("✓ 设置成功，可以开始输入了").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("开始使用").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, finished)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "setup-wizard-complete.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        rule.runOnIdle { state.value = state.value.copy(imeEnabled = false) }
        rule.onNodeWithText("✓ 设置成功，可以开始输入了").assertDoesNotExist()
        rule.onNodeWithText("完成三项后即可开始使用").assertIsNotEnabled()
    }
    @Test fun preparationFailureHasRetryAndCannotClaimSuccess() {
        var retries = 0
        rule.setContent { MaterialTheme {
            SetupWizardContent(SetupStatus(true, true, false, listOf("中文九键"), "词库准备失败，请重试"), {}, {}, { retries++ }, {}, {})
        } }
        rule.onNodeWithText("需要处理").assertExists()
        rule.onNodeWithText("重试准备").performScrollTo().performClick()
        assertEquals(1, retries)
        rule.onNodeWithText("完成三项后即可开始使用").assertIsNotEnabled()
        rule.onNodeWithText("✓ 设置成功，可以开始输入了").assertDoesNotExist()
    }
}
