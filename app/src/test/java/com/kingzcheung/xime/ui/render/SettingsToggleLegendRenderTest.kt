package com.kingzcheung.xime.ui.render

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.settings.AutoSpaceLegend
import com.kingzcheung.xime.ui.settings.CandidateExtraLegend
import com.kingzcheung.xime.ui.settings.CandidateSpacingLegend
import com.kingzcheung.xime.ui.settings.DoublePinyinAbbreviationLegend
import com.kingzcheung.xime.ui.settings.EmailSuffixLegend
import com.kingzcheung.xime.ui.settings.EvenMergedKeysLegend
import com.kingzcheung.xime.ui.settings.SemicolonOnShiftLegend
import com.kingzcheung.xime.ui.settings.ShiftSyllableSplitLegend
import com.kingzcheung.xime.ui.settings.SplitRightGLegend
import com.kingzcheung.xime.ui.settings.SyllableSplitKeyLegend
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 开关下面的图例（11-02 限宽卡片，只画当前效果）：开关关着时不画多出来的那一项，开着时画出来。
 * 设置了 CYIME_RENDER_OUT 时，左右两栏（关／开）各出浅色、深色一张，手机宽 390dp。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1720dp-h1900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsToggleLegendRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Composable
    private fun AllLegends(on: Boolean) = Column(Modifier.width(390.dp).fillMaxHeight()
        .background(MaterialTheme.colorScheme.surfaceContainerLow), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SyllableSplitKeyLegend(on, comma = true, period = false, reversed = false)
        ShiftSyllableSplitLegend(on)
        SemicolonOnShiftLegend(on)
        DoublePinyinAbbreviationLegend(on)
        CandidateExtraLegend("rare-characters-legend", "gui", listOf("鬼", "贵", "归"), "槻", on, skipped = true)
        CandidateExtraLegend("datetime-candidates-legend", "shijian", listOf("时间"), "15:35", on)
        CandidateExtraLegend("candidate-emoji-legend", "kaixin", listOf("开心"), "😄", on)
        CandidateExtraLegend("symbol-names-legend", "jiahao", listOf("加号"), "+", on)
        CandidateExtraLegend("greek-letters-legend", "pi", listOf("批", "皮"), "π", on)
        AutoSpaceLegend("english-auto-space-legend", "点 world", "hello world", "|", on)
        AutoSpaceLegend("english-punctuation-space-legend", "打 ,", "Hello,", "|", on)
        EmailSuffixLegend(on)
        EvenMergedKeysLegend(on)
        SplitRightGLegend(on)
        CandidateSpacingLegend(on)
    }

    @Test fun legendsShowTheAddedItemOnlyWhenTheirSwitchIsOn() {
        val on = mutableStateOf(false)
        rule.setContent { XimeTheme(darkTheme = false) { AllLegends(on.value) } }
        val added = listOf("槻", "15:35", "😄", "+", "π", "微信支付", "qq.com", "分词", "␣")
        fun count(text: String) = rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size
        added.forEach { assertEquals("关着时不该有 $it", 0, count(it)) }
        rule.runOnIdle { on.value = true }
        rule.waitForIdle()
        added.forEach { assertEquals("开着时应有 $it", if (it == "␣") 2 else 1, count(it)) }
        // 每个图例都在限宽卡片里（SettingsLegend 打的 tag）。
        listOf("syllable-split-key-legend", "shift-syllable-split-legend", "semicolon-on-shift-legend",
            "double-pinyin-abbreviation-legend", "even-merged-keys-legend", "split-right-g-legend",
            "candidate-spacing-legend", "email-suffix-legend").forEach {
            assertEquals(it, 1, rule.onAllNodesWithTag(it).fetchSemanticsNodes().size)
        }
    }

    /** 11-02 挪过位置的几页：语言与方案（只留入口）、中文输入、英文输入、候选词（四组）。左右并排，浅色、深色各一张。 */
    @Test fun renderMovedPages() {
        val out = System.getenv("CYIME_RENDER_OUT")
        org.junit.Assume.assumeTrue(!out.isNullOrEmpty())
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.kingzcheung.xime.ui.theme.KeyboardThemes.initFromConfig(context)
        // 同 SettingsSwitchStatesRenderTest：语言页在后台线程读布局，状态先在全局快照里建好。
        com.kingzcheung.xime.settings.CustomKeyboardLayouts.load(context)
        com.kingzcheung.xime.settings.LetterLayouts.load(context)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        val dark = mutableStateOf(false)
        rule.setContent {
            XimeTheme(darkTheme = dark.value) {
                Row {
                    androidx.compose.foundation.layout.Box(Modifier.width(420.dp).fillMaxHeight()) {
                        com.kingzcheung.xime.ui.settings.LanguageSettingsContent(onBack = {})
                    }
                    androidx.compose.foundation.layout.Box(Modifier.width(420.dp).fillMaxHeight()) {
                        com.kingzcheung.xime.ui.settings.ChineseInputSettingsContent(onBack = {})
                    }
                    androidx.compose.foundation.layout.Box(Modifier.width(420.dp).fillMaxHeight()) {
                        com.kingzcheung.xime.ui.settings.EnglishInputSettingsContent(onBack = {})
                    }
                    androidx.compose.foundation.layout.Box(Modifier.width(420.dp).fillMaxHeight()) {
                        com.kingzcheung.xime.ui.settings.LayoutDisplaySettingsContent(onBack = {}, initialPage = "candidates")
                    }
                }
            }
        }
        File(out!!).mkdirs()
        for (theme in listOf("light", "dark")) {
            rule.runOnIdle { dark.value = theme == "dark" }
            rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "settings-moved-pages-$theme.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun renderLegends() {
        val out = System.getenv("CYIME_RENDER_OUT")
        org.junit.Assume.assumeTrue(!out.isNullOrEmpty())
        val dark = mutableStateOf(false)
        rule.setContent {
            XimeTheme(darkTheme = dark.value) {
                Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AllLegends(on = false)
                    Spacer(Modifier.width(40.dp))
                    AllLegends(on = true)
                }
            }
        }
        File(out!!).mkdirs()
        for (theme in listOf("light", "dark")) {
            rule.runOnIdle { dark.value = theme == "dark" }
            rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "toggle-legends-$theme.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
