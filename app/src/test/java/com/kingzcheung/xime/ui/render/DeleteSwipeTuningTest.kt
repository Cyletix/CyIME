package com.kingzcheung.xime.ui.render

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 11-05 别人的评论：删除键要能单独反向，清空要滑的距离能微调（70%–120%），默认都不变。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class DeleteSwipeTuningTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var prefs by mutableStateOf(KeyboardInputPreferences())
    private var clear = 0
    private var undo = 0
    private var pixelsPerDp = 1f

    private fun open() {
        AppFonts.initialize(rule.activity)
        rule.setContent {
            pixelsPerDp = LocalDensity.current.density
            CompositionLocalProvider(LocalKeyboardInputPreferences provides prefs) {
                Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(76.dp, 56.dp)) {
                        // 和真键盘的删除键一样：跟「反转上下滑方向」走，默认下滑清空、上滑撤回。
                        SwipeableIconKeyButton(rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace), {},
                            Color.DarkGray, Color.White, Modifier.testTag("delete"),
                            onSwipeUp = { clear++ }, onSwipeDown = { undo++ },
                            followSymbolSwipeDirection = true, deleteKey = true)
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun swipe(dyDp: Int) {
        rule.onNodeWithTag("delete").performTouchInput {
            down(center); moveTo(center + Offset(0f, dyDp * pixelsPerDp)); up()
        }
        rule.waitForIdle()
    }

    private fun counts() = rule.runOnIdle { clear to undo }

    @Test fun defaultsKeepTheOldDirectionAndDistance() {
        open()
        swipe(70); swipe(-70)
        assertEquals("默认 80dp，70dp 不触发", 0 to 0, counts())
        swipe(90); assertEquals("默认下滑清空", 1 to 0, counts())
        swipe(-90); assertEquals("默认上滑撤回", 1 to 1, counts())
    }

    @Test fun deleteKeyReversesOnItsOwnOnTopOfTheSymbolDirection() {
        open()
        rule.runOnIdle { prefs = KeyboardInputPreferences(reverseDeleteSwipe = true) }
        swipe(-90); assertEquals("单独反向：上滑清空", 1 to 0, counts())
        rule.runOnIdle { prefs = KeyboardInputPreferences(reverseSymbolSwipe = true, reverseDeleteSwipe = true) }
        swipe(90); assertEquals("两个都开又回到下滑清空", 2 to 0, counts())
        rule.runOnIdle { prefs = KeyboardInputPreferences(reverseSymbolSwipe = true) }
        swipe(-90); assertEquals("只开反转上下滑方向：删除键跟着反过来", 3 to 0, counts())
    }

    @Test fun shorterClearDistanceTriggersEarlierAndLongerLater() {
        open()
        rule.runOnIdle { prefs = KeyboardInputPreferences(clearSwipePercent = 70f) }
        swipe(50); assertEquals("70% = 56dp，50dp 不触发", 0 to 0, counts())
        swipe(62); assertEquals(1 to 0, counts())
        rule.runOnIdle { prefs = KeyboardInputPreferences(clearSwipePercent = 120f) }
        swipe(90); assertEquals("120% = 96dp，90dp 不触发", 1 to 0, counts())
        swipe(104); assertEquals(2 to 0, counts())
    }
}
