package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.filters.SdkSuppress
import com.kingzcheung.xime.ui.theme.KeyboardBackdropFrame
import com.kingzcheung.xime.ui.theme.LocalSharedKeyboardBackdrop
import com.kingzcheung.xime.ui.theme.SharedKeyboardBackdrop
import com.kingzcheung.xime.ui.theme.liquidGlassCap
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

@SdkSuppress(minSdkVersion = 33)
class LiquidGlassKeyImageTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun keyCentresKeepThePictureEvenWhileTheBackdropFrameIsMoving() {
        val photo = Bitmap.createBitmap(240, 180, Bitmap.Config.ARGB_8888)
        for (y in 0 until photo.height) for (x in 0 until photo.width) {
            photo.setPixel(x, y, android.graphics.Color.rgb(30 + x / 2, 30 + y, if ((x / 8 + y / 8) % 2 == 0) 184 else 52))
        }
        var glass by mutableStateOf(false)
        var displaced by mutableStateOf(false)
        rule.setContent { LiquidKeyImageGrid(photo, glass, displaced) }
        fun shot() = rule.onNodeWithTag("photo-keys").captureToImage().asAndroidBitmap()
        val reference = shot()
        assertNotEquals(reference.getPixel(reference.width / 12, reference.height / 12),
            reference.getPixel(reference.width / 12, reference.height * 9 / 12))
        var aligned: Bitmap? = null
        for (moving in listOf(false, true)) {
            rule.runOnIdle { glass = true; displaced = moving }
            val actual = shot()
            for (row in 0..2) for (column in 0..2) {
                // In the undistorted interior, away from the legend in the middle.
                val x = (column * 4 + 1) * actual.width / 12
                val y = (row * 4 + 1) * actual.height / 12
                assertEquals("row=$row column=$column moving=$moving", reference.getPixel(x, y), actual.getPixel(x, y))
            }
            if (!moving) aligned = actual
            else {
                assertFalse("The liquid rim must still be visible", reference.sameAs(actual))
                assertFalse("In-bounds glass must also refract the image", actual.sameAs(requireNotNull(aligned)))
            }
        }
    }
}

@Composable
internal fun LiquidKeyImageGrid(photo: Bitmap, glass: Boolean, displaced: Boolean) {
    val image = remember(photo) { photo.asImageBitmap() }
    val root = LocalView.current.rootView
    var frame by remember { mutableStateOf(KeyboardBackdropFrame(Offset.Zero, IntSize.Zero)) }
    var windowToRoot by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.padding(start = 9.dp, top = 21.dp)) {
        Column(Modifier.size(240.dp, 180.dp).testTag("photo-keys").onGloballyPositioned {
            val origin = it.positionInWindow()
            windowToRoot = it.positionInRoot() - origin
            frame = KeyboardBackdropFrame(origin, it.size)
        }.drawBehind { drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }) {
            val drawingFrame = if (displaced) frame.copy(origin = frame.origin + Offset(0f, frame.size.height.toFloat())) else frame
            CompositionLocalProvider(LocalSharedKeyboardBackdrop provides SharedKeyboardBackdrop(
                drawingFrame, image, Color.Black, 1f, root, windowToRoot = windowToRoot)) {
                val legends = listOf("分词", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")
                repeat(3) { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        repeat(3) { col ->
                            val cap = Modifier.background(Color.White.copy(alpha = .18f), RoundedCornerShape(6.dp))
                            Box(Modifier.weight(1f).fillMaxHeight().padding(2.dp)
                                .then(if (glass) Modifier.liquidGlassCap(cap, 6.dp) else cap), contentAlignment = Alignment.Center) {
                                BasicText(legends[row * 3 + col], style = TextStyle(color = Color.White, fontSize = 14.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}
