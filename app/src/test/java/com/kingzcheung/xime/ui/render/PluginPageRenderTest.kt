package com.kingzcheung.xime.ui.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.plugin.core.runtime.PluginManager
import com.kingzcheung.xime.settings.XimeIndexSource
import com.kingzcheung.xime.ui.settings.PluginSettingsContent
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 11-03「这几个插件再也整不好了，而且没有界面，界面跳转极其混乱」：一个插件一页。
 * 页里有说明和版本、接口和「测试接口」、结果写在按钮下面、联网、卸载；没有「保存」。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-rCN-w360dp-h680dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PluginPageRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun setup() {
        File(app.filesDir, "rime/xime.custom.yaml").apply {
            parentFile!!.mkdirs()
            writeText("xime_index:\n  base_urls: []\n")
        }
        KeyboardThemes.initFromConfig(app)
        PluginManager.initialize(app)
        runBlocking {
            for (plugin in XimeIndexSource.bundledPlugins(app).plugins) {
                PluginManager.installerManager.uninstallPlugin(plugin.id)
                XimeIndexSource.installMarketPlugin(app, plugin.id, plugin.resolvedVersion()!!)
            }
        }
    }

    @After fun cleanup() = runBlocking {
        for (plugin in XimeIndexSource.bundledPlugins(app).plugins) PluginManager.installerManager.uninstallPlugin(plugin.id)
    }

    private fun shot(name: String) = System.getenv("CYIME_RENDER_OUT")?.let { dir ->
        val view = rule.activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { view.draw(Canvas(image)) }
        File(dir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    @Test fun aiReplyPageHasTestNetworkAndUninstallButNoSave() {
        val plugin = XimeIndexSource.bundledPlugins(app).plugins.single { it.name == "AI 回复" }
        rule.setContent { XimeTheme { PluginSettingsContent(pluginId = plugin.id, onBack = {}) } }
        rule.onNodeWithText("版本", substring = true).assertIsDisplayed()
        rule.onNodeWithText("API 密钥").assertIsDisplayed()
        shot("plugin-page-top.png")

        val list = rule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("测试接口"))
        rule.onNodeWithText("测试接口").performClick()
        // 没填密钥：结果直接写在按钮下面，不跳页、不弹「成功」
        rule.waitUntil(10_000) { rule.onAllNodesWithText("结果：还没填 API 密钥").fetchSemanticsNodes().isNotEmpty() }
        shot("plugin-page-test-result.png")

        list.performScrollToNode(hasText("卸载"))
        rule.onNodeWithText("联网").assertExists()
        rule.onNodeWithText("只连你在上面填的接口地址").assertExists()
        rule.onNodeWithText("卸载").assertIsDisplayed()
        rule.onAllNodesWithText("保存").assertCountEquals(0)
        shot("plugin-page-bottom.png")
    }
}
