package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.model.BuiltinModelCatalog
import com.kingzcheung.xime.speech.SpeechModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDownloadSizeTest {
    @Test fun `first-run size is written the way the model centre writes MB`() {
        val bytes = listOf("ochwpro", "predictive-text-base", SpeechModelCatalog.SENSEVOICE)
            .map { id -> BuiltinModelCatalog.models.first { it.id == id } }
            .sumOf { model -> model.files.sumOf { it.sizeBytes } }
        assertEquals("270 MB", formatModelMegabytes(bytes)) // 6.7 + 34.8 + 228.5
        assertEquals("0 MB", formatModelMegabytes(0))
        assertEquals("1 MB", formatModelMegabytes(524_288))
    }
}
