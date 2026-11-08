package com.kingzcheung.xime.ui.render

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.handwriting.training.HandwritingContribution
import com.kingzcheung.xime.handwriting.training.TrainingPoint
import com.kingzcheung.xime.handwriting.training.TrainingPromptType
import com.kingzcheung.xime.handwriting.training.TrainingSample
import com.kingzcheung.xime.handwriting.training.TrainingSampleStore
import com.kingzcheung.xime.handwriting.training.TrainingStroke
import com.kingzcheung.xime.ui.settings.textPrompts
import com.kingzcheung.xime.ui.settings.HandwritingSettingsContent
import com.kingzcheung.xime.ui.settings.HandwritingTrainingScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 「手写训练」「贡献手写样本」「手写数据采集」合成一个入口（用户 11-05：两个一样的界面）。
 * 自用包：手写页只有「手写训练」；贡献题在题库菜单最后，来回切换并记住上次停在哪边；
 * 原数据采集变成「自己的文字」题库：粘贴一段话逐字写，样本存进训练样本，可删某个字的样本。
 * 公开包：手写页只有「贡献手写样本」，写字页没有题库菜单。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h860dp-xhdpi")
class HandwritingTrainingMergeTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun selfBuildHasOneWritingEntry() {
        rule.setContent { MaterialTheme { HandwritingSettingsContent(onBack = {}, devTools = true) } }
        rule.onNodeWithText("手写训练").assertExists()
        rule.onAllNodesWithText("手写数据采集").assertCountEquals(0)
        rule.onAllNodesWithText("贡献手写样本").assertCountEquals(0)
        rule.onAllNodesWithText("手写训练模式").assertCountEquals(0)
    }

    @Test fun publicBuildKeepsOnlyContribute() {
        rule.setContent { MaterialTheme { HandwritingSettingsContent(onBack = {}, devTools = false) } }
        rule.onNodeWithText("贡献手写样本").assertExists()
        rule.onAllNodesWithText("手写训练").assertCountEquals(0)
        rule.onAllNodesWithText("手写数据采集").assertCountEquals(0)
    }

    @Test fun publicBuildHasNoBankMenu() {
        rule.setContent { MaterialTheme { HandwritingTrainingScreen(onBack = {}, contribute = true, devTools = false) } }
        rule.waitForIdle()
        rule.onNodeWithText("贡献手写样本").assertExists()
        rule.onNodeWithContentDescription("切换题库").assertDoesNotExist()
    }

    @Test fun contributeShowsHowToAndUploadButtonUpFront() {
        rule.setContent { MaterialTheme { HandwritingTrainingScreen(onBack = {}, contribute = true, devTools = false) } }
        rule.waitForIdle()
        // 不用翻右上角菜单：顶栏下面一行说怎么写，右边就是「上传」，还没写时不能点
        rule.onNodeWithTag("contribute-status").assertExists()
        rule.onNodeWithText("照着上面的字写在框里", substring = true).assertExists()
        rule.onNodeWithTag("contribute-upload").assertIsNotEnabled()
    }

    @Test fun firstOpenOfTrainingEntryLandsOnContribute() {
        assertTrue(HandwritingContribution.lastOpened(context))
    }

    @Test fun bankMenuSwitchesBetweenTrainingAndContribute() {
        rule.setContent { MaterialTheme { HandwritingTrainingScreen(onBack = {}, devTools = true) } }
        rule.waitForIdle()

        // 训练 → 贡献：贡献题在菜单最后
        rule.onNodeWithContentDescription("切换题库").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("贡献手写样本").performClick()
        rule.waitForIdle()
        assertTrue(HandwritingContribution.lastOpened(context))
        rule.onNodeWithText("贡献手写样本").assertExists()
        // 贡献那边仍有题库菜单，能切回训练题库
        rule.onNodeWithContentDescription("切换题库").assertExists()

        // 贡献 → 训练的「假名」题库
        rule.onNodeWithContentDescription("切换题库").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("假名").performClick()
        rule.waitForIdle()
        assertFalse(HandwritingContribution.lastOpened(context))
        rule.onNodeWithText("假名").assertExists()
        rule.onAllNodesWithText("贡献手写样本").assertCountEquals(0)
    }

    @Test fun ownTextBankWritesCharByChar() {
        rule.setContent { MaterialTheme { HandwritingTrainingScreen(onBack = {}, devTools = true) } }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("切换题库").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("自己的文字").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("training-text-input").performTextInput("你好 你们")
        rule.onNodeWithText("开始").performClick()
        rule.waitForIdle()
        // 去掉空白、按出现顺序去重：你 好 们，从第一个字写起
        rule.onNodeWithText("1/3", substring = true).assertExists()
        rule.onNodeWithText("你").assertExists()
    }

    @Test fun textPromptsDedupeAndSkipSpaces() {
        val prompts = textPrompts("你好 a你\n好!")
        assertEquals(listOf("你", "好", "a", "!"), prompts.map { it.label })
        assertEquals(TrainingPromptType.Chinese, prompts[0].type)
        assertEquals(TrainingPromptType.Custom, prompts[2].type)
        assertTrue(prompts.all { it.id == "text:${it.label}" })
        assertTrue(textPrompts("  \n ").isEmpty())
    }

    @Test fun removeSamplesDropsOnlyThatPrompt() {
        val store = TrainingSampleStore(context, "handwriting_merge_test")
        val stroke = listOf(TrainingStroke(listOf(TrainingPoint(0f, 0f, 0L), TrainingPoint(1f, 1f, 10L))))
        fun sample(label: String) = TrainingSample(textPrompts(label).first(), 100f, 100f, 1f, stroke, 0L)
        store.append(sample("你"), "test", "test")
        store.append(sample("好"), "test", "test")
        store.append(sample("你"), "test", "test")
        assertEquals(2, store.removeSamples("text:你"))
        assertEquals(mapOf("text:好" to 1), store.countsByPrompt())
        assertEquals(0, store.removeSamples("text:你"))
    }
}
