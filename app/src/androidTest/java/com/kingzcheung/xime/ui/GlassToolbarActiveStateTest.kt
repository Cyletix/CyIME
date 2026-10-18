package com.kingzcheung.xime.ui

import android.app.Application
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.keyboard.MainType
import com.kingzcheung.xime.service.CandidateState
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardView
import com.kingzcheung.xime.ui.theme.PureBlackTheme
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** With glass every toolbar control sits on a keycap disc; the active tool must still stand out. */
class GlassToolbarActiveStateTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private val preferenceFiles = mutableSetOf<String>()
    private val namespace = "glass-toolbar-${System.nanoTime()}"
    private val context = object : ContextWrapper(app) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val file = "$namespace-$name"
            preferenceFiles += file
            return app.getSharedPreferences(file, mode)
        }
    }

    @After fun releaseFixture() {
        store.clear()
        preferenceFiles.forEach { app.deleteSharedPreferences(it) }
        KeysConfigHelper.setActiveKeyboardSchema(SettingsPreferences.getCurrentSchema(app))
    }

    @Test fun handwritingToolStandsOutOnBlackGlass() = checkActiveStandsOut(glass = true, "black-glass")

    @Test fun handwritingToolStandsOutOnBlackWithoutGlass() = checkActiveStandsOut(glass = false, "black-plain")

    private fun checkActiveStandsOut(glass: Boolean, name: String) {
        SettingsPreferences.setKeyboardTheme(context, PureBlackTheme.ID)
        FrostedGlassPreferences.save(context, FrostedGlassConfig.defaults(true).copy(enabled = glass), true)
        KeysConfigHelper.loadConfig(app)
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app)).get(KeyboardViewModel::class.java)
        vm.resetKeyboard(false, "rime_ice")
        val state = KeyboardUiState(currentSchemaId = "rime_ice", isDarkTheme = true, themeId = PureBlackTheme.ID,
            schemas = listOf(SchemaInfo("rime_ice", "rime_ice", "", "", "")), isHandwritingMode = true)
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = 400
                screenHeightDp = 800
            }
            CompositionLocalProvider(LocalContext provides context, LocalDensity provides Density(2f),
                LocalConfiguration provides configuration) {
                MaterialTheme {
                    // The reported case: a black host behind the black theme with glass on.
                    Box(Modifier.requiredSize(400.dp, 320.dp).background(Color.Black)) {
                        KeyboardView(vm, state, KeyboardCallbacks(onKeyPress = { _, _ -> }, onCandidateSelect = {}),
                            modifier = Modifier.requiredSize(400.dp, 320.dp).testTag("glass-toolbar-keyboard"),
                            candidateState = mutableStateOf(CandidateState()), inputSessionManagedByHost = true)
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { vm.switchMain(MainType.HANDWRITING) }
        rule.waitForIdle()
        val root = rule.onNodeWithTag("glass-toolbar-keyboard").fetchSemanticsNode().boundsInRoot
        val image = rule.onNodeWithTag("glass-toolbar-keyboard").captureToImage().asAndroidBitmap()
        java.io.File(app.getExternalFilesDir(null), "glass-toolbar-$name.png").outputStream().use {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        val slots = rule.onAllNodes(hasTestTag("toolbar-slot"), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("fixture shows the configurable toolbar", slots.size >= 3)
        val active = rule.onAllNodes(isSelected() and hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { node -> slots.any { it.boundsInRoot.contains(node.boundsInRoot.center) } }
        assertEquals("exactly one toolbar tool is marked active", 1, active.size)
        val activeSlot = slots.first { it.boundsInRoot.contains(active.single().boundsInRoot.center) }
        // Sample the pill fill between the glyph and the pill edge.
        fun fill(bounds: Rect): Int = image.getPixel(
            (bounds.center.x - root.left + 13f * 2f).toInt(), (bounds.center.y - root.top).toInt())
        val activeFill = fill(activeSlot.boundsInRoot)
        slots.filter { it !== activeSlot }.forEach { slot ->
            assertTrue("$name: active tool must differ clearly from ${slot.boundsInRoot}",
                distance(activeFill, fill(slot.boundsInRoot)) >= 60)
        }
    }

    private fun distance(a: Int, b: Int): Int = maxOf(
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
        abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
        abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)))
}
