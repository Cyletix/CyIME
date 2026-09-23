package com.kingzcheung.xime.handwriting

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.ui.keyboard.HandwritingKeyboardLayout
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 前置条件：隔离设备已安装原市场 ochwpro；本测试不下载模型，也不部署 Rime。 */
@RunWith(AndroidJUnit4::class)
class InstalledHandwritingModelTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun installedModelRunsRealInferenceAndRendersTheHandwritingPage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val showPage = mutableStateOf(true)
        var pageShown = false
        try {
            assertTrue("需先安装原市场手写模型", HandwritingEngine.hasModel(context))
            assertTrue("手写模型应能通过真实推理服务加载", withContext(Dispatchers.IO) {
                HandwritingEngine.initialize(context)
            })
            // 一条合法横向笔画：只验证 ONNX/词表/IPC 全链路，不断言识别准确率。
            val stroke = (0..12).map { index -> (24f + index * 12f) to (90f + index * 0.2f) }
            val candidates = withContext(Dispatchers.IO) {
                HandwritingEngine.predict(listOf(stroke), topK = 5)
            }
            assertTrue("真实推理应返回 1 至 5 个候选", candidates.size in 1..5)
            assertTrue("候选字符及分数应有效", candidates.all { it.char.isNotBlank() && it.score.isFinite() })

            rule.setContent {
                MaterialTheme {
                    if (showPage.value) HandwritingKeyboardLayout(
                        modifier = Modifier.fillMaxWidth().height(300.dp).testTag("real-handwriting-page"),
                        bottomPaddingDp = 0,
                        keyTextColor = MaterialTheme.colorScheme.onSurface,
                        keyBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        specialKeyBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        specialKeyTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            pageShown = true
            rule.onNodeWithContentDescription("删除").assertIsDisplayed()
            val delete = rule.onNodeWithContentDescription("删除").fetchSemanticsNode().boundsInRoot
            val comma = rule.onNodeWithText("，").fetchSemanticsNode().boundsInRoot
            val period = rule.onNodeWithText("。").fetchSemanticsNode().boundsInRoot
            val enter = rule.onNodeWithContentDescription("回车").fetchSemanticsNode().boundsInRoot
            assertTrue("手写右列应依次为删除、逗号、句号、回车",
                delete.center.y < comma.center.y && comma.center.y < period.center.y && period.center.y < enter.center.y)
            val page = rule.onNodeWithTag("real-handwriting-page")
            assertTrue("四个功能键应在右列", delete.center.x > page.fetchSemanticsNode().boundsInRoot.center.x)
            val directory = File(context.getExternalFilesDir(null), "ime-regression").apply { mkdirs() }
            File(directory, "handwriting-real-model.png").outputStream().use { output ->
                assertTrue(page.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            if (pageShown) {
                rule.runOnUiThread { showPage.value = false }
                rule.waitForIdle()
            }
            withContext(Dispatchers.IO) { HandwritingEngine.release() }
        }
    }
}
