package com.kingzcheung.xime.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertEquals
import kotlin.random.Random

/** Runs the actual bundled native engine without taking a photo or reading personal images. */
class DocumentOcrTest {
    @Test fun reportedKeycapsAreRecognizedWithoutAssumingKeyboardLetters() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        withContext(Dispatchers.Default) {
            DocumentOcrEngine.Session(context).use { engine ->
                val source = assets.open("ocr/keycaps-reported.jpg").use(BitmapFactory::decodeStream)!!
                val strip = Bitmap.createBitmap(source, 36, 1017, 589, 77)
                val empty = assets.open("ocr/empty-textured-camera.png").use(BitmapFactory::decodeStream)!!
                try {
                    assertEquals("ZXCVBN", engine.recognize(strip, true).filterNot(Char::isWhitespace))
                    assertEquals("", engine.recognize(empty, true))
                } finally { source.recycle(); strip.recycle(); empty.recycle() }
            }
        }
    }
    @Test fun blankAndNoisyFramesReturnNothingThroughTheRealService() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        withContext(Dispatchers.Main) {
            OcrRecognitionClient(context).use { client ->
                val random = Random(77)
                for (kind in 0..4) {
                    val pixels = IntArray(640 * 160) { i ->
                        val gray = when (kind) {
                            0 -> 0; 1 -> 255; 2 -> random.nextInt(0, 35)
                            3 -> 110 + random.nextInt(10); else -> (i % 640) * 255 / 640
                        }
                        Color.rgb(gray, gray, gray)
                    }
                    val frame = Bitmap.createBitmap(pixels, 640, 160, Bitmap.Config.ARGB_8888)
                    try { assertEquals("Empty camera fixture $kind", "", client.recognize(frame)) }
                    finally { frame.recycle() }
                }
            }
        }
    }

    @Test fun punctuationAndDarkBackgroundTextAreNotBlacklisted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        withContext(Dispatchers.Default) {
            DocumentOcrEngine.Session(context).use { engine ->
                for (inverse in listOf(false, true)) {
                    val frame = Bitmap.createBitmap(1000, 180, Bitmap.Config.ARGB_8888)
                    Canvas(frame).apply {
                        drawColor(if (inverse) Color.BLACK else Color.WHITE)
                        drawText("Hello, world! 123", 40f, 110f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = if (inverse) Color.WHITE else Color.BLACK; textSize = 64f
                        })
                    }
                    try {
                        val text = engine.recognize(frame, singleLine = true)
                        assertTrue("inverted=$inverse: $text", text.contains("Hello, world! 123"))
                    } finally { frame.recycle() }
                }
            }
        }
    }
    @Test fun cameraCropIsRecognizedByTheRemoteWorkerWithoutOutsideText() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 54f }
            drawText("EXCLUDE TOP", 60f, 100f, paint)
            drawText("中文识别 Hello 123", 60f, 270f, paint)
            drawText("EXCLUDE BOTTOM", 60f, 440f, paint)
        }
        val crop = OcrScanGeometry.crop(1000, 500, 1000, 500)
        val strip = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width, crop.height)
        try {
            val result = withContext(Dispatchers.Main) {
                OcrRecognitionClient(context).use { it.recognize(strip) }
            }.filterNot(Char::isWhitespace)
            assertTrue(result, result.contains("中文识别"))
            assertTrue(result, result.contains("Hello123", ignoreCase = true))
            assertTrue(result, !result.contains("EXCLUDE", ignoreCase = true))
        } finally { bitmap.recycle(); strip.recycle() }
    }
    @Test fun bundledModelsRecognizePrintedChineseAndEnglishOffline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(1000, 300, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f }
            drawText("中文文字识别", 40f, 100f, paint)
            drawText("Hello world 12345", 40f, 220f, paint)
        }
        try {
            val result = withContext(Dispatchers.Default) { DocumentOcrEngine.recognize(context, bitmap) }
                .filterNot(Char::isWhitespace)
            assertTrue(result, result.contains("中文文字识别"))
            assertTrue(result, result.contains("Helloworld12345", ignoreCase = true))
        } finally { bitmap.recycle() }
    }
}
