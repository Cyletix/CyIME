package com.kingzcheung.xime.ocr

import android.content.Context
import android.graphics.Bitmap
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
            // 轻量版的模型是下载的；带模型的安装包第一次用时从 assets 解出（见 OcrModelFiles）。
            val dataDir = OcrModelFiles.prepare(context) ?: throw OcrModelMissingException()
            fun file(name: String): String = File(dataDir, name).absolutePath
            engine = DocumentOcrPipeline(
                file(OcrModelFiles.DETECTION), file(OcrModelFiles.RECOGNITION), file(OcrModelFiles.CHARACTERS),
            )
        }
        /** [table] lays the text out as a table in that format; null keeps plain text. */
        fun recognize(bitmap: Bitmap, @Suppress("UNUSED_PARAMETER") singleLine: Boolean = false,
                      table: OcrTableLayout.Format? = null): String {
            if (OcrFrameQuality.assess(bitmap) != OcrFrameQuality.Verdict.READY) return ""
            val rgba = Mat()
            val bgr = Mat()
            return try {
                Utils.bitmapToMat(bitmap, rgba)
                Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
                if (table != null) engine.recognizeTable(bgr, table)
                else DocumentOcrPipeline.assemble(engine.recognize(bgr))
            } finally { bgr.release(); rgba.release() }
        }
        override fun close() { engine.close() }
    }
}
