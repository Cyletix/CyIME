package com.kingzcheung.xime.ui

import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.settings.IconSettingsContent
import com.kingzcheung.xime.ui.theme.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IconAppearanceTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun preserve(block: () -> Unit) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val saved = prefs.all.filterKeys { it == SettingsPreferences.KEY_VISUAL_STYLE || it == IconAppearance.KEY_STYLE || it == IconAppearance.KEY_LINKED || it == IconAppearance.KEY_FRAMED }
        try { block() } finally {
            rule.runOnIdle {
                val edit = prefs.edit().remove(SettingsPreferences.KEY_VISUAL_STYLE).remove(IconAppearance.KEY_STYLE).remove(IconAppearance.KEY_LINKED).remove(IconAppearance.KEY_FRAMED)
                saved.forEach { (k,v) -> when(v) { is String -> edit.putString(k,v); is Boolean -> edit.putBoolean(k,v) } }
                edit.commit(); KeyboardThemes.reload(context); LauncherIcons.apply(context, IconAppearance.effective)
            }
        }
    }

    @Test fun bindingWorksBothWaysAndUnlinkedChoicesSurviveReload() = preserve {
        rule.runOnIdle {
            IconAppearance.setLinked(context, true)
            SettingsPreferences.setVisualStyle(context, VisualStyle.GLASS)
            assertEquals(VisualStyle.GLASS, IconAppearance.effective)
            IconAppearance.setIconStyle(context, VisualStyle.NEON)
            assertEquals(VisualStyle.NEON, SettingsPreferences.getVisualStyle(context))
            IconAppearance.setLinked(context, false)
            IconAppearance.setFramed(context, false)
            IconAppearance.setIconStyle(context, VisualStyle.FROST)
            SettingsPreferences.setVisualStyle(context, VisualStyle.FACET)
            KeyboardThemes.reload(context)
            assertEquals(VisualStyle.FROST, IconAppearance.effective)
            assertFalse(IconAppearance.framed)
            assertEquals(VisualStyle.FACET, VisualStyles.current)
            IconAppearance.setLinked(context, true)
            assertEquals(VisualStyle.FACET, IconAppearance.effective)
        }
    }

    @Test fun allTenLauncherChoicesKeepExactlyOneEntryAndMainActivityEnabled() = preserve {
        rule.runOnIdle {
            IconAppearance.setLinked(context, false)
            for (framed in listOf(true, false)) {
            IconAppearance.setFramed(context, framed)
            VisualStyle.entries.forEach { style ->
                IconAppearance.setIconStyle(context, style)
                val entries = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
                assertEquals(1, entries.size)
                assertEquals(LauncherIcons.component(context, style).className, entries.single().activityInfo.name)
                assertEquals(launcherIconResource(style, framed), entries.single().activityInfo.icon)
                assertNotNull(entries.single().loadIcon(context.packageManager))
                assertTrue(context.packageManager.getActivityInfo(ComponentName(context.packageName, "com.kingzcheung.xime.MainActivity"), 0).enabled)
            }
            }
        }
    }

    @Test fun iconSettingsHasBackAndIndependentControls() = preserve {
        var backs = 0
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) {
            XimeTheme { Box(Modifier.size(460.dp, 820.dp).testTag("icon-settings")) {
                IconSettingsContent(onBack = { backs++ }, onChanged = {})
            } }
        } }
        rule.runOnIdle { IconAppearance.setLinked(context, true) }
        rule.onNodeWithTag("icon-frame-false").performClick().assertIsSelected()
        rule.onNodeWithTag("icon-frame-true").performClick().assertIsSelected()
        rule.onNodeWithTag("icon-link").performScrollTo().performClick()
        rule.onNodeWithTag("icon-style-glass").performScrollTo().performClick().assertIsSelected()
        rule.assertGeometry("icon-settings", "reference icon settings")
        File(context.getExternalFilesDir(null), "icon-settings.png").outputStream().use {
            rule.onNodeWithTag("icon-settings").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
        rule.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backs)
    }

    @Test fun exportLauncherAssetsFromProductionGenerator() {
        VisualStyle.entries.filter { it != VisualStyle.ORIGINAL }.forEach { style ->
            for (foreground in listOf(false, true)) {
                val name = "cyime_${style.id}" + if (foreground) "_foreground" else ""
                val bitmap = CyimeIconGenerator.render(context, style, 432, foreground)
                // Catch reintroduced baked-in tiles/frames, including the old inset squircle.
                val edge = (bitmap.width * .18f).toInt()
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    if (x < edge || y < edge || x >= bitmap.width - edge || y >= bitmap.height - edge) {
                        assertEquals("${style.id}: exterior must be transparent at $x,$y", 0, android.graphics.Color.alpha(bitmap.getPixel(x, y)))
                    }
                }
                assertTrue("glyph must contain visible artwork", (edge until bitmap.width - edge).any { x ->
                    (edge until bitmap.height - edge).any { y -> android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0 }
                })
                File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test fun launcherResourcesFillTheirCanvasWithoutShrinkingTheKeyboardMark() {
        VisualStyle.entries.forEach { style ->
            for (framed in listOf(true, false)) {
                val bitmap = renderLauncherIcon(context, style, framed)
                val points = (0 until bitmap.width).filter { x ->
                    (0 until bitmap.height).any { y -> android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 32 }
                }
                val width = points.last() - points.first() + 1
                assertTrue("$style / $framed must fill the launcher slot", width >= bitmap.width * .78f)
                if (framed) {
                    assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)))
                } else {
                    assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                }
                bitmap.recycle()
            }
        }
    }
}
