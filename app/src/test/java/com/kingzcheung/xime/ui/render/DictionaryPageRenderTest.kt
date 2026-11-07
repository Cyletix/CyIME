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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.CellDictionaryState
import com.kingzcheung.xime.settings.InstalledCellDictionary
import com.kingzcheung.xime.ui.settings.CellDictionarySettingsPanel
import com.kingzcheung.xime.ui.settings.DictionarySettingsContent
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.viewmodel.CellDictionaryUiState
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 重构后的词库页：左为整页（我的短语 / 查词标签），右为「导入的词库」开了一个 12 万条的词库时底部的代价和删除按钮。
 * 浅色、深色各一张。只在设置了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w880dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DictionaryPageRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun installedDictionaryCanBeDeletedAfterConfirmation() {
        val id = "builtin_arknights"
        val item = InstalledCellDictionary(id, "明日方舟", "电子游戏", 10, sha256 = "test")
        val state = mutableStateOf(CellDictionaryState(installed = listOf(item), selected = setOf(id), applied = setOf(id)))
        val removed = mutableListOf<String>()
        rule.setContent {
            CellDictionarySettingsPanel(CellDictionaryUiState(dictionaries = state.value, busy = false), {}, {}, { _, _ -> }, {},
                {}, {}, onRemove = { removed.add(it) }, embedded = true)
        }
        rule.onNode(hasSetTextAction()).performTextInput(item.name)
        rule.onNodeWithContentDescription("${item.name}的更多操作").assertIsEnabled().performClick()
        rule.onNodeWithText("删除词库").performClick()
        rule.onNodeWithText("删除词库？").assertExists()
        rule.onNodeWithText("取消").performClick()
        rule.runOnIdle { org.junit.Assert.assertTrue(removed.isEmpty()) }
        rule.onNodeWithContentDescription("${item.name}的更多操作").performClick()
        rule.onNodeWithText("删除词库").performClick()
        rule.onNodeWithText("删除", useUnmergedTree = true).performClick()
        rule.runOnIdle { org.junit.Assert.assertEquals(listOf(id), removed) }
    }

    @Test fun removedBuiltinOffersRestore() {
        val id = "builtin_arknights"
        val restored = mutableListOf<String>()
        rule.setContent {
            CellDictionarySettingsPanel(
                CellDictionaryUiState(dictionaries = CellDictionaryState(removedBuiltins = setOf(id)), busy = false),
                {}, {}, { _, _ -> }, {}, {}, {}, onRestoreBuiltin = { restored.add(it) }, embedded = true)
        }
        rule.onNode(hasSetTextAction()).performTextInput("明日方舟")
        rule.onNodeWithText("已删除，可恢复").assertExists()
        rule.onNodeWithText("恢复").performClick()
        rule.runOnIdle { org.junit.Assert.assertEquals(listOf(id), restored) }
    }

    @Test fun renderDictionaryPage() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        val dark = mutableStateOf(false)
        val cells = CellDictionaryUiState(
            dictionaries = CellDictionaryState(
                installed = listOf(
                    InstalledCellDictionary("builtin_arknights", "明日方舟", "电子游戏", 5645, sha256 = "a"),
                    InstalledCellDictionary("builtin_dota2", "Dota2", "电子游戏", 1599, english = 634, sha256 = "b"),
                    InstalledCellDictionary("builtin_genshin", "原神", "电子游戏", 507, english = 123, sha256 = "c"),
                    InstalledCellDictionary("builtin_programming", "编程常用", "编程与 AI", 1530, english = 478, sha256 = "d"),
                    InstalledCellDictionary("local_aaaaaaaaaaaaaaaaaaaaaaaa", "我的行业词库", "本地导入", 120_000, 35, sha256 = "e"),
                ),
                selected = setOf("builtin_arknights", "builtin_dota2", "local_aaaaaaaaaaaaaaaaaaaaaaaa"),
                applied = setOf("builtin_arknights"),
            ),
            busy = false,
        )
        rule.setContent {
            XimeTheme(darkTheme = dark.value) {
                Row {
                    Box(Modifier.width(420.dp).fillMaxHeight()) { DictionarySettingsContent(onBack = {}) }
                    Spacer(Modifier.width(40.dp))
                    Box(Modifier.width(420.dp).fillMaxHeight()) {
                        CellDictionarySettingsPanel(cells, {}, {}, { _, _ -> }, {}, {}, {}, onRemove = {}, embedded = true)
                    }
                }
            }
        }
        File(out!!).mkdirs()
        fun shot(name: String) {
            rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "dictionary-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        for (theme in listOf("light", "dark")) {
            rule.runOnIdle { dark.value = theme == "dark" }
            rule.onAllNodesWithText("我的短语")[0].performClick()
            shot("phrases-$theme")
            rule.onAllNodesWithText("查词")[0].performClick()
            shot("lookup-$theme")
            rule.onAllNodesWithText("删掉的候选")[0].performClick()
            shot("removed-$theme")
        }
    }
}
