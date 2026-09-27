package com.kingzcheung.xime.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.ui.keyboard.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TabletPanelRegressionTest {
    @get:Rule val rule = createComposeRule()
    @Test fun tabletMenuUsesTwoBoundedRowsAtBothOrientations() {
        var landscape by mutableStateOf(true)
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(1f)) {
                MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                    MenuBar(MenuBarState(true, true, 1, Color(0xFF211E29), Color(0xFF33303D), Color.White),
                        MenuBarCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}),
                        Modifier.size(if (landscape) 1000.dp else 700.dp, 340.dp).testTag("tablet-menu"))
                }
            }
        }
        for (wide in listOf(true, false)) {
            rule.runOnIdle { landscape = wide }
            val cards = listOf("键盘调节", "设置", "快捷发送", "定制工具栏", "浅色模式", "部署方案")
                .map { rule.onNodeWithTag("menu-item:$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
            cards.forEach { assertTrue(it.width <= 145); assertTrue(it.height <= 105); assertTrue(it.width / it.height in 0.7f..1.5f) }
            assertTrue(cards[4].top > cards[0].bottom)
            val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "tablet-menu-$wide.png")
            file.outputStream().use { rule.onNodeWithTag("tablet-menu").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}
