package com.kingzcheung.xime.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
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
 @Test fun numberedCandidatesRespectInheritedSpacingWithoutClippingLastWord() {
  var width by mutableStateOf(220.dp)
  var scale by mutableStateOf(1f)
  var density by mutableStateOf(1f)
  val words=listOf("测试","侧视","侧室","策士","测","侧","册","策","厕")
  rule.setContent { MaterialTheme {
   CompositionLocalProvider(LocalDensity provides Density(density,scale),
    LocalTextStyle provides LocalTextStyle.current.copy(letterSpacing=2.sp)) {
    FixedCandidateStrip(words,comments=List(words.size){"ce"},
     visuals=CandidateBarVisuals(Color.Black,Color.White,Color.Gray),
     callbacks=CandidateBarCallbacks(onCandidateSelect={}), fontSize=19.sp,
     showNumberLabels=true,itemSpacing=8.dp,modifier=Modifier.width(width))
   }
  } }
  for ((nextWidth,nextScale,nextDensity) in listOf(Triple(220.dp,1f,1f),Triple(340.dp,1f,1f),Triple(220.dp,1.3f,1.4f))) {
   rule.runOnIdle { width=nextWidth;scale=nextScale;density=nextDensity }
   val nodes=rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),true)
   assertTrue(nodes.fetchSemanticsNodes().isNotEmpty())
   repeat(nodes.fetchSemanticsNodes().size) { index ->
    val layouts=mutableListOf<TextLayoutResult>()
    nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    layouts.forEach { layout ->
     val detail="${layout.layoutInput.text}, size=${layout.size}, intrinsic=${layout.multiParagraph.maxIntrinsicWidth}, paragraph=${layout.multiParagraph.width}, lineRight=${layout.getLineRight(0)}, density=$nextDensity, scale=$nextScale"
     assertFalse("A visible word must not be ellipsized: $detail",layout.isLineEllipsized(0))
     assertEquals("All characters must remain visible: $detail",layout.layoutInput.text.length,layout.getLineEnd(0))
     assertTrue("Actual text must fit its bounds: $detail",layout.getLineRight(0)<=layout.size.width+0.5f)
    }
   }
  }
 }
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
