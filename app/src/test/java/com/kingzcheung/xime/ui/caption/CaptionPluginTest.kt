package com.kingzcheung.xime.ui.caption

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.SettingsPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 字幕是插件（11-05）：没装就收起所有入口；用过字幕的老用户才自动装上；卸载时按钮一起拿掉。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class CaptionPluginTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before fun clean() {
        SettingsPreferences.getPrefsPublic(app).edit().clear().commit()
        app.getSharedPreferences("caption_mode", 0).edit().clear().commit()
    }

    private fun state(cls: Class<*>) = app.packageManager.getComponentEnabledSetting(ComponentName(app, cls))

    @Test fun `没装字幕插件：入口收起，下拉快捷开关和通话字幕停用`() {
        CaptionPlugin.sync(app)
        assertFalse(CaptionPlugin.isActive(app))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state(CaptionTileService::class.java))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state(CallCaptionService::class.java))
    }

    @Test fun `用过字幕才算：打开过字幕页或改过字幕设置、自己把字幕放上工具栏`() {
        assertFalse(CaptionPlugin.usedCaption(app))
        app.getSharedPreferences("caption_mode", 0).edit().putBoolean("auto_save", true).commit()
        assertTrue(CaptionPlugin.usedCaption(app))
        app.getSharedPreferences("caption_mode", 0).edit().clear().commit()
        SettingsPreferences.setToolbarButtons(app, listOf("voice", CaptionPlugin.BUTTON_ID))
        assertTrue(CaptionPlugin.usedCaption(app))
    }

    @Test fun `默认放进菜单的字幕不算用过`() {
        SettingsPreferences.setQuickActions(app, com.kingzcheung.xime.keyboard.QuickActionLayout(listOf("voice"),
            listOf("settings", CaptionPlugin.BUTTON_ID, "arrange")))
        assertFalse(CaptionPlugin.usedCaption(app))
    }

    @Test fun `卸载字幕插件：工具栏和菜单里的字幕按钮一起拿掉，再装上时重新放进菜单`() {
        SettingsPreferences.setQuickActions(app, com.kingzcheung.xime.keyboard.QuickActionLayout(listOf("voice", CaptionPlugin.BUTTON_ID),
            listOf("settings", "arrange")))
        SettingsPreferences.placeNewPluginButtons(app, listOf(CaptionPlugin.BUTTON_ID))
        SettingsPreferences.forgetUninstalledPlugin(app, CaptionPlugin.ID, emptyList())
        assertFalse(CaptionPlugin.BUTTON_ID in SettingsPreferences.getToolbarButtons(app))
        assertFalse(CaptionPlugin.BUTTON_ID in SettingsPreferences.getMenuButtons(app))
        assertFalse(CaptionPlugin.isActive(app))
        // 「已放过」的记录也清掉：再装上时放回菜单「排列」前。
        assertTrue(SettingsPreferences.placeNewPluginButtons(app, listOf(CaptionPlugin.BUTTON_ID)))
        val menu = SettingsPreferences.getMenuButtons(app)
        assertEquals(menu.indexOf("arrange") - 1, menu.indexOf(CaptionPlugin.BUTTON_ID))
    }

    @Test fun `别的插件卸载不动字幕按钮`() {
        SettingsPreferences.setQuickActions(app, com.kingzcheung.xime.keyboard.QuickActionLayout(listOf("voice", CaptionPlugin.BUTTON_ID),
            listOf("settings", "arrange")))
        SettingsPreferences.forgetUninstalledPlugin(app, "com.kingzcheung.xime.plugin.translate", listOf("com.kingzcheung.xime.plugin.translate:open"))
        assertTrue(CaptionPlugin.BUTTON_ID in SettingsPreferences.getToolbarButtons(app))
    }
}
