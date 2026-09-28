package com.kingzcheung.xime.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.ui.keyboard.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class FixedCandidateStripTest {
 @get:Rule val rule=createComposeRule()
 @Test fun mixedScriptsSameHeightAndOverflowIsNotRendered() {
  var visible: List<String> = emptyList()
  var selected = -1
  val words=listOf("PS","输入","很长的测试候选词","QQ","下一个")
  rule.setContent { MaterialTheme {
   FixedCandidateStrip(words, comments=emptyList(), visuals=CandidateBarVisuals(Color.Black,Color.White,Color.Gray),
    callbacks=CandidateBarCallbacks(onCandidateSelect={selected=it},onVisibleCandidatesChanged={visible=it}),
    fontSize=19.sp, modifier=Modifier.width(180.dp).background(Color.Black))
  } }
  rule.waitForIdle()
  assertTrue(visible.size >= 2 && visible.size < words.size)
  assertEquals(words.take(visible.size), visible)
  val a=rule.onNodeWithTag("bar-candidate:0").fetchSemanticsNode().boundsInRoot
  val b=rule.onNodeWithTag("bar-candidate:1").fetchSemanticsNode().boundsInRoot
  assertEquals(a.top,b.top,0.5f);assertEquals(a.height,b.height,0.5f)
  rule.onNodeWithText("下一个").assertDoesNotExist()
  val c=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
  java.io.File(c.cacheDir,"candidate-strip-fixture.png").outputStream().use { output ->
   rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,output)
  }
  rule.onNodeWithText("输入").performClick();rule.runOnIdle { assertEquals(1,selected) }
 }
 @Test fun resizingUpdatesHeaderPrefix() {
  var width by mutableStateOf(130.dp)
  var visible:List<String> = emptyList()
  val words=listOf("你好","世界","输入","中文","布局","候选")
  rule.setContent { MaterialTheme { FixedCandidateStrip(words,comments=emptyList(),
   visuals=CandidateBarVisuals(Color.Black,Color.White,Color.Gray),
   callbacks=CandidateBarCallbacks(onCandidateSelect={},onVisibleCandidatesChanged={visible=it}),
   fontSize=19.sp,modifier=Modifier.width(width)) } }
  rule.waitForIdle();val small=visible.size
  rule.runOnIdle { width=300.dp };rule.waitForIdle()
  assertTrue(visible.size>small);assertEquals(words.take(visible.size),visible)
 }
}
