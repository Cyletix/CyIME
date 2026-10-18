package com.kingzcheung.xime.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Synthetic test content only; retain actual device renders for visual review. */
internal fun saveUiEvidence(name: String, node: SemanticsNodeInteraction) {
    val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "ui-verification")
    check(directory.isDirectory || directory.mkdirs())
    File(directory, "$name.png").outputStream().use {
        check(node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
    }
}
