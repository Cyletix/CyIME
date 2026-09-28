package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardPanelShapeTest {
    @Test fun fixedBottomIsSquareAndFloatingKeepsAllCornersAcrossPanelSizes() {
        for (size in listOf(Size(320f, 228f), Size(1000f, 500f))) {
            for (direction in LayoutDirection.entries) {
                for (floating in listOf(false, true)) {
                    val outline = keyboardPanelShape(floating).createOutline(size, direction, Density(1f)) as Outline.Rounded
                    val rect = outline.roundRect
                    assertEquals(16f, rect.topLeftCornerRadius.x, 0f)
                    assertEquals(16f, rect.topRightCornerRadius.x, 0f)
                    assertEquals(if (floating) 16f else 0f, rect.bottomLeftCornerRadius.x, 0f)
                    assertEquals(if (floating) 16f else 0f, rect.bottomRightCornerRadius.x, 0f)
                    assertEquals(size.width, rect.width, 0f)
                    assertEquals(size.height, rect.height, 0f)
                }
            }
        }
    }
    @Test fun roundedSettingRestoresFixedBottomCornersWithoutChangingSize() {
        val size = Size(360f, 228f)
        for (floating in listOf(false, true)) {
            val rect = (keyboardPanelShape(floating, true)
                .createOutline(size, LayoutDirection.Ltr, Density(1f)) as Outline.Rounded).roundRect
            assertEquals(16f, rect.bottomLeftCornerRadius.x, 0f)
            assertEquals(16f, rect.bottomRightCornerRadius.x, 0f)
            assertEquals(size.width, rect.width, 0f)
            assertEquals(size.height, rect.height, 0f)
        }
    }

    @Test fun navigationIconsFollowKeyboardTextOrSystemAccordingToStyle() {
        for (systemDark in listOf(false, true)) {
            assertEquals(true, useDarkNavigationIcons(false, systemDark, 0.1f))
            assertEquals(false, useDarkNavigationIcons(false, systemDark, 0.9f))
            assertEquals(!systemDark, useDarkNavigationIcons(true, systemDark, 0.1f))
            assertEquals(!systemDark, useDarkNavigationIcons(true, systemDark, 0.9f))
        }
    }}