package com.kingzcheung.xime.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.handwriting.handwritingInk
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class HandwritingInkAppearanceTest {
    @get:Rule val rule = createComposeRule()
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)

    @Test fun themedInkContrastAndRealPointerWritingAcrossBackgrounds() {
        val index = mutableIntStateOf(0)
        val cases = listOf(Triple("soft_blue", false, false), Triple("soft_blue", true, false),
            Triple("lavender_purple", false, false), Triple("lavender_purple", true, false),
            Triple("lavender_purple", true, true))
        val samples = cases + cases.takeLast(3)
        val metrics = mutableListOf<String>()
        rule.setContent {
            val (id, dark, complex) = samples[index.intValue]
            val phone = index.intValue >= cases.size
            val density = if (phone) 2.75f else 1f
            val palette = resolveKeyboardPalette(id, dark)
            CompositionLocalProvider(LocalDensity provides Density(density), LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(handwritingPauseSeconds = 2.5f)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(if(phone) 384.dp else 620.dp, if(phone) 300.dp else 360.dp).background(palette.background).testTag("ink-host")) {
                        if (complex) Canvas(Modifier.fillMaxSize()) {
                            drawRect(Brush.linearGradient(listOf(Color.Black, Color.White, Color(0xFFBD713C), Color(0xFF153E54))))
                            for (x in 0..12) for (y in 0..7) if ((x+y)%2 == 0)
                                drawRect(if (x%3 == 0) Color.White else Color.Black,
                                    Offset(x*size.width/13,y*size.height/8), Size(size.width/13,size.height/8), alpha=.45f)
                        }
                        HandwritingKeyboardLayout(modifier=Modifier.fillMaxSize(), sessionKey=index.intValue.toLong(),
                            expanded=complex, bottomPaddingDp=0, panelBackgroundColor=if(complex) Color.Transparent else palette.background,
                            keyTextColor=palette.text, keyBackgroundColor=palette.key,
                            specialKeyBackgroundColor=palette.function, specialKeyTextColor=palette.functionText)
                    }
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (i in samples.indices) {
            rule.runOnIdle { index.intValue=i }
            rule.waitForIdle()
            rule.mainClock.autoAdvance=false
            val paper = rule.onNodeWithTag("ink-host").captureToImage().toPixelMap()
            // Continuous pointer paths for handwritten 手写, including bends and hooks.
            rule.onNodeWithTag("handwriting-canvas").performTouchInput {
                fun stroke(vararg points: Pair<Float,Float>) {
                    val first=points.first(); down(Offset(width*first.first,height*first.second))
                    var last=first
                    points.drop(1).forEach { point ->
                        // Feed intermediate touch samples as a real drag, not disconnected endpoints.
                        for (step in 1..6) {
                            val t=step/6f
                            moveTo(Offset(width*(last.first+(point.first-last.first)*t),
                                height*(last.second+(point.second-last.second)*t)), 12)
                        }
                        last=point
                    }
                    up(); advanceEventTime(50)
                }
                stroke(.32f to .23f,.27f to .26f,.21f to .285f,.155f to .295f)
                stroke(.155f to .39f,.22f to .382f,.30f to .375f)
                stroke(.12f to .525f,.20f to .515f,.29f to .51f,.355f to .505f)
                stroke(.255f to .285f,.252f to .40f,.256f to .55f,.255f to .69f,.245f to .76f,.224f to .718f)
                stroke(.475f to .225f,.495f to .28f)
                stroke(.445f to .335f,.54f to .326f,.63f to .32f,.655f to .34f,.635f to .405f)
                stroke(.515f to .445f,.595f to .437f)
                stroke(.484f to .432f,.468f to .515f,.463f to .542f,.54f to .535f,.627f to .53f,
                    .62f to .625f,.607f to .725f,.583f to .77f,.554f to .736f)
                stroke(.427f to .656f,.505f to .647f,.58f to .641f,.65f to .638f)
            }
            rule.mainClock.advanceTimeByFrame()
            val image = rule.onNodeWithTag("ink-host").captureToImage()
            val (id,dark,complex)=samples[i]
            val density = if(i >= cases.size) 2.75f else 1f
            val palette=resolveKeyboardPalette(id,dark)
            val ink=handwritingInk(palette.accent,palette.background)
            val ratio=contrast(ink,palette.background)
            assertTrue("$id dark=$dark contrast=$ratio",ratio >= 4.499f)
            val pixels=image.toPixelMap()
            var matching=0
            // Compare with the same panel before writing; unchanged controls cannot satisfy this assertion.
            for(y in 0 until image.height) for(x in 0 until image.width) {
                val c=pixels[x,y]; val old=paper[x,y]
                if(kotlin.math.abs(c.red-old.red)+kotlin.math.abs(c.green-old.green)+kotlin.math.abs(c.blue-old.blue)>.08f) matching++
            }
            val changedAreaDp = matching / (density * density)
            assertTrue("Continuous pen should be visible and slim: $id/$dark/$complex ($changedAreaDp)", changedAreaDp in 300f..6000f)
            File(context.getExternalFilesDir(null),"handwriting-$i.png").outputStream().use {
                image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
            }
            metrics += "$id,dark=$dark,complex=$complex,density=$density,opaqueInkContrast=$ratio,changedAreaDp=$changedAreaDp"
            rule.mainClock.autoAdvance=true
        }
        File(context.getExternalFilesDir(null),"handwriting-contrast.txt").writeText(metrics.joinToString("\n"))
    }
}
