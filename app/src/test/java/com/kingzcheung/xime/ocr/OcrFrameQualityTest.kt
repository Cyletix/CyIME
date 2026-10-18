package com.kingzcheung.xime.ocr

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class OcrFrameQualityTest {
    private fun gray(v: Int) = 0xff000000.toInt() or (v shl 16) or (v shl 8) or v
    @Test fun blackWhiteCoveredLensNoiseAndSmoothGradientsAreNotTextFrames() {
        val random = Random(71)
        for (pixels in listOf(
            IntArray(32000) { gray(0) }, IntArray(32000) { gray(255) },
            IntArray(32000) { gray(random.nextInt(0, 35)) },
            IntArray(32000) { gray(120 + random.nextInt(10)) },
            IntArray(32000) { gray((it % 320) * 255 / 320) },
        )) assertNotEquals(OcrFrameQuality.Verdict.READY, OcrFrameQuality.assess(pixels, 320, 100))
    }
    @Test fun brightStrokesOnDarkBackgroundAndDarkStrokesOnLightAreRetained() {
        for (inverse in listOf(false, true)) {
            val pixels = IntArray(32000) { gray(if (inverse) 0 else 255) }
            for (y in 30..70) for (x in 60..250) if (x % 20 < 3 || y % 20 < 3)
                pixels[y * 320 + x] = gray(if (inverse) 255 else 0)
            assertEquals(OcrFrameQuality.Verdict.READY, OcrFrameQuality.assess(pixels, 320, 100))
        }
    }
    @Test fun previewRequiresAgreementAndClearsImmediatelyWhenSceneChanges() {
        val state = OcrPreviewStability()
        assertEquals("", state.update("?~"))
        assertEquals("", state.update("|~:"))
        assertEquals("", state.update("中文 Hello, 123!"))
        assertEquals("中文 Hello, 123!", state.update("中文 Hello, 123!"))
        assertEquals("", state.update(""))
        assertEquals("", state.update("中文 Hello, 123!"))
    }
}
