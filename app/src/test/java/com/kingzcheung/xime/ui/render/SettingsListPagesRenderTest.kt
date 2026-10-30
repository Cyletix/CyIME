package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.CustomLayoutManager
import com.kingzcheung.xime.settings.CellDictionaryState
import com.kingzcheung.xime.settings.InputModes
import com.kingzcheung.xime.settings.InstalledCellDictionary
import com.kingzcheung.xime.settings.SchemaMeta
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.settings.CellDictionarySettingsPanel
import com.kingzcheung.xime.ui.settings.LayoutDisplaySettingsContent
import com.kingzcheung.xime.ui.settings.SchemaToggleItem
import com.kingzcheung.xime.ui.settings.SettingsGroupTitle
import com.kingzcheung.xime.ui.settings.SettingsNote
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
 * 统一样式后的三张列表页放在一起看：「导入词库」、布局管理、已安装的方案（已启用的组合一张卡片），
 * 再加「键盘布局」页看新图标。浅色、深色各一张。只在设置了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1840dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsListPagesRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderListPages() {
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
                    InstalledCellDictionary("builtin_programming", "编程常用", "编程与 AI", 1530, english = 478, sha256 = "d"),
                    InstalledCellDictionary("local_aaaaaaaaaaaaaaaaaaaaaaaa", "本地词库", "本地导入", 3200, 12, sha256 = "e"),
                ),
                selected = setOf("builtin_arknights", "local_aaaaaaaaaaaaaaaaaaaaaaaa"),
                applied = setOf("builtin_arknights"),
            ),
            busy = false,
            message = "已导入「本地词库」3200 个词，跳过 12 行（单字或没有拼音），点「应用更改」后生效",
        )
        val schemas = listOf(SchemaMeta("rime_ice", "中文26键"), SchemaMeta("t9_pinyin", "中文九键", "3.0.0", "Dvel"),
            SchemaMeta(InputModes.ENGLISH, "英文"))
        rule.setContent {
            XimeTheme(darkTheme = dark.value) {
                Row {
                    Box(Modifier.width(420.dp).fillMaxHeight()) {
                        CellDictionarySettingsPanel(cells, {}, {}, { _, _ -> }, {}, {}, {}, onRemove = {})
                    }
                    Spacer(Modifier.width(20.dp))
                    Box(Modifier.width(420.dp).fillMaxHeight()) {
                        CustomLayoutManager(SettingsPreferences.getKeyboardTheme(context), dark.value) {}
                    }
                    Spacer(Modifier.width(20.dp))
                    Surface(Modifier.width(420.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            SettingsGroupTitle("已启用的输入组合", Modifier.padding(top = 8.dp))
                            schemas.forEachIndexed { index, schema ->
                                val english = schema.schemaId == InputModes.ENGLISH
                                SchemaToggleItem(schema, enabled = true, isCompiled = true, isCurrent = index == 0,
                                    onToggle = {}, onSelect = {}, isBuiltIn = english,
                                    groupIndex = index, groupSize = schemas.size)
                            }
                            SettingsNote("每项是语言、输入方案与布局的一个组合。长按可排序，开关控制资源准备。")
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Box(Modifier.width(420.dp).fillMaxHeight()) { LayoutDisplaySettingsContent(onBack = {}) }
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
            File(out, "settings-list-pages-$theme.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
