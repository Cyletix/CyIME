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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.model.BuiltinModelCatalog
import com.kingzcheung.xime.model.CandidateModelCatalog
import com.kingzcheung.xime.model.DeviceModelProfiles
import com.kingzcheung.xime.model.DeviceModelTier
import com.kingzcheung.xime.model.SetupAnswers
import com.kingzcheung.xime.model.SetupFacts
import com.kingzcheung.xime.model.SetupGuide
import com.kingzcheung.xime.model.SetupGuidePlanner
import com.kingzcheung.xime.model.VoiceUse
import com.kingzcheung.xime.speech.SpeechModelCatalog
import com.kingzcheung.xime.ui.settings.SetupGuideContent
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
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
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w880dp-h1900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetupGuideRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderSetupGuide() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val mb = 1024L * 1024
        val bundled = setOf(SpeechModelCatalog.SENSEVOICE, DeviceModelProfiles.BASE, DeviceModelProfiles.SMALL,
            BuiltinModelCatalog.SPEAKER_MODEL, "ochwpro", CandidateModelCatalog.NGRAM)
        val sizes = mapOf(SpeechModelCatalog.SENSEVOICE to 228 * mb, SpeechModelCatalog.PARAFORMER to 226 * mb,
            SpeechModelCatalog.XASR to 136 * mb, DeviceModelProfiles.BASE to 35 * mb, DeviceModelProfiles.SMALL to 19 * mb,
            BuiltinModelCatalog.SPEAKER_MODEL to 27 * mb, "ochwpro" to 7 * mb, CandidateModelCatalog.NGRAM to 203 * mb,
            CandidateModelCatalog.NGRAM_SMALL to 105 * mb)
        val facts = SetupFacts(DeviceModelTier.ENHANCED, memoryBytes = 8 * 1024 * mb, cores = 8, maxCpuKHz = 3_200_000,
            soc = "Qualcomm SM8550", freeBytes = 45 * 1024 * mb, metered = true, language = "中文",
            installed = bundled.associateWith { sizes.getValue(it) }, inPackage = bundled, sizes = sizes)
        val plan = SetupGuidePlanner.plan(facts, SetupAnswers(VoiceUse.OFTEN, t9 = false, caption = false))
        val dark = mutableStateOf(false)
        rule.setContent {
            XimeTheme(darkTheme = dark.value) {
                Row {
                    Box(Modifier.width(420.dp).fillMaxHeight()) {
                        SetupGuideContent(facts, plan, plan.remove.map { it.id }.toSet(), allowMetered = false, applying = false,
                            result = null, firstRun = true, onAnswers = {}, onToggleRemove = { _, _ -> }, onAllowMetered = {},
                            onApply = {}, onSkip = {}, onDone = {})
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
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "setup-guide-$theme.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
