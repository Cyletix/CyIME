package com.kingzcheung.xime.ui.render

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.model.ModelStorage
import com.kingzcheung.xime.settings.LanguagePreferences
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.settings.SettingsScreen
import com.kingzcheung.xime.ui.settings.modelDeleteNote
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 11-03 截图：扩展 › 模型详情只能下载、删不掉。装好的模型在详情页也能删。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-rCN-w360dp-h680dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModelDetailDeleteTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val modelId = "words-ru"
    private val wordList get() = File(ModelStorage.getModelDir(app, modelId), "cyime-words-ru-v1.txt")

    @Before fun setup() {
        File(app.filesDir, "rime/xime.custom.yaml").apply {
            parentFile!!.mkdirs()
            writeText("xime_index:\n  base_urls: []\n")
        }
        KeyboardThemes.initFromConfig(app)
        // 直接写语言开关，不走 LanguagePreferences.save（那会顺带排下载和部署）。
        SettingsPreferences.getPrefsPublic(app).edit()
            .putStringSet(LanguagePreferences.KEY, setOf("zh", "en", "ru")).commit()
    }

    @After fun cleanUp() {
        ModelStorage.getModelDir(app, modelId).deleteRecursively()
        SettingsPreferences.getPrefsPublic(app).edit().remove(LanguagePreferences.KEY).commit()
    }

    private fun installWordList() {
        val source = listOf(File("tools/wordlists/${wordList.name}"), File("../tools/wordlists/${wordList.name}"))
            .first { it.isFile }
        wordList.parentFile!!.mkdirs()
        source.copyTo(wordList, overwrite = true)
    }

    @Test fun installedModelCanBeDeletedFromDetailPage() {
        installWordList()
        rule.setContent { XimeTheme { SettingsScreen(initialRoute = "model_market_detail/$modelId") } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("model-detail-delete").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("model-detail-delete").performScrollTo().performClick()
        rule.onNodeWithText("删除模型").assertIsDisplayed()
        // 俄语开着：打俄语时词表会自动重新下载，确认框里说清楚。
        rule.onNodeWithText("俄语还开着", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("model-delete-confirm").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("model-detail-delete").fetchSemanticsNodes().isEmpty() }
        assertFalse(wordList.exists())
        // 删掉后版本卡片回到「下载」，可以重新装。
        rule.onNodeWithText("下载").assertExists()
    }

    @Test fun cancelKeepsTheModel() {
        installWordList()
        rule.setContent { XimeTheme { SettingsScreen(initialRoute = "model_market_detail/$modelId") } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("model-detail-delete").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("model-detail-delete").performScrollTo().performClick()
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithText("删除模型").assertDoesNotExist()
        assertTrue(wordList.isFile)
        rule.onNodeWithTag("model-detail-delete").assertExists()
    }

    @Test fun notInstalledShowsNoDeleteButton() {
        rule.setContent { XimeTheme { SettingsScreen(initialRoute = "model_market_detail/$modelId") } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("下载").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("model-detail-delete").assertDoesNotExist()
    }

    @Test fun reDownloadNoteOnlyWhenTheLanguageIsOn() {
        assertEquals("俄语还开着，打俄语时会重新下载。", modelDeleteNote(app, modelId))
        SettingsPreferences.getPrefsPublic(app).edit()
            .putStringSet(LanguagePreferences.KEY, setOf("zh", "en")).commit()
        assertNull(modelDeleteNote(app, modelId))
        // 不是补全词表的模型不提示。
        assertNull(modelDeleteNote(app, "not-a-word-list"))
    }
}
