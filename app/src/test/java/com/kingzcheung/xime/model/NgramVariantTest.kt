package com.kingzcheung.xime.model

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NgramVariantTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun model(id: String) = ModelInfo(id, id, "", ModelCategory.CANDIDATE,
        versions = listOf(ModelVersion("v1", files = listOf(ModelFile("t9_ngram.lm", "", "", 0L)))))
    private val full = model("t9-ngram")
    private val small = model("t9-ngram-small")

    private fun stored(name: String, content: String) = temporary.newFolder(name).apply {
        File(this, "t9_ngram.lm").writeText(content)
    }

    @Test fun runtimeLinkIdentifiesTheVariantInUse() {
        val fullDir = stored("full", "full model")
        val smallDir = stored("small", "small model")
        val runtime = temporary.newFolder("rime")
        Files.createLink(File(runtime, "t9_ngram.lm").toPath(), File(smallDir, "t9_ngram.lm").toPath())
        assertEquals(small, activeVariant(runtime, listOf(full to fullDir, small to smallDir)))
    }

    @Test fun copiedOrMissingRuntimeFileIsNotAttributed() {
        val fullDir = stored("full", "full model")
        val smallDir = stored("small", "small model")
        val runtime = temporary.newFolder("rime")
        assertNull(activeVariant(runtime, listOf(full to fullDir, small to smallDir)))
        // 拷贝安装的内容即使相同也无法确认归属，不能据此删掉另一档。
        File(fullDir, "t9_ngram.lm").copyTo(File(runtime, "t9_ngram.lm"))
        assertNull(activeVariant(runtime, listOf(full to fullDir, small to smallDir)))
    }
}
