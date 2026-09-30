package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Vibration
import androidx.compose.material.icons.twotone.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** The browser consumes real Android colors and icons, never substitute glyphs or a second palette. */
class VisualLabResourcesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun exportActualThemePalettesAndOriginalIcons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        KeysConfigHelper.loadConfig(context)
        val icons = ToolbarButton.DEFAULT_VISIBLE.associate { it.id to it.icon } + linkedMapOf(
            "hide" to Icons.Default.KeyboardArrowDown, "delete" to Icons.AutoMirrored.Filled.Backspace,
            "reset" to Icons.Default.Refresh, "language" to Icons.Default.Language,
            "enter" to Icons.AutoMirrored.Filled.KeyboardReturn,
            "palette" to Icons.TwoTone.Palette, "effects" to Icons.TwoTone.Vibration,
            "layout" to Icons.TwoTone.TableChart, "next" to Icons.AutoMirrored.Filled.KeyboardArrowRight,
        )
        val theme = mutableStateOf(KeyboardThemes.themes.first().id)
        val dark = mutableStateOf(false)
        val modes = mutableMapOf<String, JSONObject>()
        val renderedIcons = mutableMapOf<String, Bitmap>()
        fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
        rule.setContent {
            XimeTheme(darkTheme = dark.value, themeId = theme.value) {
                val p = resolveKeyboardPalette(theme.value, dark.value)
                val m = MaterialTheme.colorScheme
                SideEffect {
                    val keyboard = JSONObject().put("bg", hex(p.background)).put("key", hex(p.key))
                        .put("fn", hex(p.function)).put("enter", hex(p.enter))
                        .put("ink", hex(p.text)).put("fnInk", hex(p.functionText))
                        .put("accent", hex(p.accent)).put("toolbarInk", hex(p.candidateText))
                        .put("dark", p.background.luminance() < .5f)
                    val settings = JSONObject().put("bg", hex(m.background)).put("card", hex(m.surfaceContainerLow))
                        .put("key", hex(m.surfaceVariant)).put("ink", hex(m.onSurface))
                        .put("muted", hex(m.onSurfaceVariant)).put("accent", hex(m.primary))
                        .put("dark", m.surface.luminance() < .5f)
                    modes["${theme.value}:${dark.value}"] = JSONObject().put("keyboard", keyboard).put("settings", settings)
                }
                icons.forEach { (id, vector) ->
                    val painter = rememberVectorPainter(vector)
                    SideEffect {
                        if (id !in renderedIcons) {
                            val bitmap = ImageBitmap(72, 72)
                            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(72f, 72f)) {
                                with(painter) { draw(Size(72f, 72f), colorFilter = ColorFilter.tint(Color.White)) }
                            }
                            renderedIcons[id] = bitmap.asAndroidBitmap()
                        }
                    }
                }
            }
        }
        val exportedIcons = JSONObject()
        rule.waitForIdle()
        icons.keys.forEach { id ->
            val bitmap = renderedIcons.getValue(id)
            assertEquals("Icon must retain transparency", 0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("Icon must contain its original geometry: $id", pixels.any { android.graphics.Color.alpha(it) > 0 })
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            exportedIcons.put(id, "data:image/png;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP))
        }
        // Export complete flat palettes; photo/gradient themes require their full background renderer.
        val themes = JSONArray()
        KeyboardThemes.themes.filter { it.keyboardBackground?.type.let { type -> type == null || type == "solid" } }.forEach { entry ->
            val item = JSONObject().put("id", entry.id).put("name", entry.name)
            for (isDark in listOf(false, true)) {
                rule.runOnIdle { theme.value = entry.id; dark.value = isDark }
                rule.waitForIdle()
                item.put(if (isDark) "dark" else "light", modes.getValue("${entry.id}:$isDark"))
            }
            themes.put(item)
        }
        assertTrue("Built-in themes must be exported", themes.length() >= 3)
        File(context.getExternalFilesDir(null), "visual-lab-resources.json").writeText(
            JSONObject().put("source", "Android · ${com.kingzcheung.xime.BuildConfig.VERSION_NAME}").put("selectedTheme", SettingsPreferences.getKeyboardTheme(context))
                .put("themes", themes).put("icons", exportedIcons).toString(2))
    }
}
