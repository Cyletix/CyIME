package com.kingzcheung.xime.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.association.*
import com.kingzcheung.xime.ui.settings.LearningDataControls
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LearningDataTest {
    @get:Rule val rule = createComposeRule()

    @Test fun controlsRemainReachableAtNarrowLargeFontAndTabletSizes() {
        val size = mutableStateOf(Triple(280, 360, 2f))
        val clicked = mutableListOf<String>()
        rule.setContent {
            val (w, h, font) = size.value
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                MaterialTheme {
                    Column(Modifier.requiredSize(w.dp,h.dp).testTag("learning-host").verticalScroll(rememberScrollState())) {
                        LearningDataControls(190,255,50000,false,"已保存到本机",
                            {clicked += "save"},{clicked += "refresh"},{clicked += "export"},{clicked += "import"})
                    }
                }
            }
        }
        for (viewport in listOf(Triple(280,360,2f), Triple(360,600,1.3f), Triple(800,400,1f))) {
            rule.runOnIdle { size.value = viewport }
            for ((label, action) in listOf("立即保存到本机" to "save", "刷新统计" to "refresh", "导出文件" to "export", "导入学习文件" to "import")) {
                rule.onNodeWithText(label).performScrollTo().performClick()
                rule.runOnIdle { assertEquals(action, clicked.last()) }
                rule.assertGeometry("learning-host", "学习数据 $viewport $label")
            }
        }
    }

    @Test fun importExportAtomicPersistenceAndCorpusIsolation() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(base.cacheDir, "learning-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getFilesDir() = directory }
        val cache = UserNgramCache(context)
        cache.initialize()
        cache.recordInput("你好")
        cache.save()
        val before = cache.snapshot()
        val data = before.copy(profileName="测试", continuations=listOf(PersonalContinuation("我想","看看",3)))
        cache.importData(data)
        assertEquals(before, PersonalLearningData.decode(File(directory,"user_learning_before_import.json").readText()))
        val persisted = File(directory,"user_ngram_cache.json").readText()
        assertTrue(persisted.contains("profileReference"))
        assertFalse(persisted.contains("看看"))
        val reopened = UserNgramCache(context)
        reopened.initialize()
        assertEquals(data, reopened.snapshot())
        assertEquals("看看", reopened.profileCandidates("我想").single().text)
        // Touching observations must preserve the separately stored default profile.
        reopened.recordInput("你们")
        reopened.save()
        val final = UserNgramCache(context)
        final.initialize()
        assertEquals(2L, final.snapshot().observations)
        assertEquals(data.continuations, final.snapshot().continuations)
        // A failed read must never become a successful overwrite with empty data.
        val brokenDirectory = File(directory, "broken").apply { mkdirs() }
        val brokenFile = File(brokenDirectory, "user_ngram_cache.json").apply { writeText("{broken") }
        val broken = UserNgramCache(object : ContextWrapper(base) { override fun getFilesDir() = brokenDirectory })
        assertTrue(runCatching { broken.initialize() }.isFailure)
        assertTrue(runCatching { broken.save() }.isFailure)
        assertEquals("{broken", brokenFile.readText())
    }
}
