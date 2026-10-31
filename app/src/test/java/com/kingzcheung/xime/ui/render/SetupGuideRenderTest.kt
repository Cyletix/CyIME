package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.model.BuiltinModelCatalog
import com.kingzcheung.xime.model.CandidateModelCatalog
import com.kingzcheung.xime.model.DeviceModelProfiles
import com.kingzcheung.xime.model.DeviceModelTier
import com.kingzcheung.xime.model.DoublePinyinChoice
import com.kingzcheung.xime.model.SetupAnswers
import com.kingzcheung.xime.model.SetupFacts
import com.kingzcheung.xime.model.SetupGuide
import com.kingzcheung.xime.model.SetupGuidePlanner
import com.kingzcheung.xime.model.VoiceUse
import com.kingzcheung.xime.speech.SpeechModelCatalog
import com.kingzcheung.xime.ui.settings.SetupGuideContent
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 「按设备推荐配置」：左为完整版新装、手机性能强、移动数据（有可删的、要下载的、用流量开关），
 * 右为点了“按推荐设置”之后。浅色、深色各一张。只在设置了 CYIME_RENDER_OUT 时出图。
 * 另有总会跑的检查：选项卡够大、选中的那张标成已选中、点另一张后选中跟着换（用户 10-30：选了哪个一目了然），
 * 出图时再存一张选中切换到一半的画面，看过渡是否放慢到看得清。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w880dp-h3200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetupGuideRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val mb = 1024L * 1024

    private fun facts(): SetupFacts {
        val bundled = setOf(SpeechModelCatalog.SENSEVOICE, DeviceModelProfiles.BASE, DeviceModelProfiles.SMALL,
            BuiltinModelCatalog.SPEAKER_MODEL, "ochwpro", CandidateModelCatalog.NGRAM)
        val sizes = mapOf(SpeechModelCatalog.SENSEVOICE to 228 * mb, SpeechModelCatalog.PARAFORMER to 226 * mb,
            SpeechModelCatalog.XASR to 136 * mb, DeviceModelProfiles.BASE to 35 * mb, DeviceModelProfiles.SMALL to 19 * mb,
            BuiltinModelCatalog.SPEAKER_MODEL to 27 * mb, "ochwpro" to 7 * mb, CandidateModelCatalog.NGRAM to 203 * mb,
            CandidateModelCatalog.NGRAM_SMALL to 105 * mb)
        return SetupFacts(DeviceModelTier.ENHANCED, performanceTier = com.kingzcheung.xime.runtime.adaptation.PerformanceTier.HIGH,
            memoryBytes = 8 * 1024 * mb, cores = 8, maxCpuKHz = 3_200_000,
            soc = "Qualcomm SM8550", freeBytes = 45 * 1024 * mb, metered = true, language = "中文",
            installed = bundled.associateWith { sizes.getValue(it) }, inPackage = bundled, sizes = sizes,
            doublePinyinChoices = doublePinyins, shownDoublePinyin = doublePinyins.map { it.id }.toSet())
    }

    private val doublePinyins = listOf("double_pinyin_flypy" to "小鹤双拼" to "ul'pb", "double_pinyin_shoudao" to "首道双拼" to "eh'pc",
        "double_pinyin_ziranma" to "自然码" to "ud'pn", "double_pinyin_znabc" to "智能ABC双拼" to "vt'pm",
        "double_pinyin_jiajia" to "拼音加加" to "ih'pl", "double_pinyin_microsoft" to "微软双拼" to "ud'pn")
        .map { (idName, example) -> DoublePinyinChoice(idName.first, idName.second, example) }

    private fun shot(out: String, name: String) {
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        File(out, "setup-guide-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun chosenOptionIsObvious() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val facts = facts()
        rule.setContent {
            var answers by androidx.compose.runtime.remember {
                mutableStateOf(SetupAnswers(VoiceUse.OFTEN, t9 = false, caption = false))
            }
            val plan = SetupGuidePlanner.plan(facts, answers)
            XimeTheme(darkTheme = false) {
                Box(Modifier.width(400.dp).fillMaxHeight()) {
                    SetupGuideContent(facts, plan, plan.remove.map { it.id }.toSet(), allowMetered = false, applying = false,
                        result = null, firstRun = true, onAnswers = { answers = it }, onToggleRemove = { _, _ -> },
                        onAllowMetered = {}, onApply = {}, onSkip = {}, onDone = {})
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("setup-voice:OFTEN").assertIsSelected()
        rule.onNodeWithTag("setup-voice:SOMETIMES").assertIsNotSelected()
        rule.onNodeWithTag("setup-t9:false").assertIsSelected()
        rule.onNodeWithTag("setup-t9:true").assertIsNotSelected()
        for (tag in listOf("setup-voice:OFTEN", "setup-voice:NEVER", "setup-t9:true", "setup-caption:false")) {
            val bounds = rule.onNodeWithTag(tag).getBoundsInRoot()
            assertTrue("$tag 只有 ${bounds.bottom - bounds.top} 高", bounds.bottom - bounds.top >= 76.dp)
            assertTrue("$tag 不是整行宽", bounds.right - bounds.left >= 360.dp)
        }
        // 像用户一样点（时钟照常走）：点完另一张就成了已选中，原来那张取消。
        rule.onNodeWithTag("setup-voice:SOMETIMES").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-voice:SOMETIMES").assertIsSelected()
        rule.onNodeWithTag("setup-voice:OFTEN").assertIsNotSelected()
        // 打字方式：新装是全拼；分不清的读音可以多选，“都分得清”和它们互斥。
        rule.onNodeWithTag("setup-typing:FULL_PINYIN").assertIsSelected()
        rule.onNodeWithTag("setup-fuzzy:none").assertIsSelected()
        rule.onNodeWithTag("setup-fuzzy:NL").performScrollTo().performClick()
        rule.onNodeWithTag("setup-fuzzy:NASAL").performScrollTo().performClick()
        rule.waitForIdle()
        // 多选卡按复选框报“已勾选”。
        rule.onNodeWithTag("setup-fuzzy:NL").assertIsOn()
        rule.onNodeWithTag("setup-fuzzy:NASAL").assertIsOn()
        rule.onNodeWithTag("setup-fuzzy:FH").assertIsOff()
        rule.onNodeWithTag("setup-fuzzy:none").assertIsNotSelected()
        rule.onNodeWithTag("setup-hide-double-pinyin").assertExists()
        val out = System.getenv("CYIME_RENDER_OUT")
        if (!out.isNullOrEmpty()) { File(out).mkdirs(); shot(out, "full-pinyin-fuzzy") }
        // 选双拼：出“用哪一种”，模糊音那题收起。
        rule.onNodeWithTag("setup-typing:DOUBLE_PINYIN").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-double-pinyin:double_pinyin_flypy").assertIsSelected()
        rule.onNodeWithTag("setup-fuzzy:NL").assertDoesNotExist()
        rule.onNodeWithTag("setup-double-pinyin:double_pinyin_ziranma").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-double-pinyin:double_pinyin_ziranma").assertIsSelected()
        if (!out.isNullOrEmpty()) shot(out, "double-pinyin")
        if (out.isNullOrEmpty()) return
        File(out).mkdirs()
        // 出图：停住时钟再点回第一张，存一张颜色、打勾按 CyMotion.GUIDE 变到一半的，再存一张变完的。
        // 不用触摸注入：时钟停住时滚动和触摸都等不到下一帧。
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("setup-voice:OFTEN").performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(com.kingzcheung.xime.ui.theme.CyMotion.GUIDE / 2L)
        shot(out, "switching-halfway")
        rule.mainClock.advanceTimeBy(com.kingzcheung.xime.ui.theme.CyMotion.GUIDE.toLong() + 200)
        shot(out, "switched")
        rule.mainClock.autoAdvance = true
    }

    @Test fun renderSetupGuide() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val facts = facts()
        val plan = SetupGuidePlanner.plan(facts, SetupAnswers(VoiceUse.OFTEN, t9 = false, caption = false))
        val dark = mutableStateOf(false)
        rule.setContent {
            XimeTheme(darkTheme = dark.value) {
                Row {
                    Box(Modifier.width(420.dp).fillMaxHeight()) {
                        SetupGuideContent(facts, plan, plan.remove.map { it.id }.toSet(), allowMetered = false, applying = false,
                            result = null, firstRun = true, onAnswers = {}, onToggleRemove = { _, _ -> }, onAllowMetered = {},
                            onApply = {}, onSkip = {}, onDone = {}, onOpenPerformance = {})
                    }
                    Spacer(Modifier.width(40.dp))
                    Box(Modifier.width(420.dp).fillMaxHeight()) {
                        SetupGuideContent(facts, plan, emptySet(), allowMetered = false, applying = false,
                            result = SetupGuide.Result(3, 249 * mb, 362 * mb, waitingForWifi = true, failed = emptyList()),
                            firstRun = true, onAnswers = {}, onToggleRemove = { _, _ -> }, onAllowMetered = {},
                            onApply = {}, onSkip = {}, onDone = {})
                    }
                }
            }
        }
        File(out!!).mkdirs()
        for (theme in listOf("light", "dark")) {
            rule.runOnIdle { dark.value = theme == "dark" }
            rule.waitForIdle()
            shot(out, theme)
        }
    }
}
