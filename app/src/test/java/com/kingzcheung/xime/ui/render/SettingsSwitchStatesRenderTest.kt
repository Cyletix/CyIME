package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Abc
import androidx.compose.material.icons.twotone.Gesture
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.CustomKeyboardLayouts
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.settings.InputModes
import com.kingzcheung.xime.settings.LetterLayouts
import com.kingzcheung.xime.settings.SchemaMeta
import com.kingzcheung.xime.ui.settings.LanguageSettingsContent
import com.kingzcheung.xime.ui.settings.SchemaToggleItem
import com.kingzcheung.xime.ui.settings.SettingsSection
import com.kingzcheung.xime.ui.settings.SettingsToggleItem
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
 * 开着但拨不动的开关要看得出是开着的（「语言与方案」里英文始终开启，深色下曾经像关着）。
 * 左边是真实的「语言与方案」页，右边四种状态并排：开、关、锁住开、锁住关，再加已安装方案里的内置英文。
 * 浅色、深色、玻璃各一张。只在设置了 CYIME_RENDER_OUT 时出图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w880dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsSwitchStatesRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun renderSwitchStates() {
        val out = System.getenv("CYIME_RENDER_OUT")
        assumeTrue(!out.isNullOrEmpty())
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardThemes.initFromConfig(context)
        // 语言页在后台线程读 LetterLayouts / CustomKeyboardLayouts；它们的状态要先在全局快照里建好，
        // 否则第一次读发生在组合快照里，报「state created after the snapshot was taken」。
        CustomKeyboardLayouts.load(context)
        LetterLayouts.load(context)
        Snapshot.sendApplyNotifications()
        val variants = listOf("light" to (false to false), "dark" to (true to false), "glass" to (true to true))
        val shown = mutableIntStateOf(0)
        val lock = "已被「角标输入方式：长按」锁定"
        rule.setContent {
            val (_, mode) = variants[shown.intValue]
            XimeTheme(darkTheme = mode.first) {
                Row {
                    Box(Modifier.width(420.dp).fillMaxHeight()) { LanguageSettingsContent(onBack = {}) }
                    Spacer(Modifier.width(40.dp))
                    Surface(Modifier.width(420.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(16.dp)) {
                            SettingsSection(title = "开关状态") {
                                SettingsToggleItem(Icons.TwoTone.Abc, "开", "可以拨", checked = true, onCheckedChange = {})
                                SettingsToggleItem(Icons.TwoTone.Abc, "关", "可以拨", checked = false, onCheckedChange = {})
                                SettingsToggleItem(Icons.TwoTone.Gesture, "锁住开", "拨不动，要看得出开着",
                                    checked = true, onCheckedChange = {}, lockMessage = lock)
                                SettingsToggleItem(Icons.TwoTone.Gesture, "锁住关", "拨不动，看得出关着",
                                    checked = false, onCheckedChange = {}, lockMessage = lock)
                            }
                            Spacer(Modifier.height(16.dp))
                            SchemaToggleItem(SchemaMeta(InputModes.ENGLISH, "英文"), enabled = true, isCompiled = true,
                                isCurrent = false, onToggle = {}, onSelect = {}, isBuiltIn = true)
                        }
                    }
                }
            }
        }
        File(out!!).mkdirs()
        variants.forEachIndexed { index, (name, mode) ->
            rule.runOnIdle {
                FrostedGlassPreferences.saveProfiles(context, FrostedGlassPreferences.readProfiles(context).withEnabled(mode.second))
                shown.intValue = index
            }
            rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
            File(out, "settings-switch-states-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
