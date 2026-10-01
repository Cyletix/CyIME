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
        val metrics = mutableListOf<String>()
        rule.setContent {
            val (id, dark, complex) = cases[index.intValue]
            val palette = resolveKeyboardPalette(id, dark)
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalKeyboardPalette provides palette,
                LocalKeyboardInputPreferences provides KeyboardInputPreferences(handwritingPauseSeconds = 2.5f)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(620.dp, 360.dp).background(palette.background).testTag("ink-host")) {
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
        for (i in cases.indices) {
            rule.runOnIdle { index.intValue=i }
            rule.waitForIdle()
            rule.mainClock.autoAdvance=false
            // Real pointer strokes for 中, not a bitmap or a different preview renderer.
            rule.onNodeWithTag("handwriting-canvas").performTouchInput {
                fun stroke(vararg points: Pair<Float,Float>) {
                    val first=points.first(); down(Offset(width*first.first,height*first.second))
                    points.drop(1).forEach { (x,y) -> moveTo(Offset(width*x,height*y), 70) }
                    up(); advanceEventTime(35)
                }
                stroke(.30f to .30f,.30f to .66f)
                stroke(.30f to .30f,.68f to .30f,.68f to .66f)
                stroke(.30f to .66f,.68f to .66f)
                stroke(.49f to .14f,.49f to .84f)
            }
            rule.mainClock.advanceTimeByFrame()
            val image = rule.onNodeWithTag("ink-host").captureToImage()
            val (id,dark,complex)=cases[i]
            val palette=resolveKeyboardPalette(id,dark)
            val ink=handwritingInk(palette.accent,palette.background)
            val ratio=contrast(ink,palette.background)
            assertTrue("$id dark=$dark contrast=$ratio",ratio >= 4.499f)
            val pixels=image.toPixelMap()
            var matching=0
            for(y in 0 until pixels.height) for(x in 0 until pixels.width) {
                val c=pixels[x,y]
                if(kotlin.math.abs(c.red-ink.red)<.015f && kotlin.math.abs(c.green-ink.green)<.015f && kotlin.math.abs(c.blue-ink.blue)<.015f) matching++
            }
            assertTrue("Actual pointer ink must use accent: $id/$dark/$complex", matching>500)
            File(context.getExternalFilesDir(null),"handwriting-$i.png").outputStream().use {
                image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
            }
            metrics += "$id,dark=$dark,complex=$complex,contrast=$ratio,corePixels=$matching"
            rule.mainClock.autoAdvance=true
        }
        File(context.getExternalFilesDir(null),"handwriting-contrast.txt").writeText(metrics.joinToString("\n"))
    }
}
