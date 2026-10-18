package com.kingzcheung.xime.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import java.io.File
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Core
import org.opencv.imgproc.Imgproc
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Offline OCR lives in :ocr; its single worker never competes with the IME input queue. */
internal object DocumentOcrEngine {
    suspend fun recognize(context: Context, bitmap: Bitmap): String {
        currentCoroutineContext().ensureActive()
        return Session(context).use { it.recognize(bitmap) }
    }

    class Session(context: Context) : AutoCloseable {
        private val engine: DocumentOcrPipeline
        init {
            check(OpenCVLoader.initLocal()) { "Cannot load OCR image processing" }
            Core.setNumThreads(1)
            val dataDir = File(context.noBackupFilesDir, "ocr-ppocr-v5-1").apply { check(mkdirs() || isDirectory) }
            fun asset(name: String): String {
                val target = File(dataDir, name)
                val atomic = AtomicFile(target)
                if (!target.exists() || target.length() == 0L) {
                    val stream = atomic.startWrite()
                    try {
                        context.assets.open("ocr/ppocr-v5/$name").use { it.copyTo(stream) }
                        atomic.finishWrite(stream)
                    } catch (e: Exception) { atomic.failWrite(stream); throw e }
                }
                return target.absolutePath
            }
            engine = DocumentOcrPipeline(
                asset("ch_PP-OCRv5_det_mobile.onnx"), asset("ch_PP-OCRv5_rec_mobile.onnx"),
                asset("ppocrv5-characters.txt"),
            )
        }
        fun recognize(bitmap: Bitmap, @Suppress("UNUSED_PARAMETER") singleLine: Boolean = false): String {
            if (OcrFrameQuality.assess(bitmap) != OcrFrameQuality.Verdict.READY) return ""
            val rgba = Mat()
            val bgr = Mat()
            return try {
                Utils.bitmapToMat(bitmap, rgba)
                Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
                DocumentOcrPipeline.assemble(engine.recognize(bgr))
            } finally { bgr.release(); rgba.release() }
        }
        override fun close() { engine.close() }
    }
}
